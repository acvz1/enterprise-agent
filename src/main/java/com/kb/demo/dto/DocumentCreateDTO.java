package com.kb.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

/**
 * 文档创建数据传输对象
 * 用于创建文档时同时设置分类和标签
 * @author LiJingLin
 */
public class DocumentCreateDTO {

    @NotEmpty(message = "文档标题不能为空")
    private String title;

    @NotEmpty(message = "文档内容不能为空")
    private String content;

    private List<Long> categoryIds;

    private List<Long> tagIds;

    /** 新文档的可见部门；普通用户省略时使用自己的可访问部门。 */
    private Set<Long> visibleDepartmentIds;

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Set<Long> getVisibleDepartmentIds() {
        return visibleDepartmentIds;
    }

    public void setVisibleDepartmentIds(Set<Long> visibleDepartmentIds) {
        this.visibleDepartmentIds = visibleDepartmentIds;
    }
}
