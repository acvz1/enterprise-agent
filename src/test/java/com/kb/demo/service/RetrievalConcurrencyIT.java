package com.kb.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.dto.RetrievalSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 并发对照基准测试（真实 Redis Vector + Elasticsearch BM25）：
 *
 * Baseline：100 并发直接调用 search()，绕过 Hits Cache 和 Single-flight，
 *           每请求独立执行 Vector + BM25，统计真实调用次数与延迟。
 *
 * After   ：100 并发相同 query 调用 searchHits()，启用 Hits Cache + Single-flight，
 *           验证 cold miss 下 Vector/BM25 调用次数降至 1。
 *
 * 每组先 warm-up 2 次，再正式跑 5 次，取中位数。
 * 不使用 Thread.sleep 模拟延迟，延迟完全来自真实网络 I/O。
 */
class RetrievalConcurrencyIT {

    private static final int CONCURRENCY = 100;
    private static final int WARMUP_ROUNDS = 2;
    private static final int MEASURE_ROUNDS = 5;
    /** 6379 在 Windows Hyper-V 环境被系统保留（WSAEACCES），故本地测试走 16379。 */
    private static final int REDIS_PORT =
            Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "16379"));
    private static final String QUERY = "concurrency-benchmark-probe 并发检索基准测试";
    private static final long TEST_DOC_ID = 940001L;
    private static final int TEST_CHUNK_INDEX = 0;

    // 基础设施
    private RestClient restClient;
    private RestClientTransport transport;
    private ElasticsearchSearchService esService;
    private JedisPooled jedisCleanup;
    private LettuceConnectionFactory lettuceFactory;
    private RedisTemplate<String, String> redisTemplate;
    private StringRedisTemplate stringRedisTemplate;
    private EmbeddingModel embeddingModel;

    // 两组服务实例，共享同一 meterRegistry
    private SimpleMeterRegistry meterRegistry;
    /** Baseline：无 cache 无 single-flight，直接调 search() */
    private HybridRetrievalService baseline;
    /** After：启用 Hits Cache + Single-flight，调 searchHits() */
    private HybridRetrievalService after;
    private RetrievalHitsCache hitsCache;

    // 预热线程池（所有组复用）
    private ExecutorService pool;

    // -----------------------------------------------------------------------
    // 基础设施初始化
    // -----------------------------------------------------------------------

    private int esPort() {
        return Integer.parseInt(System.getenv().getOrDefault("ELASTICSEARCH_PORT", "9200"));
    }

    @BeforeEach
    void setUp() throws IOException {
        embeddingModel = new BgeSmallZhV15EmbeddingModel();

        // ES 客户端
        restClient = RestClient.builder(new HttpHost("localhost", esPort(), "http")).build();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        esService = new ElasticsearchSearchService(new ElasticsearchClient(transport));

        // Redis Jedis（清理用）
        jedisCleanup = new JedisPooled("localhost", REDIS_PORT);

        // Lettuce RedisTemplate（Hits Cache 用）
        lettuceFactory = new LettuceConnectionFactory("localhost", REDIS_PORT);
        lettuceFactory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(lettuceFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();

        // StringRedisTemplate（RetrievalGenerationService 用）
        stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(lettuceFactory);
        stringRedisTemplate.afterPropertiesSet();

        meterRegistry = new SimpleMeterRegistry();

        // 写入测试 corpus
        writeTestCorpus();

        // 构建两组服务
        baseline = buildService(false);
        after = buildService(true);

        hitsCache = new RetrievalHitsCache(redisTemplate, new ObjectMapper(), meterRegistry);

        // 预热线程池
        pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        for (int i = 0; i < CONCURRENCY; i++) pool.submit(ready::countDown);
        try { ready.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @AfterEach
    void tearDown() throws IOException {
        pool.shutdownNow();
        cleanTestCorpus();
        if (jedisCleanup != null) jedisCleanup.close();
        if (transport != null) transport.close();
        if (restClient != null) restClient.close();
        if (lettuceFactory != null) lettuceFactory.destroy();
    }

    // -----------------------------------------------------------------------
    // 主测试
    // -----------------------------------------------------------------------

    @Test
    void concurrencyBenchmark_baselineVsAfter() throws InterruptedException {
        System.out.println("\n====== Retrieval Concurrency Benchmark (concurrency=" + CONCURRENCY + ") ======");

        // ---- Baseline warm-up ----
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            runBaselineRound(new AtomicInteger(), new AtomicInteger(), new ArrayList<>());
        }

        // ---- Baseline 正式 5 次 ----
        List<RoundResult> baselineResults = new ArrayList<>();
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            AtomicInteger vec = new AtomicInteger();
            AtomicInteger bm25 = new AtomicInteger();
            List<Long> latencies = new ArrayList<>();
            long wall = runBaselineRound(vec, bm25, latencies);
            baselineResults.add(new RoundResult(wall, vec.get(), bm25.get(), latencies, 0, 0, 0, 0));
            System.out.printf("  [Baseline #%d] wall=%dms  vec=%d  bm25=%d  p50=%dms  p95=%dms%n",
                    i + 1, wall, vec.get(), bm25.get(),
                    percentile(latencies, 50), percentile(latencies, 95));
        }

        // ---- After warm-up ----
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            evictHitsCache();
            runAfterRound(new AtomicInteger(), new AtomicInteger(), new ArrayList<>(), new long[4]);
        }

        // ---- After 正式 5 次 ----
        List<RoundResult> afterResults = new ArrayList<>();
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            evictHitsCache();
            AtomicInteger vec = new AtomicInteger();
            AtomicInteger bm25 = new AtomicInteger();
            List<Long> latencies = new ArrayList<>();
            long[] sfMetrics = new long[4]; // [hit, miss, leader, follower]
            long wall = runAfterRound(vec, bm25, latencies, sfMetrics);
            afterResults.add(new RoundResult(wall, vec.get(), bm25.get(), latencies,
                    sfMetrics[0], sfMetrics[1], sfMetrics[2], sfMetrics[3]));
            System.out.printf("  [After   #%d] wall=%dms  vec=%d  bm25=%d  p50=%dms  p95=%dms  leader=%d  follower=%d%n",
                    i + 1, wall, vec.get(), bm25.get(),
                    percentile(latencies, 50), percentile(latencies, 95),
                    sfMetrics[2], sfMetrics[3]);
        }

        // ---- 中位数 ----
        RoundResult bMed = median(baselineResults);
        RoundResult aMed = median(afterResults);

        long bQps = CONCURRENCY * 1000L / Math.max(bMed.wallMs, 1);
        long aQps = CONCURRENCY * 1000L / Math.max(aMed.wallMs, 1);
        String qpsChange = delta(bQps, aQps);
        String p95Change = delta(bMed.p95(), aMed.p95());
        String p99Change = delta(bMed.p99(), aMed.p99());

        System.out.println("\n----- Median Results -----");
        System.out.printf("%-20s %10s %10s %10s%n", "Metric", "Baseline", "After", "Change");
        System.out.printf("%-20s %10d %10d %10s%n", "QPS",          bQps, aQps, qpsChange);
        System.out.printf("%-20s %10d %10d %10s%n", "P50 (ms)",     bMed.p50(), aMed.p50(), delta(bMed.p50(), aMed.p50()));
        System.out.printf("%-20s %10d %10d %10s%n", "P95 (ms)",     bMed.p95(), aMed.p95(), p95Change);
        System.out.printf("%-20s %10d %10d %10s%n", "P99 (ms)",     bMed.p99(), aMed.p99(), p99Change);
        System.out.printf("%-20s %10d %10d %10s%n", "Vector calls", bMed.vectorCalls, aMed.vectorCalls,
                bMed.vectorCalls + "→" + aMed.vectorCalls);
        System.out.printf("%-20s %10d %10d %10s%n", "BM25 calls",   bMed.bm25Calls, aMed.bm25Calls,
                bMed.bm25Calls + "→" + aMed.bm25Calls);
        System.out.printf("%-20s %10s %10d%n",       "SF leader",   "-", aMed.sfLeader);
        System.out.printf("%-20s %10s %10d%n",       "SF follower", "-", aMed.sfFollower);
        System.out.printf("%-20s %10s %10d%n",       "Cache hit",   "-", aMed.cacheHit);
        System.out.printf("%-20s %10s %10d%n",       "Cache miss",  "-", aMed.cacheMiss);

        // ---- 断言：After Vector/BM25 调用次数应为 1 ----
        assertThat(aMed.vectorCalls)
                .as("After: Vector calls must collapse to 1 via Single-flight")
                .isEqualTo(1);
        assertThat(aMed.bm25Calls)
                .as("After: BM25 calls must collapse to 1 via Single-flight")
                .isEqualTo(1);
        assertThat(aMed.sfLeader).isEqualTo(1L);
        assertThat(aMed.sfFollower).isEqualTo(CONCURRENCY - 1L);
    }

    // -----------------------------------------------------------------------
    // 单轮 Baseline 跑（直接调 search()，无 cache/single-flight）
    // -----------------------------------------------------------------------

    private long runBaselineRound(AtomicInteger vecCounter, AtomicInteger bm25Counter,
                                  List<Long> latencies) throws InterruptedException {
        // Baseline 专用 meterRegistry（每轮隔离）
        SimpleMeterRegistry localReg = new SimpleMeterRegistry();
        HybridRetrievalService svc = buildServiceWithRegistry(false, localReg);

        CountDownLatch barrier = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);
        List<Long> localLat = Collections.synchronizedList(new ArrayList<>());

        long wallStart = System.currentTimeMillis();
        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(() -> {
                try {
                    barrier.await();
                    long t0 = System.currentTimeMillis();
                    svc.search(QUERY, 10, 0.0, 10);
                    localLat.add(System.currentTimeMillis() - t0);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        barrier.countDown();
        done.await();
        long wallMs = System.currentTimeMillis() - wallStart;

        vecCounter.set((int) localReg.counter("vector.search.calls").count());
        bm25Counter.set((int) localReg.counter("bm25.search.calls").count());
        latencies.addAll(localLat);
        return wallMs;
    }

    // -----------------------------------------------------------------------
    // 单轮 After 跑（调 searchHits()，走 Hits Cache + Single-flight）
    // -----------------------------------------------------------------------

    private long runAfterRound(AtomicInteger vecCounter, AtomicInteger bm25Counter,
                               List<Long> latencies, long[] sfMetrics) throws InterruptedException {
        SimpleMeterRegistry localReg = new SimpleMeterRegistry();
        HybridRetrievalService svc = buildServiceWithRegistry(true, localReg);

        CountDownLatch barrier = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);
        List<Long> localLat = Collections.synchronizedList(new ArrayList<>());

        long wallStart = System.currentTimeMillis();
        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(() -> {
                try {
                    barrier.await();
                    long t0 = System.currentTimeMillis();
                    svc.searchHits(QUERY, 10, 0.0, 10);
                    localLat.add(System.currentTimeMillis() - t0);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        barrier.countDown();
        done.await();
        long wallMs = System.currentTimeMillis() - wallStart;

        vecCounter.set((int) localReg.counter("vector.search.calls").count());
        bm25Counter.set((int) localReg.counter("bm25.search.calls").count());
        latencies.addAll(localLat);
        sfMetrics[0] = (long) localReg.counter("retrieval.cache.hit").count();
        sfMetrics[1] = (long) localReg.counter("retrieval.cache.miss").count();
        sfMetrics[2] = (long) localReg.counter("retrieval.singleflight.leader").count();
        sfMetrics[3] = (long) localReg.counter("retrieval.singleflight.follower").count();
        return wallMs;
    }

    // -----------------------------------------------------------------------
    // 服务构建
    // -----------------------------------------------------------------------

    private HybridRetrievalService buildService(boolean withCacheAndSingleFlight) {
        return buildServiceWithRegistry(withCacheAndSingleFlight, meterRegistry);
    }

    @SuppressWarnings("unchecked")
    private HybridRetrievalService buildServiceWithRegistry(boolean withCacheAndSingleFlight,
                                                             SimpleMeterRegistry reg) {
        VectorSearchService vectorSvc = new VectorSearchService();
        ReflectionTestUtils.setField(vectorSvc, "redisHost", "localhost");
        ReflectionTestUtils.setField(vectorSvc, "redisPort", REDIS_PORT);
        ReflectionTestUtils.setField(vectorSvc, "embeddingModel", embeddingModel);
        ReflectionTestUtils.setField(vectorSvc, "documentRepository",
                mock(com.kb.demo.repository.DocumentRepository.class));
        ReflectionTestUtils.setField(vectorSvc, "documentChunkRepository",
                mock(com.kb.demo.repository.DocumentChunkRepository.class));
        MetricsService metricsSvc = mock(MetricsService.class);
        when(metricsSvc.startVectorSearchTimer()).thenReturn(null);
        ReflectionTestUtils.setField(vectorSvc, "metricsService", metricsSvc);

        DepartmentAccessService deptSvc = mock(DepartmentAccessService.class);
        when(deptSvc.currentScope())
                .thenReturn(new DepartmentAccessService.AccessScope(true, Set.of()));
        when(deptSvc.currentScopeCacheKey()).thenReturn("global");

        RetrievalResultService resultSvc = mock(RetrievalResultService.class);
        when(resultSvc.assembleHits(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        RetrievalGenerationService genSvc = new RetrievalGenerationService(stringRedisTemplate);

        RetrievalHitsCache cache;
        RetrievalSingleFlight singleFlight;
        if (withCacheAndSingleFlight) {
            cache = new RetrievalHitsCache(redisTemplate, new ObjectMapper(), reg);
            singleFlight = new RetrievalSingleFlight(reg);
        } else {
            // Baseline：cache 始终 miss（RedisTemplate mock 返回 null），single-flight 仍实例化但永不命中
            org.springframework.data.redis.core.RedisTemplate<String, String> mockRt =
                    mock(org.springframework.data.redis.core.RedisTemplate.class);
            org.springframework.data.redis.core.ValueOperations<String, String> mockVo =
                    mock(org.springframework.data.redis.core.ValueOperations.class);
            when(mockRt.opsForValue()).thenReturn(mockVo);
            when(mockVo.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
            cache = new RetrievalHitsCache(mockRt, new ObjectMapper(), reg);
            // Baseline 直接调 search()，不走 searchHits()，所以 single-flight 不参与
            singleFlight = new RetrievalSingleFlight(reg);
        }

        return new HybridRetrievalService(
                vectorSvc,
                esService,
                new RrfFusionService(),
                resultSvc,
                deptSvc,
                cache,
                singleFlight,
                genSvc,
                reg
        );
    }

    // -----------------------------------------------------------------------
    // 测试语料写入 / 清理
    // -----------------------------------------------------------------------

    private void writeTestCorpus() throws IOException {
        // 清理旧数据
        cleanTestCorpus();

        // 写 ES
        esService.indexChunk(new ElasticsearchChunkDocument(
                TEST_DOC_ID, TEST_CHUNK_INDEX, QUERY));
        esService.refreshIndex();

        // 写 Redis Vector
        RedisEmbeddingStore store = RedisEmbeddingStore.builder()
                .host("localhost").port(REDIS_PORT)
                .dimension(512)
                .indexName("document-embeddings")
                .metadataKeys(List.of("documentId", "chunkIndex"))
                .build();
        Metadata meta = new Metadata()
                .put("documentId", TEST_DOC_ID)
                .put("chunkIndex", TEST_CHUNK_INDEX);
        TextSegment seg = TextSegment.from(QUERY, meta);
        store.add(embeddingModel.embed(seg.text()).content(), seg);
    }

    private void cleanTestCorpus() throws IOException {
        // 清 ES
        try { esService.deleteByDocumentId(TEST_DOC_ID); esService.refreshIndex(); }
        catch (Exception ignored) {}

        // 清 Redis embedding keys（按 documentId 过滤）
        for (String key : jedisCleanup.keys("embedding:*")) {
            try {
                Map<?, ?> obj = jedisCleanup.jsonGet(key, Map.class);
                if (obj != null && String.valueOf(TEST_DOC_ID).equals(String.valueOf(obj.get("documentId")))) {
                    jedisCleanup.del(key);
                }
            } catch (Exception ignored) {}
        }

        // 清 Hits Cache（pattern 匹配）
        try {
            Set<String> cacheKeys = redisTemplate.keys("retrieval:*");
            if (cacheKeys != null && !cacheKeys.isEmpty()) redisTemplate.delete(cacheKeys);
        } catch (Exception ignored) {}
    }

    private void evictHitsCache() {
        try {
            Set<String> keys = redisTemplate.keys("retrieval:*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // 统计工具
    // -----------------------------------------------------------------------

    private static long percentile(List<Long> data, int pct) {
        if (data.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(data);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(sorted.size() * pct / 100.0) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private static String delta(long base, long after) {
        if (base == 0) return "N/A";
        long diff = after - base;
        double pct = diff * 100.0 / base;
        return String.format("%+.0f%%", pct);
    }

    private static RoundResult median(List<RoundResult> results) {
        List<RoundResult> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> Long.compare(a.wallMs, b.wallMs));
        return sorted.get(sorted.size() / 2);
    }

    // -----------------------------------------------------------------------
    // 数据结构
    // -----------------------------------------------------------------------

    private record RoundResult(
            long wallMs,
            int vectorCalls,
            int bm25Calls,
            List<Long> latencies,
            long cacheHit,
            long cacheMiss,
            long sfLeader,
            long sfFollower
    ) {
        long p50() { return percentile(latencies, 50); }
        long p95() { return percentile(latencies, 95); }
        long p99() { return percentile(latencies, 99); }
    }
}
