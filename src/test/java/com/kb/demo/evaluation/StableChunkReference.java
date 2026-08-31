package com.kb.demo.evaluation;

/**
 * Stable gold reference. The logical document and section survive a database rebuild;
 * the fixture maps them to the temporary runtime documentId and chunkIndex.
 */
public record StableChunkReference(String documentLogicalId, String sectionId) {
}
