package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.UploadProgress;
import com.kb.demo.repository.UploadProgressRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Executes document ingestion on the configured background executor.
 */
@Service
public class DocumentProcessingWorker {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final FileParseService fileParseService;
    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final UploadProgressRepository uploadProgressRepository;
    private final MetricsService metricsService;
    private final DocumentFileStorage documentFileStorage;

    public DocumentProcessingWorker(
            FileParseService fileParseService,
            DocumentService documentService,
            DocumentChunkService documentChunkService,
            UploadProgressRepository uploadProgressRepository,
            MetricsService metricsService,
            DocumentFileStorage documentFileStorage) {
        this.fileParseService = fileParseService;
        this.documentService = documentService;
        this.documentChunkService = documentChunkService;
        this.uploadProgressRepository = uploadProgressRepository;
        this.metricsService = metricsService;
        this.documentFileStorage = documentFileStorage;
    }

    /**
     * Accepts only a durable job identifier, never the request-scoped MultipartFile.
     */
    @Async("taskExecutor")
    public void processFileAsync(String uploadId) {
        Timer.Sample timer = metricsService.startDocumentProcessingTimer();

        try {
            UploadProgress progress = uploadProgressRepository.findByUploadId(uploadId)
                    .orElseThrow(() -> new IllegalStateException("找不到上传记录"));
            Path storedFile = documentFileStorage.resolve(uploadId, progress.getFileName());

            updateProgress(uploadId, UploadProgress.UploadStatus.PARSING, 10);
            logger.info("开始解析文件: uploadId={}, thread={}",
                    uploadId, Thread.currentThread().getName());
            Map<String, String> parseResult = fileParseService.parseFile(storedFile, progress.getFileName());

            updateProgress(uploadId, UploadProgress.UploadStatus.CHUNKING, 40);
            logger.info("开始分块处理: {}", uploadId);

            Document document = new Document();
            document.setTitle(parseResult.get("title"));

            String fileType = fileParseService.getFileTypeDescription(progress.getFileName());
            String enhancedContent = String.format(
                    "文件类型: %s%n%n%s", fileType, parseResult.get("content"));
            document.setContent(enhancedContent);

            Document savedDocument = documentService.saveDocument(document, false);

            updateProgress(uploadId, UploadProgress.UploadStatus.EMBEDDING, 80);
            logger.info("开始分块和向量化: {}", uploadId);

            int chunkCount = documentChunkService.processDocumentWithProgress(
                    savedDocument.getId(),
                    (currentChunk, totalChunks) -> {
                        int percentage = 80
                                + (int) ((currentChunk / (double) totalChunks) * 20);
                        percentage = Math.min(percentage, 99);
                        updateProgress(
                                uploadId,
                                UploadProgress.UploadStatus.EMBEDDING,
                                percentage);
                    });

            updateProgress(uploadId, UploadProgress.UploadStatus.COMPLETED, 100);
            logger.info("文件处理完成: uploadId={}, fileName={}, chunks={}",
                    uploadId, progress.getFileName(), chunkCount);
        } catch (Exception e) {
            logger.error("文件处理失败: uploadId={}", uploadId, e);
            updateProgressWithError(uploadId, safeErrorMessage(e));
        } finally {
            metricsService.recordDocumentProcessingTime(timer);
        }
    }

    private void updateProgress(
            String uploadId,
            UploadProgress.UploadStatus status,
            int percentage) {
        uploadProgressRepository.findByUploadId(uploadId).ifPresent(progress -> {
            progress.setStatus(status);
            progress.setPercentage(percentage);
            progress.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(progress);
        });
    }

    private void updateProgressWithError(String uploadId, String errorMessage) {
        uploadProgressRepository.findByUploadId(uploadId).ifPresent(progress -> {
            progress.setStatus(UploadProgress.UploadStatus.FAILED);
            progress.setErrorMessage(errorMessage);
            progress.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(progress);
        });
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "文档处理失败" : message;
    }
}
