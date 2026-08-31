package com.kb.demo.dto;

public class ElasticsearchChunkDocument {
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    
    private Integer documentVersion;

    public ElasticsearchChunkDocument(Long documentId, Integer chunkIndex, String content, Integer documentVersion) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.documentVersion = documentVersion;
    }

    public ElasticsearchChunkDocument() {
    }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(Integer documentVersion) { this.documentVersion = documentVersion; }
}
