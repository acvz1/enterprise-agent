package com.kb.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 并发压测：baseline / warm cache / cold 100-concurrent same query。
 * 不依赖真实 Redis 或 Elasticsearch — 通过 mock 量化 retrieval 调用次数与延迟分布。
 *
 * 输出指标：wall-clock time, QPS, P50/P95/P99 latency per thread, leader/follower counts.
 */
class RetrievalConcurrencyBenchmarkTest {

    private SimpleMeterRegistry meterRegistry;
    private RetrievalHitsCache cache;
    private RetrievalSingleFlight singleFlight;

    private static final FusedRetrievalCandidate CANDIDATE =
            new FusedRetrievalCandidate(1L, 0, 0.9, Set.of(RetrievalSource.REDIS_VECTOR), 1);

    private static final long RETRIEVAL_LATENCY_MS = 50;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        cache = new RetrievalHitsCache(redisTemplate, new ObjectMapper(), meterRegistry);
        singleFlight = new RetrievalSingleFlight(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Scenario A: baseline — 每请求独立执行 retrieval，无缓存，无 single-flight
    // -----------------------------------------------------------------------
    @Test
    void benchmark_baseline_100IndependentRetrievals() throws InterruptedException {
        int concurrency = 100;
        AtomicInteger callCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());

        long wallStart = System.currentTimeMillis();
        runConcurrent(concurrency, () -> {
            long t0 = System.currentTimeMillis();
            simulateRetrieval(callCount);
            latenciesMs.add(System.currentTimeMillis() - t0);
        });
        long wallMs = System.currentTimeMillis() - wallStart;

        LatencyStats stats = LatencyStats.of(latenciesMs);
        double qps = concurrency * 1000.0 / Math.max(wallMs, 1);
        System.out.printf(
                "[Baseline]   calls=%d  wall=%dms  QPS=%.1f  p50=%dms  p95=%dms  p99=%dms%n",
                callCount.get(), wallMs, qps, stats.p50, stats.p95, stats.p99);

        assertThat(callCount.get()).isEqualTo(concurrency);
    }

    // -----------------------------------------------------------------------
    // Scenario B: warm cache — cache.get() 命中，retrieval 调用次数 = 0
    // -----------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void benchmark_warmCache_noRetrievalCalls() throws InterruptedException {
        int concurrency = 100;
        String key = "retrieval:v1:global:warmquery";
        AtomicInteger callCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());

        String json = """
                [{"documentId":1,"chunkIndex":0,"fusionScore":0.9,\
                "sources":["REDIS_VECTOR"],"documentVersion":1}]""";
        RedisTemplate<String, String> rt = mock(RedisTemplate.class);
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        when(rt.opsForValue()).thenReturn(vo);
        when(vo.get(key)).thenReturn(json);
        RetrievalHitsCache warmCache = new RetrievalHitsCache(rt, new ObjectMapper(), meterRegistry);

        long wallStart = System.currentTimeMillis();
        runConcurrent(concurrency, () -> {
            long t0 = System.currentTimeMillis();
            Optional<List<FusedRetrievalCandidate>> hit = warmCache.get(key);
            if (hit.isEmpty()) {
                simulateRetrieval(callCount);
            }
            latenciesMs.add(System.currentTimeMillis() - t0);
        });
        long wallMs = System.currentTimeMillis() - wallStart;

        LatencyStats stats = LatencyStats.of(latenciesMs);
        double qps = concurrency * 1000.0 / Math.max(wallMs, 1);
        System.out.printf(
                "[Warm Cache] calls=%d  wall=%dms  QPS=%.1f  p50=%dms  p95=%dms  p99=%dms%n",
                callCount.get(), wallMs, qps, stats.p50, stats.p95, stats.p99);

        assertThat(callCount.get()).isEqualTo(0);
        assertThat(meterRegistry.counter("retrieval.cache.hit").count()).isEqualTo(concurrency);
    }

    // -----------------------------------------------------------------------
    // Scenario C: cold + single-flight — 100 并发同 query，retrieval 只调用 1 次
    // -----------------------------------------------------------------------
    @Test
    void benchmark_coldSingleFlight_100Concurrent_retrievalCalledOnce() throws InterruptedException {
        int concurrency = 100;
        String key = "retrieval:v1:global:coldquery";
        AtomicInteger callCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());

        long wallStart = System.currentTimeMillis();
        runConcurrent(concurrency, () -> {
            long t0 = System.currentTimeMillis();
            singleFlight.execute(key, () -> {
                simulateRetrieval(callCount);
                return List.of(CANDIDATE);
            });
            latenciesMs.add(System.currentTimeMillis() - t0);
        });
        long wallMs = System.currentTimeMillis() - wallStart;

        LatencyStats stats = LatencyStats.of(latenciesMs);
        double qps = concurrency * 1000.0 / Math.max(wallMs, 1);
        System.out.printf(
                "[Cold+SF]    calls=%d  wall=%dms  QPS=%.1f  p50=%dms  p95=%dms  p99=%dms  leader=%.0f  follower=%.0f%n",
                callCount.get(), wallMs, qps, stats.p50, stats.p95, stats.p99,
                meterRegistry.counter("retrieval.singleflight.leader").count(),
                meterRegistry.counter("retrieval.singleflight.follower").count());

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(meterRegistry.counter("retrieval.singleflight.leader").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("retrieval.singleflight.follower").count()).isEqualTo(concurrency - 1);
        // wall ≈ single retrieval latency，远小于 baseline（100 * RETRIEVAL_LATENCY_MS）
        assertThat(wallMs).isLessThan(RETRIEVAL_LATENCY_MS * 3);
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private void simulateRetrieval(AtomicInteger counter) {
        counter.incrementAndGet();
        try { Thread.sleep(RETRIEVAL_LATENCY_MS); } catch (InterruptedException ignored) {}
    }

    private void runConcurrent(int n, Runnable task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
    }

    private record LatencyStats(long p50, long p95, long p99) {
        static LatencyStats of(List<Long> ms) {
            List<Long> sorted = new ArrayList<>(ms);
            Collections.sort(sorted);
            int n = sorted.size();
            if (n == 0) return new LatencyStats(0, 0, 0);
            return new LatencyStats(
                    sorted.get((int) (n * 0.50)),
                    sorted.get(Math.min((int) Math.ceil(n * 0.95) - 1, n - 1)),
                    sorted.get(Math.min((int) Math.ceil(n * 0.99) - 1, n - 1))
            );
        }
    }
}
