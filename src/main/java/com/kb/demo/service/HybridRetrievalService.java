package com.kb.demo.service;

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

    public HybridRetrievalService(VectorSearchService vectorSearchService,
            ElasticsearchSearchService elasticsearchSearchService, RrfFusionService rrfFusionService,RetrievalResultService retrievalResultService) {
        this.vectorSearchService = vectorSearchService;
        this.elasticsearchSearchService = elasticsearchSearchService;
        this.rrfFusionService = rrfFusionService;
        this.retrievalResultService=retrievalResultService;
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
        List<RetrievalCandidate>redisCandidates=vectorSearchService.searchVectorCandidates(query, candidateLimit, minVectorScore);
        List<RetrievalCandidate>elasticsearchCandidates=elasticsearchSearchService.searchBm25Candidates(query, candidateLimit);
        return rrfFusionService.fuse(redisCandidates, elasticsearchCandidates, topK);
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
        List<FusedRetrievalCandidate>candidates=search(query,candidateLimit,minVectorScore,topK);
        List<RetrievalHit>hits=retrievalResultService.assembleHits(candidates);
        return hits;
    }

}
