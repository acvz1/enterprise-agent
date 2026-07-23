package com.kb.demo.dto;

public class ElasticsearchChunkDocument {
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    
    public ElasticsearchChunkDocument(Long documentId, Integer chunkIndex, String content) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public ElasticsearchChunkDocument() {
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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
    
    
}
