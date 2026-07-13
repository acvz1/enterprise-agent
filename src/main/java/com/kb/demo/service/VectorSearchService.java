package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量检索服务类
 * 提供基于向量的文档检索功能
 * 使用基本配置避免KNN查询的k参数问题
 * @author LiJingLin
 */
@Service
public class VectorSearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    
    @Autowired
    private MetricsService metricsService;
    
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    
    /**
     * 默认检索结果数量
     */
    private static final int DEFAULT_SEARCH_RESULTS = 10;  // 从 5 增加到 10
    
    /**
     * 最小相似度阈值
     */
    private static final double MIN_SIMILARITY_THRESHOLD = 0.5;  // 从 0.7 降低到 0.5
    
    /**
     * AllMiniLmL6V2EmbeddingModel 的向量维度
     */
    private static final int EMBEDDING_DIMENSION = 384;

    /** Reciprocal Rank Fusion 的平滑常量，避免头部名次分数差距过大。 */
    private static final int RRF_K = 60;
    
    /**
     * 向量检索文档
     * 使用基本配置避免KNN查询的k参数问题
     * @param query 查询文本
     * @param maxResults 最大结果数量
     * @param minScore 最小相似度阈值
     * @return 匹配的文档列表
     */
    public List<Document> searchDocuments(String query, int maxResults, double minScore) {
        logger.info("✅ [向量检索] 开始向量检索，查询: {}, 最大结果数: {}, 最小相似度: {}", query, maxResults, minScore);
        logger.debug("Redis地址: {}:{}", redisHost, redisPort);
        
        // 记录向量检索请求
        metricsService.recordVectorSearch();
        var timer = metricsService.startVectorSearchTimer();
        
        try {
            // 创建嵌入模型
            EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            logger.debug("嵌入模型创建成功");
            
            // 创廪Redis向量存储，使用配置文件中的Redis地址
            logger.info("正在连接Redis Stack向量存储: {}:{}", redisHost, redisPort);
            EmbeddingStore<TextSegment> embeddingStore = RedisEmbeddingStore.builder()
                    .host(redisHost)
                    .port(redisPort)
                    .dimension(EMBEDDING_DIMENSION)
                    .indexName("document-embeddings")
                    .build();
            logger.info("Redis Stack向量存储创建成功");
            
            // 将查询文本转换为向量
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            logger.debug("查询文本向量化完成，维度: {}", queryEmbedding.dimension());
            
            // 创建检索请求，确保设置maxResults参数
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
            logger.debug("检索请求创建完成，maxResults: {}, minScore: {}", maxResults, minScore);
            
            // 执行检索
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
            logger.info("✅ [向量检索] Redis向量检索执行完成，结果数量: {}", result.matches().size());
            
            // 处理检索结果 - 收集文档ID而不是实体，避免懒加载问题
            List<Long> matchedDocumentIds = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                logger.debug("处理匹配结果，得分: {}", match.score());
                TextSegment segment = match.embedded();
                String segmentText = segment.text();
                
                // 从文档块中查找对应的文档ID
                List<DocumentChunk> chunks = documentChunkRepository.findByContentContaining(segmentText);
                if (!chunks.isEmpty()) {
                    DocumentChunk chunk = chunks.get(0);
                    Long docId = chunk.getDocument().getId(); // 只获取ID，不访问其他属性
                    if (!matchedDocumentIds.contains(docId)) {
                        matchedDocumentIds.add(docId);
                        logger.debug("添加匹配文档ID: {}", docId);
                    }
                }
            }
            
            // 在事务内重新加载完整的Document实体
            List<Document> matchedDocuments = new ArrayList<>();
            for (Long docId : matchedDocumentIds) {
                Document doc = documentRepository.findById(docId).orElse(null);
                if (doc != null) {
                    matchedDocuments.add(doc);
                }
            }
            
            logger.info("✅ [向量检索] 向量检索完成，找到 {} 个匹配文档", matchedDocuments.size());
            
            // 记录向量检索时间
            metricsService.recordVectorSearchTime(timer);
            
            return matchedDocuments;
        } catch (redis.clients.jedis.exceptions.JedisDataException e) {
            // Redis不支持RediSearch命令
            logger.error("❌ [向量检索] 检测到Redis不支持RediSearch命令，请使用Redis Stack而非普通Redis!", e);
            logger.warn("⚠️ [关键词检索] 因Redis不支持向量检索，回退到关键词检索模式");
            return keywordSearch(query, maxResults);
        } catch (Exception e) {
            logger.error("❌ [向量检索] 向量检索过程中发生错误", e);
            // 如果检索失败，回退到关键词检索
            logger.warn("⚠️ [关键词检索] 回退到关键词检索模式");
            return keywordSearch(query, maxResults);
        }
    }
    
    /**
     * 回退到关键词检索
     * @param query 查询文本
     * @param maxResults 最大结果数量
     * @return 匹配的文档列表
     */
    List<Document> keywordSearch(String query, int maxResults) {
        logger.info("🔍 [关键词检索] 执行关键词检索，查询: {}, 最大结果数: {}", query, maxResults);
        try {
            List<Document> results = documentRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(query, query);
            if (results.size() > maxResults) {
                results = results.subList(0, maxResults);
            }
            logger.info("🔍 [关键词检索] 关键词检索完成，找到 {} 个匹配文档", results.size());
            return results;
        } catch (Exception e) {
            logger.error("❌ [关键词检索] 关键词检索也失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 向量检索文档（使用默认参数）
     * @param query 查询文本
     * @return 匹配的文档列表
     */
    public List<Document> searchDocuments(String query) {
        return searchDocuments(query, DEFAULT_SEARCH_RESULTS, MIN_SIMILARITY_THRESHOLD);
    }
    
    /**
     * 向量检索文档（分页）
     * @param query 查询文本
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    public Page<Document> searchDocuments(String query, Pageable pageable) {
        logger.info("开始分页向量检索，查询: {}", query);
        
        // 获取所有匹配的文档
        List<Document> allMatchedDocuments = searchDocuments(query, 
                pageable.getPageSize() * (pageable.getPageNumber() + 1), 
                MIN_SIMILARITY_THRESHOLD);
        
        // 计算分页
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allMatchedDocuments.size());
        
        List<Document> pageContent = allMatchedDocuments.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, allMatchedDocuments.size());
    }
    
    /**
     * 混合检索：结合向量检索和关键词检索
     * @param query 查询文本
     * @param maxResults 最大结果数量
     * @param vectorWeight 向量检索权重（0-1）
     * @param keywordWeight 关键词检索权重（0-1）
     * @return 匹配的文档列表
     */
    public List<Document> hybridSearch(String query, int maxResults, double vectorWeight, double keywordWeight) {
        logger.info("开始混合检索，查询: {}, 向量权重: {}, 关键词权重: {}", query, vectorWeight, keywordWeight);

        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults 必须大于 0");
        }
        if (vectorWeight < 0 || vectorWeight > 1 || keywordWeight < 0 || keywordWeight > 1
                || vectorWeight + keywordWeight == 0) {
            throw new IllegalArgumentException("检索权重必须在 0 到 1 之间，且不能同时为 0");
        }
        
        // 向量检索
        List<Document> vectorResults = searchDocuments(query, maxResults, MIN_SIMILARITY_THRESHOLD);
        
        // 关键词检索
        List<Document> keywordResults = documentRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(query, query);
        
        List<Document> combinedResults = fuseRankedResults(
                vectorResults, keywordResults, maxResults, vectorWeight, keywordWeight);
        
        logger.info("混合检索完成，找到 {} 个匹配文档", combinedResults.size());
        return combinedResults;
    }

    /**
     * 用加权 Reciprocal Rank Fusion 合并两个有序结果集。
     * 当前向量存储适配层没有把原始相似度传到领域对象，因此使用排名融合比伪造统一分数更诚实。
     */
    List<Document> fuseRankedResults(
            List<Document> vectorResults,
            List<Document> keywordResults,
            int maxResults,
            double vectorWeight,
            double keywordWeight) {
        Map<Long, RankedDocument> scoresByDocumentId = new HashMap<>();
        addRankScores(scoresByDocumentId, vectorResults, vectorWeight);
        addRankScores(scoresByDocumentId, keywordResults, keywordWeight);

        return scoresByDocumentId.values().stream()
                .sorted(Comparator.comparingDouble(RankedDocument::score).reversed()
                        .thenComparing(item -> item.document().getId()))
                .limit(maxResults)
                .map(RankedDocument::document)
                .toList();
    }

    private void addRankScores(
            Map<Long, RankedDocument> scoresByDocumentId,
            List<Document> rankedDocuments,
            double weight) {
        for (int index = 0; index < rankedDocuments.size(); index++) {
            Document document = rankedDocuments.get(index);
            if (document.getId() == null) {
                continue;
            }

            double rankScore = weight / (RRF_K + index + 1.0);
            scoresByDocumentId.compute(document.getId(), (documentId, current) ->
                    new RankedDocument(document, rankScore + (current == null ? 0 : current.score())));
        }
    }

    private record RankedDocument(Document document, double score) {
    }
    
    /**
     * 混合检索（使用默认参数）
     * @param query 查询文本
     * @return 匹配的文档列表
     */
    public List<Document> hybridSearch(String query) {
        return hybridSearch(query, DEFAULT_SEARCH_RESULTS, 0.6, 0.4);
    }
    
    /**
     * 获取文档的相关段落
     * @param documentId 文档ID
     * @param query 查询文本
     * @param maxSegments 最大段落数量
     * @return 相关段落列表
     */
    public List<String> getRelevantSegments(Long documentId, String query, int maxSegments) {
        logger.info("获取文档相关段落，文档ID: {}, 查询: {}", documentId, query);
        
        // 获取文档的所有块
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.isEmpty()) {
            logger.warn("文档未分块处理，文档ID: {}", documentId);
            return new ArrayList<>();
        }
        
        // 创建嵌入模型
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        
        // 将查询文本转换为向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        
        // 计算每个块与查询的相似度
        List<SegmentScore> segmentScores = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Embedding chunkEmbedding = embeddingModel.embed(chunk.getContent()).content();
            double similarity = cosineSimilarity(queryEmbedding.vectorAsList(), chunkEmbedding.vectorAsList());
            segmentScores.add(new SegmentScore(chunk.getContent(), similarity));
        }
        
        // 按相似度排序并取前maxSegments个
        return segmentScores.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(maxSegments)
                .map(s -> s.segment)
                .collect(Collectors.toList());
    }
    
    /**
     * 计算余弦相似度
     * @param vector1 向量1
     * @param vector2 向量2
     * @return 相似度
     */
    private double cosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += Math.pow(vector1.get(i), 2);
            norm2 += Math.pow(vector2.get(i), 2);
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 内部类：段落和得分
     */
    private static class SegmentScore {
        String segment;
        double score;
        
        SegmentScore(String segment, double score) {
            this.segment = segment;
            this.score = score;
        }
    }
}
