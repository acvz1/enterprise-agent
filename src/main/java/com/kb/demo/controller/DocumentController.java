package com.kb.demo.controller;

import com.kb.demo.dto.DocumentCategoryTagDTO;
import com.kb.demo.dto.DocumentCreateDTO;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentVersion;
import com.kb.demo.service.DocumentCategoryTagService;
import com.kb.demo.service.DocumentService;
import com.kb.demo.service.DocumentVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * 文档管理控制器
 * 提供文档的增删改查和搜索功能
 * @author LiJingLin
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentCategoryTagService documentCategoryTagService;
    
    @Autowired
    private DocumentVersionService documentVersionService;
    
    @Autowired
    private com.kb.demo.service.DocumentChunkService documentChunkService;

    /**
     * 获取所有文档（分页）
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sort 排序字段
     * @param direction 排序方向（asc/desc）
     * @return 分页文档列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<Page<Document>> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        
        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        
        Page<Document> documents = documentService.getAllDocuments(pageable);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<Document> getDocumentById(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(document);
    }

    /**
     * 获取文档详细信息（包括分类和标签）
     * @param id 文档ID
     * @return 文档详细信息
     */
    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<Document> getDocumentDetailsById(@PathVariable Long id) {
        Optional<Document> document = documentService.getDocumentDetailsById(id);
        if (document.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(document.get());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<Document> createDocument(@RequestBody Document document) {
        Document savedDocument = documentService.saveDocument(document);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDocument);
    }

    /**
     * 创建文档并设置分类和标签
     * @param documentCreateDTO 文档创建DTO，包含文档信息和分类标签ID
     * @return 创建的文档
     */
    @PostMapping("/with-categories-tags")
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<Document> createDocumentWithCategoriesAndTags(@RequestBody DocumentCreateDTO documentCreateDTO) {
        // 创建文档
        Document document = new Document();
        document.setTitle(documentCreateDTO.getTitle());
        document.setContent(documentCreateDTO.getContent());
        
        Document savedDocument = documentService.saveDocument(document);
        
        // 如果有分类或标签，设置关联关系
        if ((documentCreateDTO.getCategoryIds() != null && !documentCreateDTO.getCategoryIds().isEmpty()) ||
            (documentCreateDTO.getTagIds() != null && !documentCreateDTO.getTagIds().isEmpty())) {
            
            DocumentCategoryTagDTO documentCategoryTagDTO = new DocumentCategoryTagDTO();
            documentCategoryTagDTO.setDocumentId(savedDocument.getId());
            documentCategoryTagDTO.setCategoryIds(documentCreateDTO.getCategoryIds());
            documentCategoryTagDTO.setTagIds(documentCreateDTO.getTagIds());
            
            documentCategoryTagService.setDocumentCategoriesAndTags(documentCategoryTagDTO);
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDocument);
    }

    /**
     * 更新文档
     * @param id 文档ID
     * @param document 更新的文档信息
     * @return 更新后的文档
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<Document> updateDocument(@PathVariable Long id, @RequestBody Document document) {
        Document existingDocument = documentService.getDocumentById(id);
        if (existingDocument == null) {
            return ResponseEntity.notFound().build();
        }
        
        document.setId(id);
        Document updatedDocument = documentService.updateDocumentWithVersion(id, document);
        return ResponseEntity.ok(updatedDocument);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('document:delete') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 使用完整删除方法，清理所有相关数据（MySQL 和 Redis）
        documentService.deleteDocumentWithRelatedData(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 全量重建Redis向量索引（清理孤立数据）
     * 警告：此操作会清空Redis中的所有向量数据并重新生成，耗时较长
     * @return 重建结果
     */
    @PostMapping("/rebuild-vector-index")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rebuildVectorIndex() {
        try {
            int totalChunks = documentChunkService.rebuildAllVectorIndex();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "全量重建完成");
            response.put("totalChunks", totalChunks);
            response.put("info", "已清理旧的向量索引并重新生成 " + totalChunks + " 个文档块的向量数据");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "重建失败: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 根据标题搜索文档
     * @param title 标题关键词
     * @return 匹配的文档列表
     */
    @GetMapping("/search/title")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> searchDocumentsByTitle(@RequestParam String title) {
        List<Document> documents = documentService.searchDocumentsByTitle(title);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 根据内容搜索文档
     * @param content 内容关键词
     * @return 匹配的文档列表
     */
    @GetMapping("/search/content")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> searchDocumentsByContent(@RequestParam String content) {
        List<Document> documents = documentService.searchDocumentsByContent(content);
        return ResponseEntity.ok(documents);
    }

    /**
     * 设置文档的分类和标签
     * @param id 文档ID
     * @param documentCategoryTagDTO 文档分类和标签关联DTO
     * @return 操作结果
     */
    @PostMapping("/{id}/categories-tags")
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<Void> setDocumentCategoriesAndTags(
            @PathVariable Long id, 
            @RequestBody DocumentCategoryTagDTO documentCategoryTagDTO) {
        
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        
        documentCategoryTagDTO.setDocumentId(id);
        documentCategoryTagService.setDocumentCategoriesAndTags(documentCategoryTagDTO);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取文档的分类ID列表
     * @param id 文档ID
     * @return 分类ID列表
     */
    @GetMapping("/{id}/categories")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Long>> getDocumentCategoryIds(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<Long> categoryIds = documentCategoryTagService.getDocumentCategoryIds(id);
        return ResponseEntity.ok(categoryIds);
    }

    /**
     * 获取文档的标签ID列表
     * @param id 文档ID
     * @return 标签ID列表
     */
    @GetMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Long>> getDocumentTagIds(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<Long> tagIds = documentCategoryTagService.getDocumentTagIds(id);
        return ResponseEntity.ok(tagIds);
    }

    /**
     * 获取文档的分类名称列表
     * @param id 文档ID
     * @return 分类名称列表
     */
    @GetMapping("/{id}/category-names")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<String>> getDocumentCategoryNames(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<String> categoryNames = documentCategoryTagService.getDocumentCategoryNames(id);
        return ResponseEntity.ok(categoryNames);
    }

    /**
     * 获取文档的标签名称列表
     * @param id 文档ID
     * @return 标签名称列表
     */
    @GetMapping("/{id}/tag-names")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<String>> getDocumentTagNames(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<String> tagNames = documentCategoryTagService.getDocumentTagNames(id);
        return ResponseEntity.ok(tagNames);
    }
    
    /**
     * 根据分类ID查找文档
     * @param categoryId 分类ID
     * @return 匹配的文档列表
     */
    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> getDocumentsByCategory(@PathVariable Long categoryId) {
        List<Document> documents = documentService.findDocumentsByCategory(categoryId);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 根据标签ID查找文档
     * @param tagId 标签ID
     * @return 匹配的文档列表
     */
    @GetMapping("/tag/{tagId}")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> getDocumentsByTag(@PathVariable Long tagId) {
        List<Document> documents = documentService.findDocumentsByTag(tagId);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 根据多个分类ID查找文档
     * @param categoryIds 分类ID列表，用逗号分隔
     * @return 匹配的文档列表
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> getDocumentsByCategories(@RequestParam String categoryIds) {
        List<Long> ids = parseIds(categoryIds);
        List<Document> documents = documentService.findDocumentsByCategories(ids);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 根据多个标签ID查找文档
     * @param tagIds 标签ID列表，用逗号分隔
     * @return 匹配的文档列表
     */
    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> getDocumentsByTags(@RequestParam String tagIds) {
        List<Long> ids = parseIds(tagIds);
        List<Document> documents = documentService.findDocumentsByTags(ids);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 根据创建时间范围查找文档
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 匹配的文档列表
     */
    @GetMapping("/date-range")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> getDocumentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<Document> documents = documentService.findDocumentsByDateRange(startDate, endDate);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 高级搜索：根据标题、内容、分类和标签进行搜索
     * @param title 标题关键词
     * @param content 内容关键词
     * @param categoryIds 分类ID列表，用逗号分隔
     * @param tagIds 标签ID列表，用逗号分隔
     * @param fileType 文件类型
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sort 排序字段
     * @param direction 排序方向（asc/desc）
     * @return 匹配的文档分页列表
     */
    @GetMapping("/advanced-search")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<Page<Document>> advancedSearch(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String tagIds,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        
        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        
        List<Long> catIds = categoryIds != null && !categoryIds.isEmpty() ? parseIds(categoryIds) : null;
        List<Long> tIds = tagIds != null && !tagIds.isEmpty() ? parseIds(tagIds) : null;
        
        Page<Document> documents = documentService.advancedSearch(title, content, catIds, tIds, fileType, startDate, endDate, pageable);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 解析逗号分隔的ID字符串为ID列表
     * @param idsStr ID字符串
     * @return ID列表
     */
    private List<Long> parseIds(String idsStr) {
        return java.util.Arrays.stream(idsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取文档的当前版本号
     * @param id 文档ID
     * @return 当前版本号
     */
    @GetMapping("/{id}/current-version")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<Integer> getCurrentVersion(@PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        
        Integer currentVersion = document.getCurrentVersion();
        if (currentVersion == null) {
            return ResponseEntity.ok(0);
        }
        
        return ResponseEntity.ok(currentVersion);
    }
    
    /**
     * 对所有文档进行向量化处理
     * @return 处理结果
     */
    @PostMapping("/vectorize/all")
    @PreAuthorize("hasAuthority('document:write') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> vectorizeAllDocuments() {
        try {
            int totalChunks = documentChunkService.processAllDocuments();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("totalChunks", totalChunks);
            result.put("message", "✅ 所有文档向量化处理完成，共生成 " + totalChunks + " 个文档块");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "向量化处理失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 对单个文档进行向量化处理
     * @param id 文档ID
     * @return 处理结果
     */
    @PostMapping("/{id}/vectorize")
    @PreAuthorize("hasAuthority('document:write')")
    public ResponseEntity<Map<String, Object>> vectorizeDocument(@PathVariable Long id) {
        try {
            Document document = documentService.getDocumentById(id);
            if (document == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "文档不存在");
                return ResponseEntity.notFound().build();
            }
            
            int chunks = documentChunkService.processDocument(id);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("documentId", id);
            result.put("chunks", chunks);
            result.put("message", "✅ 文档向量化处理完成，共生成 " + chunks + " 个文档块");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "向量化处理失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    
    
    
    
    
}