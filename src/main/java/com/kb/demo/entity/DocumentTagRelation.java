package com.kb.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 文档标签关联实体类
 * 表示文档与标签的多对多关系
 * @author LiJingLin
 */
@Entity
@Table(name = "document_tag_relations")
@EntityListeners(AuditingEntityListener.class)
public class DocumentTagRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private DocumentTag tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @JsonIgnore
    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public DocumentTag getTag() {
        return tag;
    }

    public void setTag(DocumentTag tag) {
        this.tag = tag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentTagRelation that = (DocumentTagRelation) o;
        return Objects.equals(id, that.id) && 
               Objects.equals(document, that.document) && 
               Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, document, tag);
    }
}