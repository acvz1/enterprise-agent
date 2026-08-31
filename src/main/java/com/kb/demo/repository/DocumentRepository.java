package com.kb.demo.repository;

import com.kb.demo.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    /**
     * 根据标题搜索文档（不区分大小写）
     * @param title 标题关键词
     * @return 匹配的文档列表
     */
    List<Document> findByTitleContainingIgnoreCase(String title);
    
    /**
     * 根据内容搜索文档（不区分大小写）
     * @param content 内容关键词
     * @return 匹配的文档列表
     */
    List<Document> findByContentContainingIgnoreCase(String content);
    
    /**
     * 根据分类ID查找文档
     * @param categoryId 分类ID
     * @return 匹配的文档列表
     */
    @Query("SELECT DISTINCT d FROM Document d JOIN d.categoryRelations cr WHERE cr.category.id = :categoryId")
    List<Document> findByCategoryId(@Param("categoryId") Long categoryId);
    
    /**
     * 根据标签ID查找文档
     * @param tagId 标签ID
     * @return 匹配的文档列表
     */
    @Query("SELECT DISTINCT d FROM Document d JOIN d.tagRelations tr WHERE tr.tag.id = :tagId")
    List<Document> findByTagId(@Param("tagId") Long tagId);
    
    /**
     * 根据多个分类ID查找文档
     * @param categoryIds 分类ID列表
     * @return 匹配的文档列表
     */
    @Query("SELECT DISTINCT d FROM Document d JOIN d.categoryRelations cr WHERE cr.category.id IN :categoryIds")
    List<Document> findByCategoryIds(@Param("categoryIds") List<Long> categoryIds);
    
    /**
     * 根据多个标签ID查找文档
     * @param tagIds 标签ID列表
     * @return 匹配的文档列表
     */
    @Query("SELECT DISTINCT d FROM Document d JOIN d.tagRelations tr WHERE tr.tag.id IN :tagIds")
    List<Document> findByTagIds(@Param("tagIds") List<Long> tagIds);
    
    /**
     * 根据创建时间范围查找文档
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 匹配的文档列表
     */
    List<Document> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 根据更新时间范围查找文档
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 匹配的文档列表
     */
    List<Document> findByUpdatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 高级搜索：根据标题、内容、分类、标签和文件类型进行搜索
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
    @Query("SELECT DISTINCT d FROM Document d " +
           "LEFT JOIN d.categoryRelations cr " +
           "LEFT JOIN d.tagRelations tr " +
           "WHERE (:title IS NULL OR :title = '' OR LOWER(d.title) LIKE LOWER(CONCAT('%', :title, '%'))) " +
           "AND (:content IS NULL OR :content = '' OR LOWER(d.content) LIKE LOWER(CONCAT('%', :content, '%'))) " +
           "AND (:categoryIds IS NULL OR cr.category.id IN :categoryIds) " +
           "AND (:tagIds IS NULL OR tr.tag.id IN :tagIds) " +
           "AND (:fileType IS NULL OR :fileType = '' OR d.fileType = :fileType) " +
           "AND (:startDate IS NULL OR d.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR d.createdAt <= :endDate)")
    Page<Document> advancedSearch(
        @Param("title") String title,
        @Param("content") String content,
        @Param("categoryIds") List<Long> categoryIds,
        @Param("tagIds") List<Long> tagIds,
        @Param("fileType") String fileType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    /**
     * 根据标题或内容搜索文档（不区分大小写）
     * @param title 标题关键词
     * @param content 内容关键词
     * @return 匹配的文档列表
     */
    List<Document> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(String title, String content);
    
    /**
     * 获取所有文档ID列表
     * @return 文档ID列表
     */
    @Query("SELECT d.id FROM Document d")
    List<Long> findAllDocumentIds();

    /** 只取当前数据范围可见的文档 ID，供检索候选在 RRF 前过滤。 */
    @Query("SELECT DISTINCT d.id FROM Document d JOIN d.visibleDepartments department WHERE department.id IN :departmentIds")
    Set<Long> findReadableDocumentIds(@Param("departmentIds") Set<Long> departmentIds);

    /** CAS 切换 active_version：仅当当前值等于 expectedVersion 时才更新，返回受影响行数。 */
    @Modifying
    @Transactional
    @Query("UPDATE Document d SET d.activeVersion = :newVersion WHERE d.id = :id AND d.activeVersion = :expectedVersion")
    int casActiveVersion(@Param("id") Long id,
                         @Param("expectedVersion") Integer expectedVersion,
                         @Param("newVersion") Integer newVersion);

    /** 批量查询指定文档的 activeVersion，供检索层版本过滤使用。 */
    @Query("SELECT d.id, d.activeVersion FROM Document d WHERE d.id IN :ids")
    List<Object[]> findActiveVersionsByIds(@Param("ids") Set<Long> ids);
}
