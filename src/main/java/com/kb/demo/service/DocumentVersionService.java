package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentVersion;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.repository.DocumentVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档版本服务类
 * 提供文档版本管理相关功能
 * @author LiJingLin
 */
@Service
public class DocumentVersionService {

    @Autowired
    private DocumentVersionRepository documentVersionRepository;
    
    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkService documentChunkService;
    
    /**
     * 获取DocumentVersionRepository
     * @return DocumentVersionRepository
     */
    public DocumentVersionRepository getDocumentVersionRepository() {
        return documentVersionRepository;
    }

    /**
     * 创建新版本
     * @param documentId 文档ID
     * @param changeSummary 变更摘要
     * @param createdBy 创建者
     * @return 创建的版本
     */
    @Transactional
    public DocumentVersion createVersion(Long documentId, String changeSummary, String createdBy) {
        Optional<Document> documentOpt = documentRepository.findById(documentId);
        if (!documentOpt.isPresent()) {
            throw new RuntimeException("文档不存在");
        }
        
        Document document = documentOpt.get();
        
        // 获取当前最大版本号
        Integer maxVersionNumber = documentVersionRepository.findMaxVersionNumberByDocumentId(documentId);
        int newVersionNumber = (maxVersionNumber == null ? 0 : maxVersionNumber) + 1;
        
        // 创建新版本
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(newVersionNumber);
        version.setTitle(document.getTitle());
        version.setContent(document.getContent());
        version.setChangeSummary(changeSummary);
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        
        return documentVersionRepository.save(version);
    }

    /**
     * 获取文档的所有版本
     * @param documentId 文档ID
     * @return 版本列表
     */
    public List<DocumentVersion> getVersionsByDocumentId(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            return java.util.Collections.emptyList();
        }
        
        return documentVersionRepository.findByDocumentId(documentId);
    }

    /**
     * 获取文档的最新版本
     * @param documentId 文档ID
     * @return 最新版本
     */
    public DocumentVersion getLatestVersionByDocumentId(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            return null;
        }
        
        List<DocumentVersion> versions = documentVersionRepository.findLatestVersionByDocumentId(documentId);
        return versions.isEmpty() ? null : versions.get(0);
    }

    /**
     * 获取特定版本
     * @param documentId 文档ID
     * @param versionNumber 版本号
     * @return 特定版本
     */
    public DocumentVersion getVersionByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber) {
        if (!documentRepository.existsById(documentId)) {
            return null;
        }
        
        return documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber);
    }

    /**
     * 恢复到指定版本
     * @param documentId 文档ID
     * @param versionNumber 版本号
     * @param createdBy 操作人
     * @return 更新后的文档
     */
    @Transactional
    public Document revertToVersion(Long documentId, Integer versionNumber, String createdBy) {
        DocumentVersion version = getVersionByDocumentIdAndVersionNumber(documentId, versionNumber);
        if (version == null) {
            throw new RuntimeException("指定版本不存在");
        }
        
        // 获取当前文档
        Optional<Document> documentOpt = documentRepository.findById(documentId);
        if (!documentOpt.isPresent()) {
            throw new RuntimeException("文档不存在");
        }
        
        Document document = documentOpt.get();
        
        // 更新文档内容
        document.setTitle(version.getTitle());
        document.setContent(version.getContent());
        
        // 保存文档
        Document updatedDocument = documentRepository.save(document);
        
        // 创建新版本记录这次回滚操作
        createVersion(documentId, "回滚到版本 " + versionNumber, createdBy);

        // 恢复正文后同步重建 MySQL chunk、Redis 向量和 Elasticsearch 索引。
        documentChunkService.processDocument(documentId);
        
        return updatedDocument;
    }

    /**
     * 比较两个版本的差异
     * @param documentId 文档ID
     * @param versionNumber1 版本号1
     * @param versionNumber2 版本号2
     * @return 差异信息
     */
    public String compareVersions(Long documentId, Integer versionNumber1, Integer versionNumber2) {
        if (!documentRepository.existsById(documentId)) {
            throw new RuntimeException("文档不存在");
        }
        
        DocumentVersion version1 = getVersionByDocumentIdAndVersionNumber(documentId, versionNumber1);
        DocumentVersion version2 = getVersionByDocumentIdAndVersionNumber(documentId, versionNumber2);
        
        if (version1 == null || version2 == null) {
            throw new RuntimeException("指定的版本不存在");
        }
        
        StringBuilder diff = new StringBuilder();
        diff.append("版本 ").append(versionNumber1).append(" 与版本 ").append(versionNumber2).append(" 的差异:\n\n");
        
        // 比较标题
        if (!version1.getTitle().equals(version2.getTitle())) {
            diff.append("标题变更:\n");
            diff.append("  版本 ").append(versionNumber1).append(": ").append(version1.getTitle()).append("\n");
            diff.append("  版本 ").append(versionNumber2).append(": ").append(version2.getTitle()).append("\n\n");
        }
        
        // 比较内容
        if (!version1.getContent().equals(version2.getContent())) {
            diff.append("内容变更:\n");
            diff.append("  版本 ").append(versionNumber1).append(": ").append(version1.getContent().substring(0, Math.min(100, version1.getContent().length()))).append("...\n");
            diff.append("  版本 ").append(versionNumber2).append(": ").append(version2.getContent().substring(0, Math.min(100, version2.getContent().length()))).append("...\n");
        }
        
        return diff.toString();
    }

    /**
     * 删除文档的所有版本
     * @param documentId 文档ID
     */
    @Transactional
    public void deleteVersionsByDocumentId(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new RuntimeException("文档不存在");
        }
        
        documentVersionRepository.deleteByDocumentId(documentId);
    }
}
