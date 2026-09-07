package com.kb.demo.dto;

import java.util.List;

/**
 * 等待用户选择的澄清状态；由 scoped sessionId（userId + sessionId）隔离。
 */
public record PendingClarification(
        String originalQuery,
        List<ClarificationCandidate> candidates,
        String createdAt) {

    public PendingClarification {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
