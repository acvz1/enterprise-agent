package com.kb.demo.dto;
import java.util.Set;

public class RetrievalHit {
    private Long documentId;
    private Long chunkId;
    private Integer chunkIndex;
    private String documentTitle;
    private String content;
    private double fusionScore;
    private Set<RetrievalSource> sources;
    private Integer documentVersion;

    public RetrievalHit() {
    }

    public RetrievalHit(Long documentId, Long chunkId, Integer chunkIndex, String documentTitle, String content,
            double fusionScore, Set<RetrievalSource> sources) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.chunkIndex = chunkIndex;
        this.documentTitle = documentTitle;
        this.content = content;
        this.fusionScore = fusionScore;
        this.sources = sources;
    }

    public Long getDocumentId() { return documentId; }
    public Long getChunkId() { return chunkId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public String getDocumentTitle() { return documentTitle; }
    public String getContent() { return content; }
    public double getFusionScore() { return fusionScore; }
    public Set<RetrievalSource> getSources() { return sources; }
    public Integer getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(Integer documentVersion) { this.documentVersion = documentVersion; }
}
