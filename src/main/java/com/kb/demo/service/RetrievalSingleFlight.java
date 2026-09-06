package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Single-flight 保护：对相同 cacheKey 的并发冷 miss 请求，只让一个 leader 执行
 * Vector+BM25+RRF，其余 follower 等待并共享结果。
 *
 * leader 异常时，CompletableFuture 以 completeExceptionally 完成，所有 follower 的 join()
 * 也抛出相同异常（不自行降级，避免雪崩）。key 在 finally 块移除，下一批请求重新竞争 leader。
 */
@Component
public class RetrievalSingleFlight {

    private static final Logger log = LoggerFactory.getLogger(RetrievalSingleFlight.class);

    private final ConcurrentHashMap<String, CompletableFuture<List<FusedRetrievalCandidate>>> inFlight =
            new ConcurrentHashMap<>();

    private final Counter leaderCounter;
    private final Counter followerCounter;

    public RetrievalSingleFlight(MeterRegistry meterRegistry) {
        this.leaderCounter = Counter.builder("retrieval.singleflight.leader")
                .description("Single-flight leader executions")
                .register(meterRegistry);
        this.followerCounter = Counter.builder("retrieval.singleflight.follower")
                .description("Single-flight follower wait count")
                .register(meterRegistry);
        Gauge.builder("retrieval.singleflight.inflight", inFlight, ConcurrentHashMap::size)
                .description("Current in-flight single-flight keys")
                .register(meterRegistry);
    }

    /**
     * 对 cacheKey 执行 single-flight。
     *
     * @param cacheKey   用于去重的键（与 RetrievalHitsCache 使用相同的键）
     * @param retrieval  冷 miss 时执行的 Vector+BM25+RRF 逻辑
     * @return RRF 融合候选列表
     */
    public List<FusedRetrievalCandidate> execute(
            String cacheKey,
            Supplier<List<FusedRetrievalCandidate>> retrieval) {

        CompletableFuture<List<FusedRetrievalCandidate>> newFuture = new CompletableFuture<>();
        CompletableFuture<List<FusedRetrievalCandidate>> existing = inFlight.putIfAbsent(cacheKey, newFuture);

        if (existing != null) {
            // follower — 等待 leader 完成；leader 失败则抛出相同异常，不自行降级
            followerCounter.increment();
            log.debug("single-flight follower waiting key={}", cacheKey);
            return existing.join();
        }

        // leader — 执行实际检索
        leaderCounter.increment();
        log.debug("single-flight leader executing key={}", cacheKey);
        try {
            List<FusedRetrievalCandidate> result = retrieval.get();
            newFuture.complete(result);
            return result;
        } catch (Exception e) {
            newFuture.completeExceptionally(e);
            throw e;
        } finally {
            // 仅当 inFlight 中的 Future 还是自己发布的那个时才移除，防止并发覆盖
            inFlight.remove(cacheKey, newFuture);
        }
    }
}
