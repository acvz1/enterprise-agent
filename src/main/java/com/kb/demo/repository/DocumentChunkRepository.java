package com.kb.demo.repository;

import com.kb.demo.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 文档块数据访问层
 * 提供文档块的数据访问方法
 * @author LiJingLin
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    
    /**
     * 根据文档ID查找所有文档块，按块索引排序
     * @param documentId 文档ID
     * @return 文档块列表
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);
    
    /**
     * 根据文档ID和块索引查找特定文档块
     * @param documentId 文档ID
     * @param chunkIndex 块索引
     * @return 文档块
     */
    DocumentChunk findByDocumentIdAndChunkIndex(Long documentId, Integer chunkIndex);
    
    /**
     * 根据文档ID删除所有文档块
     * @param documentId 文档ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentChunk dc WHERE dc.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * 获取文档的最大块索引
     * @param documentId 文档ID
     * @return 最大块索引
     */
    @Query("SELECT MAX(dc.chunkIndex) FROM DocumentChunk dc WHERE dc.document.id = :documentId")
    Integer findMaxChunkIndexByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * 统计文档的块数量
     * @param documentId 文档ID
     * @return 块数量
     */
    @Query("SELECT COUNT(dc) FROM DocumentChunk dc WHERE dc.document.id = :documentId")
    Long countByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * 根据内容查找文档块
     * @param content 内容
     * @return 文档块列表
     */
    List<DocumentChunk> findByContentContaining(String content);
    
    /**
     * 检查文档是否已分块处理
     * @param documentId 文档ID
     * @return 是否已处理
     */
    @Query("SELECT CASE WHEN COUNT(dc) > 0 THEN true ELSE false END FROM DocumentChunk dc WHERE dc.document.id = :documentId")
    boolean existsByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * 使用原生SQL保存文档块，避免Hibernate字节码增强问题
     * @param documentId 文档ID
     * @param chunkIndex 块索引
     * @param content 内容
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO document_chunks (document_id, chunk_index, content, created_at, updated_at) " +
                   "VALUES (:documentId, :chunkIndex, :content, NOW(), NOW())", nativeQuery = true)
    void insertChunk(@Param("documentId") Long documentId, 
                    @Param("chunkIndex") Integer chunkIndex, 
                    @Param("content") String content);

    /**
     * 批量查询DocumentChunk同时取回所属Document
     * @param documentIds 文档Id集合
     * @param chunkIndexes 切片索引集合
     * @return 文档切片集合
     */
    @Query("""
    SELECT dc
    FROM DocumentChunk dc
    JOIN FETCH dc.document d
    WHERE d.id IN :documentIds
      AND dc.chunkIndex IN :chunkIndexes
    """)
    List<DocumentChunk> findCandidateChunksWithDocument(
        @Param("documentIds") Set<Long> documentIds,
        @Param("chunkIndexes") Set<Integer> chunkIndexes);
}