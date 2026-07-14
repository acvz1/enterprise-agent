package com.kb.demo.service;

import com.kb.demo.dto.DocumentCategoryTagDTO;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentCategory;
import com.kb.demo.entity.DocumentCategoryRelation;
import com.kb.demo.entity.DocumentTag;
import com.kb.demo.entity.DocumentTagRelation;
import com.kb.demo.repository.DocumentCategoryRelationRepository;
import com.kb.demo.repository.DocumentCategoryRepository;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.repository.DocumentTagRelationRepository;
import com.kb.demo.repository.DocumentTagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档分类和标签关联服务层
 * @author LiJingLin
 */
@Service
public class DocumentCategoryTagService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentCategoryRepository documentCategoryRepository;

    @Autowired
    private DocumentTagRepository documentTagRepository;

    @Autowired
    private DocumentCategoryRelationRepository documentCategoryRelationRepository;

    @Autowired
    private DocumentTagRelationRepository documentTagRelationRepository;

    /**
     * 设置文档的分类和标签
     * @param documentCategoryTagDTO 文档分类和标签关联DTO
     */
    @Transactional
    public void setDocumentCategoriesAndTags(DocumentCategoryTagDTO documentCategoryTagDTO) {
        // 获取文档
        Document document = documentRepository.findById(documentCategoryTagDTO.getDocumentId())
                .orElseThrow(() -> new RuntimeException("文档不存在，ID: " + documentCategoryTagDTO.getDocumentId()));

        // 处理分类关联
        updateDocumentCategories(document, documentCategoryTagDTO.getCategoryIds());

        // 处理标签关联
        updateDocumentTags(document, documentCategoryTagDTO.getTagIds());
    }

    /**
     * 更新文档的分类关联
     * @param document 文档实体
     * @param categoryIds 分类ID列表
     */
    private void updateDocumentCategories(Document document, List<Long> categoryIds) {
        // 删除现有的分类关联
        documentCategoryRelationRepository.deleteByDocumentId(document.getId());

        // 创建新的分类关联
        if (categoryIds != null && !categoryIds.isEmpty()) {
            List<DocumentCategoryRelation> relations = new ArrayList<>();
            for (Long categoryId : categoryIds) {
                DocumentCategory category = documentCategoryRepository.findById(categoryId)
                        .orElseThrow(() -> new RuntimeException("分类不存在，ID: " + categoryId));
                
                DocumentCategoryRelation relation = new DocumentCategoryRelation();
                relation.setDocument(document);
                relation.setCategory(category);
                relations.add(relation);
            }
            documentCategoryRelationRepository.saveAll(relations);
        }
    }

    /**
     * 更新文档的标签关联
     * @param document 文档实体
     * @param tagIds 标签ID列表
     */
    private void updateDocumentTags(Document document, List<Long> tagIds) {
        // 删除现有的标签关联
        documentTagRelationRepository.deleteByDocumentId(document.getId());

        // 创建新的标签关联
        if (tagIds != null && !tagIds.isEmpty()) {
            List<DocumentTagRelation> relations = new ArrayList<>();
            for (Long tagId : tagIds) {
                DocumentTag tag = documentTagRepository.findById(tagId)
                        .orElseThrow(() -> new RuntimeException("标签不存在，ID: " + tagId));
                
                DocumentTagRelation relation = new DocumentTagRelation();
                relation.setDocument(document);
                relation.setTag(tag);
                relations.add(relation);
            }
            documentTagRelationRepository.saveAll(relations);
        }
    }

    /**
     * 获取文档的分类ID列表
     * @param documentId 文档ID
     * @return 分类ID列表
     */
    public List<Long> getDocumentCategoryIds(Long documentId) {
        return documentCategoryRelationRepository.findCategoryIdsByDocumentId(documentId);
    }

    /**
     * 获取文档的标签ID列表
     * @param documentId 文档ID
     * @return 标签ID列表
     */
    public List<Long> getDocumentTagIds(Long documentId) {
        return documentTagRelationRepository.findTagIdsByDocumentId(documentId);
    }

    /**
     * 获取文档的分类名称列表
     * @param documentId 文档ID
     * @return 分类名称列表
     */
    public List<String> getDocumentCategoryNames(Long documentId) {
        List<Long> categoryIds = getDocumentCategoryIds(documentId);
        if (categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return documentCategoryRepository.findAllById(categoryIds).stream()
                .map(DocumentCategory::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取文档的标签名称列表
     * @param documentId 文档ID
     * @return 标签名称列表
     */
    public List<String> getDocumentTagNames(Long documentId) {
        List<Long> tagIds = getDocumentTagIds(documentId);
        if (tagIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return documentTagRepository.findAllById(tagIds).stream()
                .map(DocumentTag::getName)
                .collect(Collectors.toList());
    }
}