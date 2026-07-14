package com.kb.demo.service;

import com.kb.demo.entity.AnswerEvaluation;
import com.kb.demo.entity.UploadProgress;
import com.kb.demo.repository.AnswerEvaluationRepository;
import com.kb.demo.repository.UploadProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文档处理异步服务
 * 处理大文件上传、解析、分块和向量化
 * 支持进度追踪
 * @author LiJingLin
 */
@Service
public class DocumentProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);
    
    @Autowired
    private FileParseService fileParseService;
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private DocumentChunkService documentChunkService;
    
    @Autowired
    private UploadProgressRepository uploadProgressRepository;
    
    @Autowired
    private MetricsService metricsService;
    
    /**
     * 异步处理文件上传（不阻塞主线程）
     * @param file 上传的文件
     * @return 上传ID，用于前端查询进度
     */
    public String uploadFileAsync(MultipartFile file) {
        // 生成唯一的上传ID
        String uploadId = UUID.randomUUID().toString();
        
        // 记录文档上传
        metricsService.recordDocumentUpload();
        
        // 立即创建上传进度记录（在启动异步线程之前）
        UploadProgress progress = new UploadProgress();
        progress.setUploadId(uploadId);
        progress.setFileName(file.getOriginalFilename());
        progress.setFileSize(file.getSize());
        progress.setStatus(UploadProgress.UploadStatus.UPLOADING);
        progress.setPercentage(0);
        uploadProgressRepository.save(progress);
        
        logger.info("开始异步上传文件: uploadId={}, fileName={}", uploadId, file.getOriginalFilename());
        
        // 异步处理文件（立即返回，不等待处理完成）
        processFileAsync(uploadId, file);
        
        return uploadId;
    }
    
    /**
     * 异步处理文件的核心逻辑
     */
    @Async("taskExecutor")  // 使用线程池，不阻塞主线程
    public void processFileAsync(String uploadId, MultipartFile file) {
        var timer = metricsService.startDocumentProcessingTimer();
        
        try {
            // 等待2秒，确保前端有足够时间开始轮询并看到初始进度
            // 避免前端还在等待HTTP响应时，后端已经处理完成
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            UploadProgress progress = uploadProgressRepository.findByUploadId(uploadId)
                    .orElseThrow(() -> new RuntimeException("找不到上传记录"));
            
            // 步骤1：解析文件
            updateProgress(uploadId, UploadProgress.UploadStatus.PARSING, 10);
            logger.info("开始解析文件: {}", uploadId);
            Map<String, String> parseResult = fileParseService.parseFile(file);
            
            // 步骤2：创建文档
            updateProgress(uploadId, UploadProgress.UploadStatus.CHUNKING, 40);
            logger.info("开始分块处理: {}", uploadId);
            
            // 创建文档对象
            com.kb.demo.entity.Document document = new com.kb.demo.entity.Document();
            document.setTitle(parseResult.get("title"));
            
            String fileType = fileParseService.getFileTypeDescription(file);
            String enhancedContent = String.format("文件类型: %s\n\n%s", fileType, parseResult.get("content"));
            document.setContent(enhancedContent);
            
            // 保存文档（这里不进行向量化，由下一步处理）
            com.kb.demo.entity.Document savedDocument = documentService.saveDocument(document, false);
            
            // 步骤3&4：分块处理和向量化（一步完成）
            updateProgress(uploadId, UploadProgress.UploadStatus.EMBEDDING, 80);
            logger.info("开始分块和向量化: {}", uploadId);
            
            // 使用醨收进度回调的方法，以便实時更新进度
            int chunkCount = documentChunkService.processDocumentWithProgress(
                savedDocument.getId(),
                (currentChunk, totalChunks) -> {
                    // 计算進度：80% + (currentChunk / totalChunks) * 20%
                    int percentage = 80 + (int) ((currentChunk / (double) totalChunks) * 20);
                    percentage = Math.min(percentage, 99); // 最高一99%，待最后完成时设为100%
                    updateProgress(uploadId, UploadProgress.UploadStatus.EMBEDDING, percentage);
                    logger.debug("[向量化进度] uploadId={}, \u5df2处理 {}/{} 个块, 进度 {}%", 
                            uploadId, currentChunk, totalChunks, percentage);
                }
            );
            logger.info("文档处理完成: uploadId={}, chunks={}", uploadId, chunkCount);
            
            // 完成
            updateProgress(uploadId, UploadProgress.UploadStatus.COMPLETED, 100);
            logger.info("✅ 文件处理完成: uploadId={}, fileName={}, chunks={}", 
                    uploadId, file.getOriginalFilename(), chunkCount);
            
            // 记录文档处理时间
            metricsService.recordDocumentProcessingTime(timer);
            
        } catch (Exception e) {
            logger.error("文件处理失败: uploadId={}", uploadId, e);
            updateProgressWithError(uploadId, e.getMessage());
        }
    }
    
    /**
     * 更新上传进度
     */
    private void updateProgress(String uploadId, UploadProgress.UploadStatus status, int percentage) {
        UploadProgress progress = uploadProgressRepository.findByUploadId(uploadId)
                .orElse(null);
        
        if (progress != null) {
            progress.setStatus(status);
            progress.setPercentage(percentage);
            progress.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(progress);
            logger.debug("进度更新: uploadId={}, status={}, percentage={}%", 
                    uploadId, status.getDescription(), percentage);
        }
    }
    
    /**
     * 更新进度（出错）
     */
    private void updateProgressWithError(String uploadId, String errorMessage) {
        UploadProgress progress = uploadProgressRepository.findByUploadId(uploadId)
                .orElse(null);
        
        if (progress != null) {
            progress.setStatus(UploadProgress.UploadStatus.FAILED);
            progress.setErrorMessage(errorMessage);
            progress.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(progress);
            logger.error("上传失败: uploadId={}, error={}", uploadId, errorMessage);
        }
    }
    
    /**
     * 查询上传进度
     */
    public Map<String, Object> getUploadProgress(String uploadId) {
        UploadProgress progress = uploadProgressRepository.findByUploadId(uploadId)
                .orElse(null);
        
        Map<String, Object> result = new HashMap<>();
        
        if (progress != null) {
            result.put("uploadId", progress.getUploadId());
            result.put("fileName", progress.getFileName());
            result.put("fileSize", progress.getFileSize());
            result.put("uploadedSize", progress.getUploadedSize());
            result.put("percentage", progress.getPercentage());
            result.put("status", progress.getStatus().getDescription());
            result.put("statusCode", progress.getStatus().name());
            result.put("errorMessage", progress.getErrorMessage());
            result.put("createdAt", progress.getCreatedAt());
            result.put("updatedAt", progress.getUpdatedAt());
        }
        
        return result;
    }
}
