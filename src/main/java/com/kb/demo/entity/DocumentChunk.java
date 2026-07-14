package com.kb.demo.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 文档块实体类
 * 用于存储文档分割后的块信息
 * @author LiJingLin
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "embedding_vector", columnDefinition = "BLOB")
    @JdbcTypeCode(SqlTypes.BLOB)
    private byte[] embeddingVector;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * 默认构造函数
     */
    public DocumentChunk() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 带参数的构造函数
     * @param document 文档对象
     * @param chunkIndex 块索引
     * @param content 块内容
     */
    public DocumentChunk(Document document, Integer chunkIndex, String content) {
        this();
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Document getDocument() {
        return document;
    }
    
    public void setDocument(Document document) {
        this.document = document;
    }
    
    public Integer getChunkIndex() {
        return chunkIndex;
    }
    
    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public byte[] getEmbeddingVector() {
        return embeddingVector;
    }
    
    public void setEmbeddingVector(byte[] embeddingVector) {
        this.embeddingVector = embeddingVector;
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
    
    /**
     * 更新时间戳
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}