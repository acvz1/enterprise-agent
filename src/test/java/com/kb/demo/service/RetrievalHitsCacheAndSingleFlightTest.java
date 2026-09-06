package com.kb.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetrievalHitsCacheAndSingleFlightTest {

    private SimpleMeterRegistry meterRegistry;
    private RetrievalHitsCache cache;
    private RetrievalSingleFlight singleFlight;
    private RedisTemplate<String, String> redisTemplate;

    private static final FusedRetrievalCandidate CANDIDATE_1 =
            new FusedRetrievalCandidate(1L, 0, 0.9, Set.of(RetrievalSource.REDIS_VECTOR), 1);
    private static final FusedRetrievalCandidate CANDIDATE_2 =
            new FusedRetrievalCandidate(2L, 1, 0.7, Set.of(RetrievalSource.ELASTICSEARCH_BM25), 1);

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cache = new RetrievalHitsCache(redisTemplate, new ObjectMapper(), meterRegistry);
        singleFlight = new RetrievalSingleFlight(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // 1. cache hit — 跳过 Vector+BM25
    // -----------------------------------------------------------------------
    @Test
    void cacheHit_returnsCachedCandidatesWithoutRetrieval() {
        String key = "retrieval:v1:global:123";
        // put 填充真实 Redis mock
        cache.put(key, List.of(CANDIDATE_1));

        // 重新 mock get 返回 JSON（绕过 Redis，直接验证 get 路径）
        String json = """
                [{"documentId":1,"chunkIndex":0,"fusionScore":0.9,\
                "sources":["REDIS_VECTOR"],"documentVersion":1}]""";
        when(redisTemplate.opsForValue().get(key)).thenReturn(json);

        Optional<List<FusedRetrievalCandidate>> result = cache.get(key);

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getDocumentId()).isEqualTo(1L);
        assertThat(meterRegistry.counter("retrieval.cache.hit").count()).isEqualTo(1.0);
    }

    // -----------------------------------------------------------------------
    // 2. cache miss — 返回 empty，触发 retrieval
    // -----------------------------------------------------------------------
    @Test
    void cacheMiss_returnsEmpty() {
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        Optional<List<FusedRetrievalCandidate>> result = cache.get("retrieval:v1:global:999");

        assertThat(result).isEmpty();
        assertThat(meterRegistry.counter("retrieval.cache.miss").count()).isEqualTo(1.0);
    }

    // -----------------------------------------------------------------------
    // 3. 100 并发同一 query — single-flight 保证只调用一次 retrieval
    // -----------------------------------------------------------------------
    @Test
    void singleFlight_100ConcurrentSameQuery_retrievalCalledOnce() throws InterruptedException {
        String key = "retrieval:v1:global:samequery";
        AtomicInteger retrievalCallCount = new AtomicInteger(0);
        int threads = 100;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    singleFlight.execute(key, () -> {
                        retrievalCallCount.incrementAndGet();
                        // 持有 future 足够长时间，让其余 99 个线程都到达 putIfAbsent 成为 follower
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        return List.of(CANDIDATE_1);
                    });
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        pool.shutdown();

        // leader 恰好执行一次，follower 共享结果
        assertThat(retrievalCallCount.get()).isEqualTo(1);
        assertThat(meterRegistry.counter("retrieval.singleflight.leader").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("retrieval.singleflight.follower").count()).isEqualTo(threads - 1);
    }

    // -----------------------------------------------------------------------
    // 4. 不同 query — 各自独立执行 retrieval
    // -----------------------------------------------------------------------
    @Test
    void singleFlight_differentQueries_eachExecutesIndependently() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        int queries = 5;
        CountDownLatch done = new CountDownLatch(queries);
        ExecutorService pool = Executors.newFixedThreadPool(queries);

        for (int i = 0; i < queries; i++) {
            final String key = "retrieval:v1:global:query" + i;
            pool.submit(() -> {
                singleFlight.execute(key, () -> {
                    callCount.incrementAndGet();
                    return List.of(CANDIDATE_1);
                });
                done.countDown();
            });
        }

        done.await();
        pool.shutdown();

        assertThat(callCount.get()).isEqualTo(queries);
    }

    // -----------------------------------------------------------------------
    // 5. scope 隔离 — 不同 scopeKey 不命中对方的缓存
    // -----------------------------------------------------------------------
    @Test
    void cacheKey_scopeIsolation_differentScopesDontShareCache() {
        String keyGlobal = RetrievalHitsCache.buildKey("v1", "global", "question".hashCode());
        String keyDept = RetrievalHitsCache.buildKey("v1", "dept-5", "question".hashCode());

        assertThat(keyGlobal).isNotEqualTo(keyDept);
    }

    // -----------------------------------------------------------------------
    // 6. leader 异常 — follower 收到相同异常，key 移除后下次请求重新竞争
    // -----------------------------------------------------------------------
    @Test
    void singleFlight_leaderException_followerReceivesSameException() throws InterruptedException {
        String key = "retrieval:v1:global:exception-query";
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch followerDone = new CountDownLatch(1);
        AtomicInteger followerExceptionCount = new AtomicInteger(0);
        AtomicInteger secondRequestCallCount = new AtomicInteger(0);

        // leader 线程：让 follower 进来后抛异常
        Thread leader = new Thread(() -> {
            try {
                singleFlight.execute(key, () -> {
                    leaderStarted.countDown();
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    throw new RuntimeException("leader retrieval failed");
                });
            } catch (RuntimeException ignored) {}
        });

        // follower 线程：等 leader 开始后加入，期望收到异常
        Thread follower = new Thread(() -> {
            try {
                leaderStarted.await();
                singleFlight.execute(key, () -> List.of(CANDIDATE_2));
            } catch (Exception e) {
                followerExceptionCount.incrementAndGet();
            } finally {
                followerDone.countDown();
            }
        });

        leader.start();
        follower.start();
        leader.join(2000);
        followerDone.await();

        // follower 收到异常，没有自行执行 retrieval
        assertThat(followerExceptionCount.get()).isEqualTo(1);

        // leader/follower 都失败后，key 已从 inFlight 移除，下一请求可正常竞争 leader
        singleFlight.execute(key, () -> {
            secondRequestCallCount.incrementAndGet();
            return List.of(CANDIDATE_1);
        });
        assertThat(secondRequestCallCount.get()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // 7. activeVersion 切换 — cache key 因版本哈希变化而自动失效
    // -----------------------------------------------------------------------
    @Test
    void cacheKey_activeVersionChange_producesNewKey() {
        // v1 版本的哈希
        String keyV1 = RetrievalHitsCache.buildKey("hash-v1", "global", "q".hashCode());
        // v2 版本的哈希（文档重建切换后）
        String keyV2 = RetrievalHitsCache.buildKey("hash-v2", "global", "q".hashCode());

        assertThat(keyV1).isNotEqualTo(keyV2);
        // v1 缓存对 v2 透明（不同 key，自然不命中）
        when(redisTemplate.opsForValue().get(keyV2)).thenReturn(null);
        assertThat(cache.get(keyV2)).isEmpty();
    }
}
