package com.kb.demo.dto;
import java.util.Set;

public class FusedRetrievalCandidate {
    private final Long documentId;
    private final Integer chunkIndex;
    private final double fusionScore;
    private final Set<RetrievalSource> sources;
    private final Integer documentVersion;

    public FusedRetrievalCandidate(Long documentId, Integer chunkIndex, double fusionScore,
                                   Set<RetrievalSource> sources) {
        this(documentId, chunkIndex, fusionScore, sources, null);
    }

    public FusedRetrievalCandidate(Long documentId, Integer chunkIndex, double fusionScore,
                                   Set<RetrievalSource> sources, Integer documentVersion) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.fusionScore = fusionScore;
        this.sources = sources;
        this.documentVersion = documentVersion;
    }

    public Long getDocumentId() { return documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public double getFusionScore() { return fusionScore; }
    public Set<RetrievalSource> getSources() { return sources; }
    public Integer getDocumentVersion() { return documentVersion; }
}

