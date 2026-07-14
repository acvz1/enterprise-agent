package com.kb.demo.controller;

import com.kb.demo.entity.Document;
import com.kb.demo.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 向量检索控制器
 * 提供向量检索相关的API接口
 * @author LiJingLin
 */
@RestController
@RequestMapping("/api/vector-search")
public class VectorSearchController {
    
    @Autowired
    private VectorSearchService vectorSearchService;
    
    /**
     * 向量检索文档
     * @param query 查询文本
     * @param maxResults 最大结果数量
     * @param minScore 最小相似度阈值
     * @return 匹配的文档列表
     */
    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults,
            @RequestParam(defaultValue = "0.7") double minScore) {
        
        List<Document> documents = vectorSearchService.searchDocuments(query, maxResults, minScore);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 分页向量检索文档
     * @param query 查询文本
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sort 排序字段
     * @param direction 排序方向（asc/desc）
     * @return 分页文档列表
     */
    @GetMapping("/documents/page")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<Page<Document>> searchDocumentsPage(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        
        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        
        Page<Document> documents = vectorSearchService.searchDocuments(query, pageable);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 混合检索文档
     * @param query 查询文本
     * @param maxResults 最大结果数量
     * @param vectorWeight 向量检索权重（0-1）
     * @param keywordWeight 关键词检索权重（0-1）
     * @return 匹配的文档列表
     */
    @GetMapping("/documents/hybrid")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<Document>> hybridSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults,
            @RequestParam(defaultValue = "0.6") double vectorWeight,
            @RequestParam(defaultValue = "0.4") double keywordWeight) {
        
        List<Document> documents = vectorSearchService.hybridSearch(query, maxResults, vectorWeight, keywordWeight);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * 获取文档的相关段落
     * @param documentId 文档ID
     * @param query 查询文本
     * @param maxSegments 最大段落数量
     * @return 相关段落列表
     */
    @GetMapping("/documents/{documentId}/segments")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<String>> getRelevantSegments(
            @PathVariable Long documentId,
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int maxSegments) {
        
        List<String> segments = vectorSearchService.getRelevantSegments(documentId, query, maxSegments);
        return ResponseEntity.ok(segments);
    }
    
    /**
     * 向量检索统计信息
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // 这里可以添加一些统计信息，比如向量存储的文档数量等
        Map<String, Object> stats = Map.of(
                "message", "向量检索服务正常运行",
                "features", List.of(
                        "基于Redis的向量存储",
                        "支持余弦相似度计算",
                        "支持混合检索（向量+关键词）",
                        "支持文档分块处理"
                )
        );
        return ResponseEntity.ok(stats);
    }
}