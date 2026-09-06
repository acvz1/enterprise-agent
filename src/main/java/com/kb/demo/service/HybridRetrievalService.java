package com.kb.demo.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalHit;

import java.io.IOException;
import java.util.List;
import java.util.Set;

//混合检索
@Service
public class HybridRetrievalService {
    private final VectorSearchService vectorSearchService;
    private final ElasticsearchSearchService elasticsearchSearchService;
    private final RrfFusionService rrfFusionService;
    private final RetrievalResultService retrievalResultService;
    private final DepartmentAccessService departmentAccessService;
    private final RetrievalHitsCache retrievalHitsCache;
    private final RetrievalSingleFlight singleFlight;
    private final RetrievalGenerationService retrievalGenerationService;
    private final Counter vectorSearchCallCounter;
    private final Counter bm25SearchCallCounter;

    /** Redis 相似度阈值由离线评测得到，并可通过 MIN_VECTOR_SCORE 覆盖。 */
    @Value("${app.retrieval.min-vector-score:0.72}")
    private double minVectorScore = 0.72;

    public HybridRetrievalService(VectorSearchService vectorSearchService,
            ElasticsearchSearchService elasticsearchSearchService, RrfFusionService rrfFusionService,
            RetrievalResultService retrievalResultService, DepartmentAccessService departmentAccessService,
            RetrievalHitsCache retrievalHitsCache, RetrievalSingleFlight singleFlight,
            RetrievalGenerationService retrievalGenerationService, MeterRegistry meterRegistry) {
        this.vectorSearchService = vectorSearchService;
        this.elasticsearchSearchService = elasticsearchSearchService;
        this.rrfFusionService = rrfFusionService;
        this.retrievalResultService = retrievalResultService;
        this.departmentAccessService = departmentAccessService;
        this.retrievalHitsCache = retrievalHitsCache;
        this.singleFlight = singleFlight;
        this.retrievalGenerationService = retrievalGenerationService;
        this.vectorSearchCallCounter = Counter.builder("vector.search.calls")
                .description("Vector search invocation count")
                .register(meterRegistry);
        this.bm25SearchCallCounter = Counter.builder("bm25.search.calls")
                .description("BM25 search invocation count")
                .register(meterRegistry);
    }

    /**
     * 依次执行 Redis 向量检索与 Elasticsearch BM25 检索，
     * 再通过 RRF 融合并返回 Top K 候选。不经过 Retrieval Hits Cache。
     */
    public List<FusedRetrievalCandidate> search(String query, int candidateLimit, double minVectorScore, int topK) throws IOException {
        DepartmentAccessService.AccessScope scope = departmentAccessService.currentScope();
        Set<Long> allowedDocumentIds = scope.global() ? null : departmentAccessService.readableDocumentIds(scope);
        return doRetrieve(query, candidateLimit, minVectorScore, topK, allowedDocumentIds);
    }

    /** 使用当前环境配置的 Redis 向量阈值执行混合候选召回。 */
    public List<FusedRetrievalCandidate> search(String query, int candidateLimit, int topK) throws IOException {
        return search(query, candidateLimit, minVectorScore, topK);
    }

    /**
     * 执行混合检索，并从 MySQL 批量补全候选对应的权威文档数据。
     * 在 RRF 融合后、MySQL hydration 前插入 Retrieval Hits Cache + Single-flight。
     *
     * Cache HIT  → 跳过 Vector+BM25+RRF，直接进入 MySQL hydration + 部门权限二次校验。
     * Cache MISS → Single-flight 保护，leader 执行检索后写缓存，follower 共享结果。
     */
    public List<RetrievalHit> searchHits(String query, int candidateLimit, double minVectorScore, int topK) throws IOException {
        DepartmentAccessService.AccessScope scope = departmentAccessService.currentScope();
        Set<Long> allowedDocumentIds = scope.global() ? null : departmentAccessService.readableDocumentIds(scope);

        String scopeKey = departmentAccessService.currentScopeCacheKey();
        String generation = retrievalGenerationService.currentGeneration();
        String cacheKey = RetrievalHitsCache.buildKey(generation, scopeKey, query.hashCode());

        // Cache HIT — 跳过 Vector+BM25+RRF
        var cached = retrievalHitsCache.get(cacheKey);
        if (cached.isPresent()) {
            return retrievalResultService.assembleHits(cached.get(), scope);
        }

        // Cache MISS — Single-flight 保护冷 miss 并发
        final Set<Long> finalAllowedDocumentIds = allowedDocumentIds;
        final double effectiveMinScore = minVectorScore;
        List<FusedRetrievalCandidate> candidates = singleFlight.execute(cacheKey, () -> {
            // leader 双重检查
            var doubleCheck = retrievalHitsCache.get(cacheKey);
            if (doubleCheck.isPresent()) {
                return doubleCheck.get();
            }
            try {
                List<FusedRetrievalCandidate> result = doRetrieve(
                        query, candidateLimit, effectiveMinScore, topK, finalAllowedDocumentIds);
                retrievalHitsCache.put(cacheKey, result);
                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return retrievalResultService.assembleHits(candidates, scope);
    }

    /** 使用当前环境配置的 Redis 向量阈值执行混合检索并补全证据。 */
    public List<RetrievalHit> searchHits(String query, int candidateLimit, int topK) throws IOException {
        return searchHits(query, candidateLimit, minVectorScore, topK);
    }

    private List<FusedRetrievalCandidate> doRetrieve(
            String query, int candidateLimit, double minScore, int topK,
            Set<Long> allowedDocumentIds) throws IOException {
        vectorSearchCallCounter.increment();
        bm25SearchCallCounter.increment();
        List<RetrievalCandidate> redisCandidates =
                vectorSearchService.searchVectorCandidates(query, candidateLimit, minScore, allowedDocumentIds);
        List<RetrievalCandidate> elasticsearchCandidates =
                elasticsearchSearchService.searchBm25Candidates(query, candidateLimit, allowedDocumentIds);
        return rrfFusionService.fuse(redisCandidates, elasticsearchCandidates, topK);
    }
}
