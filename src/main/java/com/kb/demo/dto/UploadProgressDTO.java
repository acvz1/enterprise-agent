package com.kb.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 上传进度DTO
 */
public class UploadProgressDTO {
    
    @JsonProperty("uploadId")
    private String uploadId;
    
    @JsonProperty("fileName")
    private String fileName;
    
    @JsonProperty("fileSize")
    private Long fileSize;
    
    @JsonProperty("uploadedSize")
    private Long uploadedSize;
    
    @JsonProperty("percentage")
    private Integer percentage;
    
    @JsonProperty("status")
    private String status;  // UPLOADING/PARSING/CHUNKING/EMBEDDING/COMPLETED/FAILED
    
    @JsonProperty("statusCode")
    private String statusCode;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    // Getters and Setters
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
