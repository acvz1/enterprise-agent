package com.kb.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalHit;

import java.io.IOException;
import java.util.List;

//混合检索
@Service
public class HybridRetrievalService {
    private final VectorSearchService vectorSearchService;
    private final ElasticsearchSearchService elasticsearchSearchService;
    private final RrfFusionService rrfFusionService;
    private final RetrievalResultService retrievalResultService;
    private final DepartmentAccessService departmentAccessService;

    /** Redis 相似度阈值由离线评测得到，并可通过 MIN_VECTOR_SCORE 覆盖。 */
    @Value("${app.retrieval.min-vector-score:0.72}")
    private double minVectorScore = 0.72;

    public HybridRetrievalService(VectorSearchService vectorSearchService,
            ElasticsearchSearchService elasticsearchSearchService, RrfFusionService rrfFusionService,
            RetrievalResultService retrievalResultService, DepartmentAccessService departmentAccessService) {
        this.vectorSearchService = vectorSearchService;
        this.elasticsearchSearchService = elasticsearchSearchService;
        this.rrfFusionService = rrfFusionService;
        this.retrievalResultService=retrievalResultService;
        this.departmentAccessService = departmentAccessService;
    }

    /**
     * 依次执行 Redis 向量检索与 Elasticsearch BM25 检索，
     * 再通过 RRF 融合并返回 Top K 候选。
     *
     * @param query 用户查询文本
     * @param candidateLimit 每路检索最多召回的候选数量
     * @param minVectorScore Redis 向量检索的最低相似度
     * @param topK RRF 融合后保留的候选数量
     * @return 按融合分数降序排列的候选列表
     * @throws IOException Elasticsearch 查询失败时抛出
     */
    public List<FusedRetrievalCandidate> search(String query,int candidateLimit,double minVectorScore,int topK)throws IOException{
        DepartmentAccessService.AccessScope scope = departmentAccessService.currentScope();
        java.util.Set<Long> allowedDocumentIds = scope.global() ? null : departmentAccessService.readableDocumentIds(scope);
        List<RetrievalCandidate>redisCandidates=vectorSearchService.searchVectorCandidates(query, candidateLimit, minVectorScore, allowedDocumentIds);
        List<RetrievalCandidate>elasticsearchCandidates=elasticsearchSearchService.searchBm25Candidates(query, candidateLimit, allowedDocumentIds);
        return rrfFusionService.fuse(redisCandidates, elasticsearchCandidates, topK);
    }

    /** 使用当前环境配置的 Redis 向量阈值执行混合候选召回。 */
    public List<FusedRetrievalCandidate> search(String query, int candidateLimit, int topK) throws IOException {
        return search(query, candidateLimit, minVectorScore, topK);
    }

    /**
     * 执行混合检索，并从 MySQL 批量补全候选对应的权威文档数据。
     *
     * @param query 用户查询文本
     * @param candidateLimit 每路检索最多召回的候选数量
     * @param minVectorScore Redis 向量检索的最低相似度
     * @param topK RRF 融合后保留并补全的结果数量
     * @return 包含文档标题、分块原文、融合分数和命中来源的最终检索结果
     * @throws IOException Elasticsearch 查询失败时抛出
     */
    public List<RetrievalHit> searchHits(String query,int candidateLimit,double minVectorScore,int topK)throws IOException{
        DepartmentAccessService.AccessScope scope = departmentAccessService.currentScope();
        java.util.Set<Long> allowedDocumentIds = scope.global() ? null : departmentAccessService.readableDocumentIds(scope);
        List<RetrievalCandidate> redisCandidates = vectorSearchService.searchVectorCandidates(query, candidateLimit, minVectorScore, allowedDocumentIds);
        List<RetrievalCandidate> elasticsearchCandidates = elasticsearchSearchService.searchBm25Candidates(query, candidateLimit, allowedDocumentIds);
        List<FusedRetrievalCandidate> candidates = rrfFusionService.fuse(redisCandidates, elasticsearchCandidates, topK);
        List<RetrievalHit>hits=retrievalResultService.assembleHits(candidates, scope);
        return hits;
    }

    /** 使用当前环境配置的 Redis 向量阈值执行混合检索并补全证据。 */
    public List<RetrievalHit> searchHits(String query, int candidateLimit, int topK) throws IOException {
        return searchHits(query, candidateLimit, minVectorScore, topK);
    }

}
