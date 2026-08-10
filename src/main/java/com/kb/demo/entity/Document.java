package com.kb.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 文档实体类
 * 表示知识库中的文档数据
 * @author LiJingLin
 */
@Entity
@Table(name = "documents")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DocumentCategoryRelation> categoryRelations;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DocumentTagRelation> tagRelations;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DocumentVersion> versions;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DocumentChunk> chunks;

    /** 可读取此文档的部门；空集合代表默认拒绝普通用户，仅管理员可见。 */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "document_visible_departments",
        joinColumns = @JoinColumn(name = "document_id"),
        inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @JsonIgnore
    private Set<Department> visibleDepartments = new LinkedHashSet<>();

    /** 仅用于创建请求，真正持久化的关系是 visibleDepartments。 */
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Set<Long> visibleDepartmentIds = new LinkedHashSet<>();

    @Column(name = "current_version")
    private Integer currentVersion;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public List<DocumentCategoryRelation> getCategoryRelations() {
        return categoryRelations;
    }

    public void setCategoryRelations(List<DocumentCategoryRelation> categoryRelations) {
        this.categoryRelations = categoryRelations;
    }

    public List<DocumentTagRelation> getTagRelations() {
        return tagRelations;
    }

    public void setTagRelations(List<DocumentTagRelation> tagRelations) {
        this.tagRelations = tagRelations;
    }

    public List<DocumentVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<DocumentVersion> versions) {
        this.versions = versions;
    }

    public List<DocumentChunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<DocumentChunk> chunks) {
        this.chunks = chunks;
    }

    public Set<Department> getVisibleDepartments() {
        return visibleDepartments;
    }

    public void setVisibleDepartments(Set<Department> visibleDepartments) {
        this.visibleDepartments = visibleDepartments == null ? new LinkedHashSet<>() : visibleDepartments;
    }

    public Set<Long> getVisibleDepartmentIds() {
        return visibleDepartmentIds;
    }

    public void setVisibleDepartmentIds(Set<Long> visibleDepartmentIds) {
        this.visibleDepartmentIds = visibleDepartmentIds == null ? new LinkedHashSet<>() : visibleDepartmentIds;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
