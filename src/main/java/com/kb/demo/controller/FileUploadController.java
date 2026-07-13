package com.kb.demo.controller;

import com.kb.demo.dto.DocumentCategoryTagDTO;
import com.kb.demo.entity.Document;
import com.kb.demo.service.DocumentCategoryTagService;
import com.kb.demo.service.DocumentService;
import com.kb.demo.service.DocumentProcessingService;
import com.kb.demo.service.FileParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传控制器
 * 提供文件上传、解析和类型检查功能
 * @author LiJingLin
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);
    
    @Autowired
    private FileParseService fileParseService;
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private DocumentCategoryTagService documentCategoryTagService;
    
    @Autowired
    private com.kb.demo.service.DocumentChunkService documentChunkService;
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    /**
     * 上传并解析文件
     * @param file 上传的文件
     * @return 解析后的文档信息
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<?> uploadAndParseFile(@RequestParam("file") MultipartFile file) {
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("文件不能为空");
            }
            
            // 检查文件类型是否支持
            if (!fileParseService.isFileTypeSupported(file)) {
                return ResponseEntity.badRequest().body("不支持的文件类型");
            }
            
            // 解析文件
            Map<String, String> parseResult = fileParseService.parseFile(file);
            
            // 创建文档对象并保存
            Document document = new Document();
            document.setTitle(parseResult.get("title"));
            document.setContent(parseResult.get("content"));
            
            // 添加文件类型信息到内容中
            String fileType = fileParseService.getFileTypeDescription(file);
            String enhancedContent = String.format("文件类型: %s\n\n%s", fileType, parseResult.get("content"));
            document.setContent(enhancedContent);
            
            // 保存文档并进行向量化处理
            Document savedDocument = documentService.saveDocument(document, true);
            
            logger.info("✅ 文件上传并解析成功(含向量化): {}", savedDocument.getTitle());
            
            return ResponseEntity.ok(savedDocument);
            
        } catch (Exception e) {
            logger.error("文件上传处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 上传并解析文件，并设置分类和标签
     * @param file 上传的文件
     * @param categoryIds 分类ID列表（可选）
     * @param tagIds 标签ID列表（可选）
     * @return 解析后的文档信息
     */
    @PostMapping("/upload-with-categories-tags")
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<?> uploadAndParseFileWithCategoriesAndTags(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "tagIds", required = false) List<Long> tagIds) {
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("文件不能为空");
            }
            
            // 检查文件类型是否支持
            if (!fileParseService.isFileTypeSupported(file)) {
                return ResponseEntity.badRequest().body("不支持的文件类型");
            }
            
            // 解析文件
            Map<String, String> parseResult = fileParseService.parseFile(file);
            
            // 创建文档对象并保存
            Document document = new Document();
            document.setTitle(parseResult.get("title"));
            document.setContent(parseResult.get("content"));
            
            // 添加文件类型信息到内容中
            String fileType = fileParseService.getFileTypeDescription(file);
            String enhancedContent = String.format("文件类型: %s\n\n%s", fileType, parseResult.get("content"));
            document.setContent(enhancedContent);
            
            // 保存文档并进行向量化处理
            Document savedDocument = documentService.saveDocument(document, true);
            
            // 设置分类和标签
            if (categoryIds != null && !categoryIds.isEmpty()) {
                DocumentCategoryTagDTO documentCategoryTagDTO = new DocumentCategoryTagDTO();
                documentCategoryTagDTO.setDocumentId(savedDocument.getId());
                documentCategoryTagDTO.setCategoryIds(categoryIds);
                documentCategoryTagDTO.setTagIds(tagIds);
                documentCategoryTagService.setDocumentCategoriesAndTags(documentCategoryTagDTO);
            }
            
            logger.info("✅ 文件上传并解析成功(含向量化)，已设置分类和标签: {}", savedDocument.getTitle());
            
            return ResponseEntity.ok(savedDocument);
            
        } catch (Exception e) {
            logger.error("文件上传处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查文件类型是否支持
     * @param file 上传的文件
     * @return 是否支持
     */
    @PostMapping("/check-type")
    public ResponseEntity<?> checkFileType(@RequestParam("file") MultipartFile file) {
        try {
            boolean supported = fileParseService.isFileTypeSupported(file);
            String fileType = fileParseService.getFileTypeDescription(file);
            
            Map<String, Object> result = Map.of(
                "supported", supported,
                "fileType", fileType,
                "fileName", file.getOriginalFilename(),
                "fileSize", file.getSize()
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("文件类型检查失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件类型检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 实验性异步上传文件。
     * TODO（二开阶段 1）：修复同类方法调用导致 @Async 不生效，以及请求临时文件跨线程生命周期问题。
     * @param file 上传的文件
     * @return 上传ID，用于查询进度
     */
    @PostMapping("/upload-async")
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<?> uploadFileAsync(@RequestParam("file") MultipartFile file) {
        try {
            // 文件验证
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("文件不能为空");
            }
            
            if (!fileParseService.isFileTypeSupported(file)) {
                return ResponseEntity.badRequest().body("不支持的文件类型");
            }
            
            // 异步处理文件
            String uploadId = documentProcessingService.uploadFileAsync(file);
            
            Map<String, String> result = new HashMap<>();
            result.put("uploadId", uploadId);
            result.put("message", "文件上传开始，请使用uploadId查询进度");
            result.put("statusUrl", "/api/files/upload-progress/" + uploadId);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("文件上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询上传进度
     * @param uploadId 上传ID
     * @return 上传进度信息
     */
    @GetMapping("/upload-progress/{uploadId}")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<?> getUploadProgress(@PathVariable String uploadId) {
        try {
            Map<String, Object> progress = documentProcessingService.getUploadProgress(uploadId);
            
            // 即使找不到进度记录，也返回200而不是404，避免轮询出错
            // 前端会根据返回的数据来判断是否有进度信息
            return ResponseEntity.ok(progress);
            
        } catch (Exception e) {
            logger.error("查询上传进度失败", e);
            // 返回空Map而不是500错误，让前端继续轮询
            return ResponseEntity.ok(new HashMap<String, Object>());
        }
    }
    
    /**
     * 获取支持的文件类型列表
     * @return 支持的文件类型列表
     */
    @GetMapping("/supported-types")
    public ResponseEntity<?> getSupportedFileTypes() {
        try {
            Map<String, String> supportedTypes = new HashMap<>();
            supportedTypes.put("pdf", "PDF文档");
            supportedTypes.put("doc", "Word文档");
            supportedTypes.put("docx", "Word文档");
            supportedTypes.put("xls", "Excel表格");
            supportedTypes.put("xlsx", "Excel表格");
            supportedTypes.put("ppt", "PowerPoint演示文稿");
            supportedTypes.put("pptx", "PowerPoint演示文稿");
            supportedTypes.put("txt", "文本文件");
            supportedTypes.put("rtf", "富文本格式");
            supportedTypes.put("html", "HTML网页");
            supportedTypes.put("htm", "HTML网页");
            supportedTypes.put("csv", "CSV数据文件");
            
            return ResponseEntity.ok(supportedTypes);
            
        } catch (Exception e) {
            logger.error("获取支持的文件类型失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取支持的文件类型失败: " + e.getMessage());
        }
    }
}
