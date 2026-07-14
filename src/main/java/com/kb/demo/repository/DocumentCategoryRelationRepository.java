package com.kb.demo.repository;

import com.kb.demo.entity.DocumentCategoryRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档分类关联数据访问层
 * @author LiJingLin
 */
@Repository
public interface DocumentCategoryRelationRepository extends JpaRepository<DocumentCategoryRelation, Long> {

    /**
     * 根据文档ID查找所有分类关联
     * @param documentId 文档ID
     * @return 分类关联列表
     */
    List<DocumentCategoryRelation> findByDocumentId(Long documentId);

    /**
     * 根据分类ID查找所有文档关联
     * @param categoryId 分类ID
     * @return 文档关联列表
     */
    List<DocumentCategoryRelation> findByCategoryId(Long categoryId);

    /**
     * 根据文档ID删除所有分类关联
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 查询指定文档的分类ID列表
     * @param documentId 文档ID
     * @return 分类ID列表
     */
    @Query("SELECT dcr.category.id FROM DocumentCategoryRelation dcr WHERE dcr.document.id = :documentId")
    List<Long> findCategoryIdsByDocumentId(@Param("documentId") Long documentId);
}