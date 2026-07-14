package com.kb.demo.repository;

import com.kb.demo.entity.DocumentTagRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档标签关联数据访问层
 * @author LiJingLin
 */
@Repository
public interface DocumentTagRelationRepository extends JpaRepository<DocumentTagRelation, Long> {

    /**
     * 根据文档ID查找所有标签关联
     * @param documentId 文档ID
     * @return 标签关联列表
     */
    List<DocumentTagRelation> findByDocumentId(Long documentId);

    /**
     * 根据标签ID查找所有文档关联
     * @param tagId 标签ID
     * @return 文档关联列表
     */
    List<DocumentTagRelation> findByTagId(Long tagId);

    /**
     * 根据文档ID删除所有标签关联
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 查询指定文档的标签ID列表
     * @param documentId 文档ID
     * @return 标签ID列表
     */
    @Query("SELECT dtr.tag.id FROM DocumentTagRelation dtr WHERE dtr.document.id = :documentId")
    List<Long> findTagIdsByDocumentId(@Param("documentId") Long documentId);
}