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
     * 记录某个文档在 Redis 中生成的向量 ID（按版本分桶）。
     */
    public void registerEmbedding(Long documentId, String embeddingId, Integer version) {
        redisTemplate.opsForSet().add(registryKey(documentId, version), embeddingId);
    }

    /** 向后兼容：无版本参数时写入 v1 桶。 */
    public void registerEmbedding(Long documentId, String embeddingId) {
        registerEmbedding(documentId, embeddingId, 1);
    }

    /**
     * 删除某个文档指定版本的全部 Redis 向量。
     */
    public long deleteByDocumentIdAndVersion(Long documentId, Integer version) {
        String registryKey = registryKey(documentId, version);
        Set<String> embeddingIds = redisTemplate.opsForSet().members(registryKey);

        if (embeddingIds == null || embeddingIds.isEmpty()) {
            redisTemplate.delete(registryKey);
            logger.debug("Redis 中没有文档 v{} 已登记的向量，documentId={}", version, documentId);
            return 0L;
        }

        List<String> embeddingKeys = embeddingIds.stream()
                .map(id -> EMBEDDING_KEY_PREFIX + id)
                .sorted()
                .toList();
        Long deletedCount = redisTemplate.delete(embeddingKeys);
        redisTemplate.delete(registryKey);

        long count = deletedCount == null ? 0L : deletedCount;
        logger.debug("Redis 文档 v{} 向量删除完成，documentId={}, deletedCount={}", version, documentId, count);
        return count;
    }

    /**
     * 删除某个文档登记过的全部 Redis 向量（所有版本）。
     *
     * @return 实际删除的向量 key 数量
     */
    public long deleteByDocumentId(Long documentId) {
        Set<String> registryKeys = redisTemplate.keys(DOCUMENT_REGISTRY_PREFIX + documentId + ":*");
        if (registryKeys == null || registryKeys.isEmpty()) {
            logger.debug("Redis 中没有文档已登记的向量，documentId={}", documentId);
            return 0L;
        }
        long totalDeleted = 0L;
        for (String registryKey : registryKeys) {
            Set<String> embeddingIds = redisTemplate.opsForSet().members(registryKey);
            if (embeddingIds != null && !embeddingIds.isEmpty()) {
                List<String> embeddingKeys = embeddingIds.stream()
                        .map(id -> EMBEDDING_KEY_PREFIX + id).toList();
                Long deleted = redisTemplate.delete(embeddingKeys);
                totalDeleted += deleted == null ? 0L : deleted;
            }
            redisTemplate.delete(registryKey);
        }
        logger.debug("Redis 文档向量全版本删除完成，documentId={}, deletedCount={}", documentId, totalDeleted);
        return totalDeleted;
    }

    /** 指定版本的向量数量，用于 validateRebuild。 */
    public long countByDocumentIdAndVersion(Long documentId, Integer version) {
        Long size = redisTemplate.opsForSet().size(registryKey(documentId, version));
        return size == null ? 0L : size;
    }

    /** 当前文档登记的向量数量（所有版本之和），用于写入后的最终一致性校验。 */
    public long countByDocumentId(Long documentId) {
        Set<String> registryKeys = redisTemplate.keys(DOCUMENT_REGISTRY_PREFIX + documentId + ":*");
        if (registryKeys == null || registryKeys.isEmpty()) return 0L;
        long total = 0L;
        for (String key : registryKeys) {
            Long size = redisTemplate.opsForSet().size(key);
            total += size == null ? 0L : size;
        }
        return total;
    }

    /**
     * 清理全量重建前遗留的文档向量 ID 登记表。
     */
    public long clearAllRegistrations() {
        Set<String> registryKeys = redisTemplate.keys(DOCUMENT_REGISTRY_PREFIX + "*");
        if (registryKeys == null || registryKeys.isEmpty()) {
            return 0L;
        }

        Long deletedCount = redisTemplate.delete(registryKeys);
        return deletedCount == null ? 0L : deletedCount;
    }

    private String registryKey(Long documentId, Integer version) {
        return DOCUMENT_REGISTRY_PREFIX + documentId + ":" + version;
    }
}
