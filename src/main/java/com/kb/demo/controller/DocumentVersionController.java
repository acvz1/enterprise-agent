package com.kb.demo.controller;

import com.kb.demo.entity.DocumentVersion;
import com.kb.demo.service.DocumentVersionService;
import com.kb.demo.service.DocumentService;
import com.kb.demo.entity.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档版本控制器
 * 提供文档版本管理相关的API接口
 * @author LiJingLin
 */
@RestController
@RequestMapping("/api/documents/{documentId}/versions")
public class DocumentVersionController {

    @Autowired
    private DocumentVersionService documentVersionService;

    @Autowired
    private DocumentService documentService;

    /**
     * 创建新版本
     * @param documentId 文档ID
     * @param changeSummary 变更摘要
     * @return 创建的版本
     */
    @PostMapping
    @PreAuthorize("hasAuthority('document:write') and @departmentAccessService.canReadDocumentId(#documentId)")
    public ResponseEntity<DocumentVersion> createVersion(
            @PathVariable Long documentId,
            @RequestParam String changeSummary,
            Authentication authentication) {
        DocumentVersion version = documentVersionService.createVersion(
                documentId, changeSummary, authentication.getName());
        
        // 更新文档的当前版本号
        Document document = documentService.getDocumentById(documentId);
        document.setCurrentVersion(version.getVersionNumber());
        documentService.saveDocument(document);
        
        return ResponseEntity.ok(version);
    }

    /**
     * 获取文档的所有版本
     * @param documentId 文档ID
     * @return 版本列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('document:read') and @departmentAccessService.canReadDocumentId(#documentId)")
    public ResponseEntity<List<DocumentVersion>> getVersionsByDocumentId(@PathVariable Long documentId) {
        List<DocumentVersion> versions = documentVersionService.getVersionsByDocumentId(documentId);
        return ResponseEntity.ok(versions);
    }

    /**
     * 获取文档的最新版本
     * @param documentId 文档ID
     * @return 最新版本
     */
    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('document:read') and @departmentAccessService.canReadDocumentId(#documentId)")
    public ResponseEntity<DocumentVersion> getLatestVersionByDocumentId(@PathVariable Long documentId) {
        DocumentVersion version = documentVersionService.getLatestVersionByDocumentId(documentId);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(version);
    }

    /**
     * 获取特定版本
     * @param documentId 文档ID
     * @param versionNumber 版本号
     * @return 特定版本
     */
    @GetMapping("/{versionNumber}")
    @PreAuthorize("hasAuthority('document:read') and @departmentAccessService.canReadDocumentId(#documentId)")
    public ResponseEntity<DocumentVersion> getVersionByDocumentIdAndVersionNumber(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber) {
        DocumentVersion version = documentVersionService.getVersionByDocumentIdAndVersionNumber(documentId, versionNumber);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(version);
    }

    /**
     * 恢复到指定版本
     * @param documentId 文档ID
     * @param versionNumber 版本号
     * @return 更新后的文档
     */
    @PostMapping("/{versionNumber}/revert")
    @PreAuthorize("hasAuthority('document:write') and @departmentAccessService.canReadDocumentId(#documentId)")
    public ResponseEntity<?> revertToVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber,
            Authentication authentication) {
        try {
            Object document = documentVersionService.revertToVersion(
                    documentId, versionNumber, authentication.getName());
            return ResponseEntity.ok(document);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 比较两个版本的差异
     * @param documentId 文档ID
     * @param versionNumber1 版本号1
     * @param versionNumber2 版本号2
     * @return 差异信息
     */
    @GetMapping("/compare")
    @PreAuthorize("hasAuthority('document:read') and @departmentAccessService.canReadDocumentId(#documentId)")
    public ResponseEntity<?> compareVersions(
            @PathVariable Long documentId,
            @RequestParam Integer versionNumber1,
            @RequestParam Integer versionNumber2) {
        try {
            String diff = documentVersionService.compareVersions(documentId, versionNumber1, versionNumber2);
            return ResponseEntity.ok(diff);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
