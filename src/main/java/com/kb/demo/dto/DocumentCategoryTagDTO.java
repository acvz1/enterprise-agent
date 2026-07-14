package com.kb.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 文档分类和标签关联数据传输对象
 * 用于在文档上传或编辑时设置分类和标签
 * @author LiJingLin
 */
public class DocumentCategoryTagDTO {

    @NotNull(message = "文档ID不能为空")
    private Long documentId;

    @NotEmpty(message = "分类ID列表不能为空")
    private List<Long> categoryIds;

    private List<Long> tagIds;

    // Getters and Setters
    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public List<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }
}