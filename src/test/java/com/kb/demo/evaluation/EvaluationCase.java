package com.kb.demo.evaluation;

import java.util.List;

/** Human-reviewed retrieval gold. No database auto-increment id is part of the schema. */
public record EvaluationCase(
        String id,
        String query,
        EvaluationCategory category,
        List<String> relevantDocs,
        List<StableChunkReference> relevantChunks,
        Answerability answerability,
        EvaluationPermissionContext permissionContext,
        String source,
        String notes) {
}
