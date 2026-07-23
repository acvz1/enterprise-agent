package com.kb.demo.dto;

public enum RetrievalSource {
    // 检索来源
    // Redis 语义向量检索
    REDIS_VECTOR,
    // MySQL 关键词兜底检索
    MYSQL_KEYWORD_FALLBACK,
    // Elasticsearch BM25 关键词检索
    ELASTICSEARCH_BM25
}
