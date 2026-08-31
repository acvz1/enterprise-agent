package com.kb.demo.evaluation;

/** Query categories are labels for analysis only; they do not change retrieval behaviour. */
public enum EvaluationCategory {
    KEYWORD_EXACT,
    SEMANTIC_PARAPHRASE,
    MIXED,
    AMBIGUOUS,
    PERMISSION_SENSITIVE,
    NO_ANSWER,
    LEGACY_REGRESSION
}
