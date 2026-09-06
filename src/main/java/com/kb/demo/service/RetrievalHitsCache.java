package com.kb.demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Retrieval Hits Cache — 缓存 RRF 融合后的候选身份列表（不含 MySQL hydration 结果）。
 *
 * Cache key: retrieval:{activeVersionsHash}:{scopeKey}:{queryHash}
 * Cache value: JSON 序列化的 List<FusedRetrievalCandidate>
 *
 * Cache HIT  → 直接返回候选列表，由调用方进行 MySQL hydration + 部门权限二次校验。
 * Cache MISS → 调用方完成 Vector+BM25+RRF，再调 put() 写入。
 */
@Component
public class RetrievalHitsCache {

    private static final Logger log = LoggerFactory.getLogger(RetrievalHitsCache.class);
    private static final String KEY_PREFIX = "retrieval:";
    private static final TypeReference<List<CachedCandidate>> LIST_TYPE = new TypeReference<>() {};

    @Value("${app.retrieval.cache.ttl-seconds:30}")
    private long ttlSeconds = 30;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter hitCounter;
    private final Counter missCounter;

    public RetrievalHitsCache(RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.hitCounter = Counter.builder("retrieval.cache.hit")
                .description("Retrieval hits cache hit count")
                .register(meterRegistry);
        this.missCounter = Counter.builder("retrieval.cache.miss")
                .description("Retrieval hits cache miss count")
                .register(meterRegistry);
    }

    public Optional<List<FusedRetrievalCandidate>> get(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                missCounter.increment();
                return Optional.empty();
            }
            List<CachedCandidate> cached = objectMapper.readValue(json, LIST_TYPE);
            hitCounter.increment();
            return Optional.of(cached.stream().map(CachedCandidate::toFused).toList());
        } catch (Exception e) {
            log.warn("retrieval cache get failed key={}", cacheKey, e);
            missCounter.increment();
            return Optional.empty();
        }
    }

    public void put(String cacheKey, List<FusedRetrievalCandidate> candidates) {
        try {
            List<CachedCandidate> serializable = candidates.stream()
                    .map(CachedCandidate::from)
                    .toList();
            String json = objectMapper.writeValueAsString(serializable);
            redisTemplate.opsForValue().set(cacheKey, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("retrieval cache put failed key={}", cacheKey, e);
        }
    }

    public void evict(String cacheKey) {
        redisTemplate.delete(cacheKey);
    }

    /** 按 activeVersion 相关的 key 模式批量失效（文档版本切换后调用）。 */
    public void evictByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public static String buildKey(String activeVersionsHash, String scopeKey, int queryHash) {
        return KEY_PREFIX + activeVersionsHash + ":" + scopeKey + ":" + queryHash;
    }

    // -----------------------------------------------------------------------
    // Serialization DTO — keeps Jackson independent of FusedRetrievalCandidate's immutable fields
    // -----------------------------------------------------------------------

    public record CachedCandidate(
            Long documentId,
            Integer chunkIndex,
            double fusionScore,
            Set<RetrievalSource> sources,
            Integer documentVersion
    ) {
        static CachedCandidate from(FusedRetrievalCandidate c) {
            return new CachedCandidate(c.getDocumentId(), c.getChunkIndex(),
                    c.getFusionScore(), c.getSources(), c.getDocumentVersion());
        }

        FusedRetrievalCandidate toFused() {
            return new FusedRetrievalCandidate(documentId, chunkIndex, fusionScore, sources, documentVersion);
        }
    }
}
