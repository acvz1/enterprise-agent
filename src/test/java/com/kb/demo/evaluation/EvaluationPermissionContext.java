package com.kb.demo.evaluation;

import java.util.List;

/** A deterministic evaluation-only representation of the caller's readable document range. */
public record EvaluationPermissionContext(
        String name,
        boolean global,
        List<String> allowedDocumentLogicalIds,
        boolean expectedAccess) {
}
