package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档服务类
 * 提供文档的增删改查和搜索功能
 * @author LiJingLin
 */
@Service
public class DocumentService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private DocumentVersionService documentVersionService;
    
    @Autowired
    private DocumentChunkService documentChunkService;

    @Autowired
    private AiService aiService;
    
    /**
     * 保存文档
     * @param document 文档对象
     * @return 保存后的文档
     */
    public Document saveDocument(Document document) {
        return documentRepository.save(document);
    }
    
    /**
     * 获取所有文档
     * @return 文档列表
     */
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }
    
    /**
     * 获取所有文档（分页）
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    public Page<Document> getAllDocuments(Pageable pageable) {
        return documentRepository.findAll(pageable);
    }
    
    /**
     * 根据ID获取文档（简化方法，供其他服务使用）
     * @param id 文档ID
     * @return 文档对象
     */
    public Document getDocumentById(Long id) {
        Optional<Document> document = documentRepository.findById(id);
        return document.orElse(null);
    }
    
    /**
     * 获取所有文档ID列表
     * @return 文档ID列表
     */
    public List<Long> getAllDocumentIds() {
        return documentRepository.findAllDocumentIds();
    }
    
    /**
     * 根据ID获取文档详细信息（包括分类和标签）
     * @param id 文档ID
     * @return 文档对象
     */
    @Transactional
    public Optional<Document> getDocumentDetailsById(Long id) {
        Optional<Document> document = documentRepository.findById(id);
        if (document.isPresent()) {
            // 强制加载关联的分类和标签
            document.get().getCategoryRelations().size();
            document.get().getTagRelations().size();
        }
        return document;
    }
    
    /**
     * 更新文档
     * @param id 文档ID
     * @param document 更新后的文档信息
     * @return 更新后的文档
     */
    @Transactional
    public Document updateDocument(Long id, Document document) {
        // 获取原始文档
        Document originalDocument = getDocumentById(id);
        if (originalDocument == null) {
            throw new RuntimeException("文档不存在");
        }
        
        // 检查是否有变更
        boolean hasChanges = !originalDocument.getTitle().equals(document.getTitle()) || 
                            !originalDocument.getContent().equals(document.getContent());
        
        // 检查文件类型是否有变更
        boolean fileTypeChanged = false;
        if (originalDocument.getFileType() == null && document.getFileType() != null) {
            fileTypeChanged = true;
        } else if (originalDocument.getFileType() != null && !originalDocument.getFileType().equals(document.getFileType())) {
            fileTypeChanged = true;
        }
        
        // 更新文档基本字段 - 不更新关联集合，避免共享引用问题
        originalDocument.setTitle(document.getTitle());
        originalDocument.setContent(document.getContent());
        originalDocument.setUpdatedAt(LocalDateTime.now()); // 设置新的更新时间
        
        // 如果传入的文档设置了fileType，则更新它
        if (document.getFileType() != null) {
            originalDocument.setFileType(document.getFileType());
        }
        
        Document updatedDocument = documentRepository.save(originalDocument);

        // 标题变化也可能影响回答和引用展示，即使无需重算向量也必须失效旧答案缓存。
        if (hasChanges) {
            aiService.invalidateAnswersByDocumentId(id);
        }
        
        return updatedDocument;
    }
    
    /**
     * 更新文档、创建版本记录，并在正文变化时同步重建检索数据
     * @param id 文档ID
     * @param document 更新后的文档信息
     * @return 更新后的文档
     */
    @Transactional
    public Document updateDocumentWithVersion(Long id, Document document) {
        // 获取原始文档
        Document originalDocument = getDocumentById(id);
        if (originalDocument == null) {
            throw new RuntimeException("文档不存在");
        }
        
        // 检查是否有变更
        boolean titleChanged = !originalDocument.getTitle().equals(document.getTitle());
        boolean contentChanged = !originalDocument.getContent().equals(document.getContent());
        boolean hasChanges = titleChanged || contentChanged;
        
        // 先更新文档
        Document updatedDocument = updateDocument(id, document);
        
        // 如果有变更，创建新版本（在事务外部）
        if (hasChanges) {
            String changeSummary = "文档更新";
            if (titleChanged) {
                changeSummary += " (标题变更)";
            }
            if (contentChanged) {
                changeSummary += " (内容变更)";
            }
            
            try {
                documentVersionService.createVersion(id, changeSummary, "系统");

                Integer maxVersionNumber = documentVersionService.getDocumentVersionRepository().findMaxVersionNumberByDocumentId(id);
                updatedDocument.setCurrentVersion(maxVersionNumber);
                // activeVersion 保持不变，等 BUILD 完成 CAS 切换
                documentRepository.save(updatedDocument);
            } catch (RuntimeException e) {
                System.err.println("创建文档版本失败: " + e.getMessage());
            }
        }

        if (contentChanged) {
            // processDocument 内部通过 SyncAttempt.targetVersion 携带目标版本号
            Integer targetVersion = updatedDocument.getCurrentVersion() != null
                    ? updatedDocument.getCurrentVersion() : 1;
            documentChunkService.processDocumentWithVersion(id, targetVersion);
        }
        
        return updatedDocument;
    }
    
    /**
     * 删除文档
     * @param id 文档ID
     */
    @Transactional
    public void deleteDocument(Long id) {
        aiService.invalidateAnswersByDocumentId(id);
        documentChunkService.deleteChunksByDocumentId(id);
        documentRepository.deleteById(id);
    }
    
    /**
     * 根据标题搜索文档
     * @param title 标题关键词
     * @return 匹配的文档列表
     */
    public List<Document> searchDocumentsByTitle(String title) {
        return documentRepository.findByTitleContainingIgnoreCase(title);
    }
    
    /**
     * 根据内容搜索文档
     * @param content 内容关键词
     * @return 匹配的文档列表
     */
    public List<Document> searchDocumentsByContent(String content) {
        return documentRepository.findByContentContainingIgnoreCase(content);
    }
    
    /**
     * 根据分类ID查找文档
     * @param categoryId 分类ID
     * @return 匹配的文档列表
     */
    public List<Document> findDocumentsByCategory(Long categoryId) {
        return documentRepository.findByCategoryId(categoryId);
    }
    
    /**
     * 根据标签ID查找文档
     * @param tagId 标签ID
     * @return 匹配的文档列表
     */
    public List<Document> findDocumentsByTag(Long tagId) {
        return documentRepository.findByTagId(tagId);
    }
    
    /**
     * 根据多个分类ID查找文档
     * @param categoryIds 分类ID列表
     * @return 匹配的文档列表
     */
    public List<Document> findDocumentsByCategories(List<Long> categoryIds) {
        return documentRepository.findByCategoryIds(categoryIds);
    }
    
    /**
     * 根据多个标签ID查找文档
     * @param tagIds 标签ID列表
     * @return 匹配的文档列表
     */
    public List<Document> findDocumentsByTags(List<Long> tagIds) {
        return documentRepository.findByTagIds(tagIds);
    }
    
    /**
     * 根据创建时间范围查找文档
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 匹配的文档列表
     */
    public List<Document> findDocumentsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return documentRepository.findByCreatedAtBetween(startDate, endDate);
    }
    
    /**
     * 根据更新时间范围查找文档
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 匹配的文档列表
     */
    public List<Document> findDocumentsByUpdatedDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return documentRepository.findByUpdatedAtBetween(startDate, endDate);
    }
    
    /**
     * 高级搜索：根据标题、内容、分类和标签进行搜索
     * @param title 标题关键词
     * @param content 内容关键词
     * @param categoryIds 分类ID列表
     * @param tagIds 标签ID列表
     * @param fileType 文件类型
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @param pageable 分页参数
     * @return 匹配的文档分页列表
     */
    public Page<Document> advancedSearch(String title, String content, List<Long> categoryIds, 
                                       List<Long> tagIds, String fileType, LocalDateTime startDate, 
                                       LocalDateTime endDate, Pageable pageable) {
        return documentRepository.advancedSearch(title, content, categoryIds, tagIds, fileType, startDate, endDate, pageable);
    }
    
    /**
     * 保存文档并进行分块处理
     * @param document 文档对象
     * @param processChunk 是否进行分块处理
     * @return 保存后的文档
     */
    @Transactional
    public Document saveDocument(Document document, boolean processChunk) {
        Document savedDocument = documentRepository.save(document);
        
        // 如果需要进行分块处理
        if (processChunk) {
            documentChunkService.processDocument(savedDocument.getId());
        }
        
        return savedDocument;
    }
    
    /**
     * 更新文档并进行分块处理
     * @param id 文档ID
     * @param document 更新后的文档信息
     * @param processChunk 是否进行分块处理
     * @return 更新后的文档
     */
    @Transactional
    public Document updateDocument(Long id, Document document, boolean processChunk) {
        // 获取原始文档
        Document originalDocument = getDocumentById(id);
        if (originalDocument == null) {
            throw new RuntimeException("文档不存在");
        }
        
        // 检查是否有变更
        boolean hasChanges = !originalDocument.getTitle().equals(document.getTitle()) || 
                            !originalDocument.getContent().equals(document.getContent());
        
        // 更新文档
        document.setId(id);
        Document updatedDocument = documentRepository.save(document);
        
        // 如果有变更，创建新版本
        if (hasChanges) {
            String changeSummary = "文档更新";
            if (!originalDocument.getTitle().equals(document.getTitle())) {
                changeSummary += " (标题变更)";
            }
            if (!originalDocument.getContent().equals(document.getContent())) {
                changeSummary += " (内容变更)";
            }
            
            try {
                documentVersionService.createVersion(id, changeSummary, "系统");
                
                // 更新当前版本号
                Integer maxVersionNumber = documentVersionService.getDocumentVersionRepository().findMaxVersionNumberByDocumentId(id);
                updatedDocument.setCurrentVersion(maxVersionNumber);
                documentRepository.save(updatedDocument);
            } catch (RuntimeException e) {
                // 创建版本失败，但文档更新已经成功，记录日志但不抛出异常
                System.err.println("创建文档版本失败: " + e.getMessage());
            }
            
            // 如果需要进行分块处理
            if (processChunk) {
                documentChunkService.processDocument(id);
            }
        }
        
        return updatedDocument;
    }
    
    /**
     * 删除文档及其相关数据
     * @param id 文档ID
     */
    @Transactional
    public void deleteDocumentWithRelatedData(Long id) {
        // 删除文档分块
        try {
            documentChunkService.deleteChunksByDocumentId(id);
        } catch (Exception e) {
            // 删除分块失败，记录日志但不抛出异常
            System.err.println("删除文档分块失败: " + e.getMessage());
        }
        
        // 删除文档版本
        try {
            documentVersionService.deleteVersionsByDocumentId(id);
        } catch (Exception e) {
            // 删除版本失败，记录日志但不抛出异常
            System.err.println("删除文档版本失败: " + e.getMessage());
        }
        
        // 删除文档
        documentRepository.deleteById(id);
    }
}
