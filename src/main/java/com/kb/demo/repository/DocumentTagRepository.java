package com.kb.demo.repository;

import com.kb.demo.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档标签数据访问层
 * @author LiJingLin
 */
@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {

    /**
     * 根据名称查找标签
     * @param name 标签名称
     * @return 标签对象
     */
    Optional<DocumentTag> findByName(String name);

    /**
     * 查询所有标签，按名称排序
     * @return 标签列表
     */
    List<DocumentTag> findAllByOrderByNameAsc();
}