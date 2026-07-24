package com.kb.demo.dto;
import java.util.Set;

public class FusedRetrievalCandidate {
    //保存同一 chunk 经过 Redis 和 ES 融合后的结果。
    private final Long documentId;
    private final Integer chunkIndex;
    private final double fusionScore;
    private final Set<RetrievalSource> sources;

    public FusedRetrievalCandidate(Long documentId, Integer chunkIndex, double fusionScore,
        Set<RetrievalSource> sources) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.fusionScore = fusionScore;
        this.sources = sources;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public double getFusionScore() {
        return fusionScore;
    }

    public Set<RetrievalSource> getSources() {
        return sources;
    }

    

    }

