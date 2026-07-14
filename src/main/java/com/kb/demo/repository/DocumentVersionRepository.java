package com.kb.demo.repository;

import com.kb.demo.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档版本仓库接口
 * 提供文档版本的数据访问方法
 * @author LiJingLin
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    
    /**
     * 根据文档ID查找所有版本
     * @param documentId 文档ID
     * @return 版本列表
     */
    List<DocumentVersion> findByDocumentId(Long documentId);
    
    /**
     * 根据文档ID查找最新版本
     * @param documentId 文档ID
     * @return 最新版本
     */
    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :documentId ORDER BY dv.versionNumber DESC")
    List<DocumentVersion> findLatestVersionByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * 根据文档ID和版本号查找特定版本
     * @param documentId 文档ID
     * @param versionNumber 版本号
     * @return 特定版本
     */
    DocumentVersion findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);
    
    /**
     * 获取文档的最大版本号
     * @param documentId 文档ID
     * @return 最大版本号
     */
    @Query("SELECT MAX(dv.versionNumber) FROM DocumentVersion dv WHERE dv.document.id = :documentId")
    Integer findMaxVersionNumberByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * 根据文档ID删除所有版本
     * @param documentId 文档ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentVersion dv WHERE dv.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}