package com.kb.demo.repository;

import com.kb.demo.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档分类数据访问层
 * @author LiJingLin
 */
@Repository
public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {

    /**
     * 根据名称查找分类
     * @param name 分类名称
     * @return 分类对象
     */
    Optional<DocumentCategory> findByName(String name);

    /**
     * 查询所有分类，按名称排序
     * @return 分类列表
     */
    List<DocumentCategory> findAllByOrderByNameAsc();
}