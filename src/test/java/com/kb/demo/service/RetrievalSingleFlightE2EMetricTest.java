package com.kb.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通过真实 HybridRetrievalService 链路验证 Single-flight 指标：
 * 100 并发同一 cold-miss query，Vector 和 BM25 实际调用次数从 ~100 降至 1。
 *
 * 不依赖真实 Redis / Elasticsearch — Vector 和 BM25 均通过 mock 计数。
 */
class RetrievalSingleFlightE2EMetricTest {

    private static final int CONCURRENCY = 100;
    private static final String QUERY = "single-flight-e2e-probe";

    private SimpleMeterRegistry meterRegistry;
    private HybridRetrievalService service;
    private AtomicInteger vectorCallCount;
    private AtomicInteger bm25CallCount;

    // 预热线程池，barrier 前已准备就绪
    private ExecutorService pool;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        meterRegistry = new SimpleMeterRegistry();
        vectorCallCount = new AtomicInteger(0);
        bm25CallCount = new AtomicInteger(0);

        // --- VectorSearchService mock：计数 + 持有 200ms 确保所有线程成为 follower ---
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        when(vectorSearchService.searchVectorCandidates(anyString(), anyInt(), anyDouble(), any()))
                .thenAnswer(inv -> {
                    vectorCallCount.incrementAndGet();
                    Thread.sleep(200);
                    return List.of(new RetrievalCandidate(1L, 0, 0.9, 1, RetrievalSource.REDIS_VECTOR, 1));
                });

        // --- ElasticsearchSearchService mock：计数 ---
        ElasticsearchSearchService esService = mock(ElasticsearchSearchService.class);
        when(esService.searchBm25Candidates(anyString(), anyInt(), any()))
                .thenAnswer(inv -> {
                    bm25CallCount.incrementAndGet();
                    return List.of(new RetrievalCandidate(1L, 0, 0.9, 1, RetrievalSource.ELASTICSEARCH_BM25, 1));
                });

        // --- DepartmentAccessService mock：全局 scope，跳过部门过滤 ---
        DepartmentAccessService deptService = mock(DepartmentAccessService.class);
        when(deptService.currentScope())
                .thenReturn(new DepartmentAccessService.AccessScope(true, Set.of()));
        when(deptService.currentScopeCacheKey()).thenReturn("global");

        // --- RetrievalResultService mock：候选直接映射空 hits（验收重点是调用次数）---
        RetrievalResultService resultService = mock(RetrievalResultService.class);
        when(resultService.assembleHits(anyList(), any()))
                .thenReturn(List.of());

        // --- RetrievalHitsCache：Redis mock 始终返回 null（cold miss）---
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        RetrievalHitsCache hitsCache =
                new RetrievalHitsCache(redisTemplate, new ObjectMapper(), meterRegistry);

        // --- RetrievalGenerationService mock ---
        RetrievalGenerationService generationService = mock(RetrievalGenerationService.class);
        when(generationService.currentGeneration()).thenReturn("0");

        RetrievalSingleFlight singleFlight = new RetrievalSingleFlight(meterRegistry);

        service = new HybridRetrievalService(
                vectorSearchService,
                esService,
                new RrfFusionService(),
                resultService,
                deptService,
                hitsCache,
                singleFlight,
                generationService,
                meterRegistry
        );

        // 预热线程池：submit 前已创建好所有线程，避免 barrier 窗口被线程创建时间占用
        pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch poolReady = new CountDownLatch(CONCURRENCY);
        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(poolReady::countDown);
        }
        poolReady.await();
    }

    @Test
    void singleFlight_100ConcurrentColdMiss_vectorAndBm25CalledOnce() throws InterruptedException {
        CountDownLatch startBarrier = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);

        long wallStart = System.currentTimeMillis();
        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(() -> {
                try {
                    startBarrier.await();
                    service.searchHits(QUERY, 10, 0.0, 10);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        startBarrier.countDown();
        done.await();
        pool.shutdown();
        long wallMs = System.currentTimeMillis() - wallStart;

        double leader   = meterRegistry.counter("retrieval.singleflight.leader").count();
        double follower = meterRegistry.counter("retrieval.singleflight.follower").count();

        System.out.printf(
                "[SF E2E] concurrency=%d  vector=%d  bm25=%d  leader=%.0f  follower=%.0f  wall=%dms%n",
                CONCURRENCY, vectorCallCount.get(), bm25CallCount.get(),
                leader, follower, wallMs);

        assertThat(vectorCallCount.get()).isEqualTo(1);
        assertThat(bm25CallCount.get()).isEqualTo(1);
        assertThat(leader).isEqualTo(1.0);
        assertThat(follower).isEqualTo(CONCURRENCY - 1);
    }
}
