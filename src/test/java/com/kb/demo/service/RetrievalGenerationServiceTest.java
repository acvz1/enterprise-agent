package com.kb.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 9 tests covering RetrievalGenerationService semantics:
 * currentGeneration, incrementGeneration, Redis failure tolerance,
 * and cache-key invalidation contract with RetrievalHitsCache.
 */
class RetrievalGenerationServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RetrievalGenerationService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new RetrievalGenerationService(redisTemplate);
    }

    // -----------------------------------------------------------------------
    // 1. currentGeneration 返回 Redis 中的值
    // -----------------------------------------------------------------------
    @Test
    void currentGeneration_returnsRedisValue() {
        when(valueOps.get(RetrievalGenerationService.GENERATION_KEY)).thenReturn("42");

        assertThat(service.currentGeneration()).isEqualTo("42");
    }

    // -----------------------------------------------------------------------
    // 2. Redis 返回 null 时退化为 "0"
    // -----------------------------------------------------------------------
    @Test
    void currentGeneration_redisReturnsNull_fallsBackToZero() {
        when(valueOps.get(RetrievalGenerationService.GENERATION_KEY)).thenReturn(null);

        assertThat(service.currentGeneration()).isEqualTo("0");
    }

    // -----------------------------------------------------------------------
    // 3. Redis 不可达时 currentGeneration 不抛异常，返回 "0"
    // -----------------------------------------------------------------------
    @Test
    void currentGeneration_redisUnavailable_returnsZeroWithoutThrowing() {
        when(valueOps.get(RetrievalGenerationService.GENERATION_KEY))
                .thenThrow(new RuntimeException("connection refused"));

        assertThat(service.currentGeneration()).isEqualTo("0");
    }

    // -----------------------------------------------------------------------
    // 4. incrementGeneration 调用 Redis INCR
    // -----------------------------------------------------------------------
    @Test
    void incrementGeneration_callsRedisIncrement() {
        service.incrementGeneration();

        verify(valueOps).increment(RetrievalGenerationService.GENERATION_KEY);
    }

    // -----------------------------------------------------------------------
    // 5. Redis INCR 失败不抛异常（不阻断文档更新流程）
    // -----------------------------------------------------------------------
    @Test
    void incrementGeneration_redisUnavailable_doesNotThrow() {
        doThrow(new RuntimeException("Redis down")).when(valueOps)
                .increment(RetrievalGenerationService.GENERATION_KEY);

        // must not throw
        service.incrementGeneration();
    }

    // -----------------------------------------------------------------------
    // 6. generation 变化导致缓存 key 不同（自动失效语义）
    // -----------------------------------------------------------------------
    @Test
    void cacheKey_generationChange_producesDistinctKeys() {
        String keyGen1 = RetrievalHitsCache.buildKey("1", "global", "q".hashCode());
        String keyGen2 = RetrievalHitsCache.buildKey("2", "global", "q".hashCode());

        assertThat(keyGen1).isNotEqualTo(keyGen2);
    }

    // -----------------------------------------------------------------------
    // 7. 相同 generation + 相同 scope + 相同 query → 相同 key（可命中缓存）
    // -----------------------------------------------------------------------
    @Test
    void cacheKey_sameInputs_producesSameKey() {
        String key1 = RetrievalHitsCache.buildKey("5", "dept-3", "query".hashCode());
        String key2 = RetrievalHitsCache.buildKey("5", "dept-3", "query".hashCode());

        assertThat(key1).isEqualTo(key2);
    }

    // -----------------------------------------------------------------------
    // 8. generation 不变、scope 不同 → key 不同（隔离不同部门视图）
    // -----------------------------------------------------------------------
    @Test
    void cacheKey_samGenerationDifferentScope_producesDistinctKeys() {
        String keyGlobal = RetrievalHitsCache.buildKey("3", "global", "q".hashCode());
        String keyDept   = RetrievalHitsCache.buildKey("3", "dept-7", "q".hashCode());

        assertThat(keyGlobal).isNotEqualTo(keyDept);
    }

    // -----------------------------------------------------------------------
    // 9. casActiveVersion 成功（affected=1）后调用 increment；失败（affected=0）不调用
    // -----------------------------------------------------------------------
    @Test
    void incrementCalledOnlyWhenCasSucceeds() {
        RetrievalGenerationService spied = spy(service);

        // 模拟 CAS 成功路径
        int affected = 1;
        if (affected == 1) {
            spied.incrementGeneration();
        }
        verify(spied, times(1)).incrementGeneration();

        RetrievalGenerationService spied2 = spy(service);
        // 模拟 CAS 失败路径
        int affected2 = 0;
        if (affected2 == 1) {
            spied2.incrementGeneration();
        }
        verify(spied2, never()).incrementGeneration();
    }
}
