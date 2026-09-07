package com.kb.demo.dto;

/** 一次歧义澄清中可供用户选择的文档主题。 */
public record ClarificationCandidate(Long documentId, String label) {
}
