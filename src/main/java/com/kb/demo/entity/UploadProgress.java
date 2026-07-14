package com.kb.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 上传进度实体
 * 用于追踪文件上传和处理的进度
 * @author LiJingLin
 */
@Entity
@Table(name = "upload_progress")
public class UploadProgress {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String uploadId;  // 唯一上传ID
    
    @Column(nullable = false)
    private String fileName;
    
    @Column(nullable = false)
    private Long fileSize;  // 文件总大小（字节）
    
    @Column(nullable = false)
    private Long uploadedSize = 0L;  // 已上传大小
    
    @Column(nullable = false)
    private Integer percentage = 0;  // 上传进度百分比
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UploadStatus status;  // 上传状态
    
    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;  // 错误信息
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUploadId() {
        return uploadId;
    }
    
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public Long getUploadedSize() {
        return uploadedSize;
    }
    
    public void setUploadedSize(Long uploadedSize) {
        this.uploadedSize = uploadedSize;
    }
    
    public Integer getPercentage() {
        return percentage;
    }
    
    public void setPercentage(Integer percentage) {
        this.percentage = percentage;
    }
    
    public UploadStatus getStatus() {
        return status;
    }
    
    public void setStatus(UploadStatus status) {
        this.status = status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
     * 上传状态枚举
     */
    public enum UploadStatus {
        UPLOADING("上传中"),
        PARSING("解析中"),
        CHUNKING("分块中"),
        EMBEDDING("向量化中"),
        COMPLETED("已完成"),
        FAILED("失败");
        
        private final String description;
        
        UploadStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
