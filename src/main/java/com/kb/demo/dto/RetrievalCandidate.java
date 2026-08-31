package com.kb.demo.dto;

public class RetrievalCandidate {
    private final Long documentId;
    private final Integer chunkIndex;
    private final double rawScore;
    private final int rank;
    private final RetrievalSource source;
    private final Integer documentVersion;

    public RetrievalCandidate(Long documentId, Integer chunkIndex, double rawScore, int rank, RetrievalSource source) {
        this(documentId, chunkIndex, rawScore, rank, source, null);
    }

    public RetrievalCandidate(Long documentId, Integer chunkIndex, double rawScore, int rank,
                              RetrievalSource source, Integer documentVersion) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.rawScore = rawScore;
        this.rank = rank;
        this.source = source;
        this.documentVersion = documentVersion;
    }

    public Long getDocumentId() { return documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public double getRawScore() { return rawScore; }
    public int getRank() { return rank; }
    public RetrievalSource getSource() { return source; }
    public Integer getDocumentVersion() { return documentVersion; }
}
