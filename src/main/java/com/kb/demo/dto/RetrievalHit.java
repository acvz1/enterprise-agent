package com.kb.demo.dto;

public class RetrievalHit {
    // 检索命中结果
    private Long documentId;
    private Long chunkId;
    private Integer chunkIndex;
    private String documentTitle;
    private String content;
    private double score;
    private RetrievalSource source;

    public RetrievalHit() {
    }

    public RetrievalHit(Long documentId, Long chunkId, Integer chunkIndex, String documentTitle, String content,
            double score, RetrievalSource source) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.chunkIndex = chunkIndex;
        this.documentTitle = documentTitle;
        this.content = content;
        this.score = score;
        this.source = source;
    }

    public Long getDocumentId() {
        return documentId;
    }
    public Long getChunkId() {
        return chunkId;
    }
    public Integer getChunkIndex() {
        return chunkIndex;
    }
    public String getDocumentTitle() {
        return documentTitle;
    }
    public String getContent() {
        return content;
    }
    public double getScore() {
        return score;
    }
    public RetrievalSource getSource() {
        return source;
    }

}
