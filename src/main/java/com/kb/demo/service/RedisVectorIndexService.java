package com.kb.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 维护文档与 Redis 向量记录之间的关系。
 *
 * LangChain4j 0.34.0 的 RedisEmbeddingStore 不提供按 metadata 删除向量的 API，
 * 因此额外记录每个文档写入 Redis 后返回的 embeddingId，供文档更新和删除时精确清理。
 */
@Service
public class RedisVectorIndexService {

    private static final Logger logger = LoggerFactory.getLogger(RedisVectorIndexService.class);
    private static final String EMBEDDING_KEY_PREFIX = "embedding:";
    private static final String DOCUMENT_REGISTRY_PREFIX = "document-embeddings:document:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisVectorIndexService(
            @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 记录某个文档在 Redis 中生成的向量 ID。
     */
    public void registerEmbedding(Long documentId, String embeddingId) {
        redisTemplate.opsForSet().add(registryKey(documentId), embeddingId);
    }

    /**
     * 删除某个文档登记过的全部 Redis 向量。
     *
     * @return 实际删除的向量 key 数量
     */
    public long deleteByDocumentId(Long documentId) {
        String registryKey = registryKey(documentId);
        Set<String> embeddingIds = redisTemplate.opsForSet().members(registryKey);

        if (embeddingIds == null || embeddingIds.isEmpty()) {
            redisTemplate.delete(registryKey);
            logger.debug("Redis 中没有文档已登记的向量，documentId={}", documentId);
            return 0L;
        }

        List<String> embeddingKeys = embeddingIds.stream()
                .map(id -> EMBEDDING_KEY_PREFIX + id)
                .sorted()
                .toList();
        Long deletedCount = redisTemplate.delete(embeddingKeys);
        redisTemplate.delete(registryKey);

        long count = deletedCount == null ? 0L : deletedCount;
        logger.debug("Redis 文档向量删除完成，documentId={}, deletedCount={}", documentId, count);
        return count;
    }

    private String registryKey(Long documentId) {
        return DOCUMENT_REGISTRY_PREFIX + documentId;
    }
}
