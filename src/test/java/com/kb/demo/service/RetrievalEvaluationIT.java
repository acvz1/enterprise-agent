package com.kb.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RetrievalEvaluationIT {
    private static final Set<Long> EVALUATION_DOCUMENT_IDS =
            Set.of(950001L, 950002L, 950003L, 950004L);
    private static final int CANDIDATE_LIMIT = 20;
    private static final int TOP_K = 3;
    private static final double MIN_VECTOR_SCORE = 0.0;

    private ElasticsearchTransport transport;
    private ElasticsearchSearchService elasticsearchSearchService;
    private EmbeddingStore<TextSegment> redisEmbeddingStore;
    private JedisPooled redisCleanupClient;
    private VectorSearchService vectorSearchService;
    private HybridRetrievalService hybridRetrievalService;

    private int elasticsearchPort() {
        return Integer.parseInt(
                System.getenv().getOrDefault("ELASTICSEARCH_PORT", "9200"));
    }

    @BeforeEach
    void setUp() throws IOException {
        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", elasticsearchPort(), "http")
        ).build();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        elasticsearchSearchService =
                new ElasticsearchSearchService(new ElasticsearchClient(transport));

        redisCleanupClient = new JedisPooled("localhost", 6379);
        redisEmbeddingStore = RedisEmbeddingStore.builder()
                .host("localhost")
                .port(6379)
                .dimension(384)
                .indexName("document-embeddings")
                .metadataKeys(List.of("documentId", "chunkIndex"))
                .build();

        vectorSearchService = new VectorSearchService();
        ReflectionTestUtils.setField(vectorSearchService, "redisHost", "localhost");
        ReflectionTestUtils.setField(vectorSearchService, "redisPort", 6379);
        DepartmentAccessService departmentAccessService = mock(DepartmentAccessService.class);
        when(departmentAccessService.currentScope())
                .thenReturn(new DepartmentAccessService.AccessScope(true, Set.of()));
        hybridRetrievalService = new HybridRetrievalService(
                vectorSearchService,
                elasticsearchSearchService,
                new RrfFusionService(),
                mock(RetrievalResultService.class),
                departmentAccessService
        );

        elasticsearchSearchService.createIndexIfAbsent();
        deleteEvaluationData();
        indexEvaluationData();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            if (redisCleanupClient != null && elasticsearchSearchService != null) {
                deleteEvaluationData();
            }
        } finally {
            if (redisCleanupClient != null) {
                redisCleanupClient.close();
            }
            if (transport != null) {
                transport.close();
            }
        }
    }

    private static class EvaluationCase{
        private final String query;
        private final Set<String> relevantChunkKeys;

        private EvaluationCase(String query, Set<String> relevantChunkKeys) {
            this.query = query;
            this.relevantChunkKeys = relevantChunkKeys;
        }

        public String getQuery() {
            return query;
        }

        public Set<String> getRelevantChunkKeys() {
            return relevantChunkKeys;
        }

    }

    private static class EvaluationChunk {
        private final Long documentId;
        private final Integer chunkIndex;
        private final String content;

        private EvaluationChunk(Long documentId, Integer chunkIndex, String content) {
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.content = content;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public String getContent() {
            return content;
        }
    }

    @FunctionalInterface
    private interface SearchOperation {
        List<String> search(String query) throws IOException;
    }

    private static class EvaluationSummary {
        private final String strategy;
        private final double hitAtK;
        private final double recallAtK;
        private final double averageLatencyMs;
        private final double p95LatencyMs;

        private EvaluationSummary(
                String strategy,
                double hitAtK,
                double recallAtK,
                double averageLatencyMs,
                double p95LatencyMs) {
            this.strategy = strategy;
            this.hitAtK = hitAtK;
            this.recallAtK = recallAtK;
            this.averageLatencyMs = averageLatencyMs;
            this.p95LatencyMs = p95LatencyMs;
        }

        public String getStrategy() {
            return strategy;
        }

        public double getHitAtK() {
            return hitAtK;
        }

        public double getRecallAtK() {
            return recallAtK;
        }

        public double getAverageLatencyMs() {
            return averageLatencyMs;
        }

        public double getP95LatencyMs() {
            return p95LatencyMs;
        }
    }

    private List<EvaluationChunk> evaluationChunks() {
        return List.of(
                new EvaluationChunk(950001L, 0,
                        "员工申请年假需至少提前3个工作日在OA系统提交，并由直属主管审批。"),
                new EvaluationChunk(950001L, 1,
                        "病假超过1天需上传医院证明，病假不扣除年假额度。"),
                new EvaluationChunk(950002L, 0,
                        "差旅报销应在行程结束后30天内提交，逾期需部门负责人补充说明。"),
                new EvaluationChunk(950002L, 1,
                        "单笔费用超过5000元时，需要部门负责人和财务负责人两级审批。"),
                new EvaluationChunk(950003L, 0,
                        "生产数据库变更必须提交工单，经过研发负责人和DBA双人审核后方可执行。"),
                new EvaluationChunk(950003L, 1,
                        "紧急变更应先电话通知值班负责人，执行后24小时内补齐工单和复盘记录。"),
                new EvaluationChunk(950004L, 0,
                        "连续5次输入错误密码会锁定企业账号30分钟。"),
                new EvaluationChunk(950004L, 1,
                        "VPN登录失败时先确认企业账号未锁定，再检查网络并重新获取验证码。")
        );
    }

    private List<EvaluationCase> evaluationCases() {
        return List.of(
                new EvaluationCase("年假要提前几个工作日申请", Set.of("950001_0")),
                new EvaluationCase("病假超过一天需要提交什么", Set.of("950001_1")),
                new EvaluationCase("差旅报销必须在多少天内提交", Set.of("950002_0")),
                new EvaluationCase("单笔费用超过5000元需要几级审批", Set.of("950002_1")),
                new EvaluationCase("生产数据库变更需要谁审核", Set.of("950003_0")),
                new EvaluationCase("紧急变更后多久补齐工单", Set.of("950003_1")),
                new EvaluationCase("密码连续输错5次会怎样", Set.of("950004_0")),
                new EvaluationCase("VPN登录失败应如何排查", Set.of("950004_1")),
                new EvaluationCase("想休年假需要提前多久走流程", Set.of("950001_0")),
                new EvaluationCase("出差回来最晚什么时候报销", Set.of("950002_0")),
                new EvaluationCase("线上库修改要经过哪些人批准", Set.of("950003_0")),
                new EvaluationCase("企业网络连不上时账号和验证码该怎么检查", Set.of("950004_1")),
                new EvaluationCase(
                        "报销时限和大额费用审批规则是什么",
                        Set.of("950002_0", "950002_1")),
                new EvaluationCase(
                        "普通与紧急数据库变更分别怎么处理",
                        Set.of("950003_0", "950003_1")),
                new EvaluationCase(
                        "请假时年假和病假分别有什么要求",
                        Set.of("950001_0", "950001_1"))
        );
    }

    private void indexEvaluationData() throws IOException {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        List<ElasticsearchChunkDocument> elasticsearchDocuments = new ArrayList<>();

        for (EvaluationChunk chunk : evaluationChunks()) {
            Metadata metadata = new Metadata()
                    .put("documentId", chunk.getDocumentId())
                    .put("chunkIndex", chunk.getChunkIndex());
            TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
            redisEmbeddingStore.add(
                    embeddingModel.embed(segment.text()).content(),
                    segment
            );

            elasticsearchDocuments.add(new ElasticsearchChunkDocument(
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    chunk.getContent()
            ));
        }

        elasticsearchSearchService.indexChunks(elasticsearchDocuments);
        elasticsearchSearchService.refreshIndex();
    }

    private void deleteEvaluationData() throws IOException {
        for (String redisKey : redisCleanupClient.keys("embedding:*")) {
            Map<?, ?> storedEmbedding = redisCleanupClient.jsonGet(redisKey, Map.class);
            if (storedEmbedding == null) {
                continue;
            }
            String storedDocumentId = String.valueOf(storedEmbedding.get("documentId"));
            boolean isEvaluationDocument = EVALUATION_DOCUMENT_IDS.stream()
                    .map(String::valueOf)
                    .anyMatch(storedDocumentId::equals);
            if (isEvaluationDocument) {
                redisCleanupClient.del(redisKey);
            }
        }

        for (Long documentId : EVALUATION_DOCUMENT_IDS) {
            elasticsearchSearchService.deleteByDocumentId(documentId);
        }
        elasticsearchSearchService.refreshIndex();
    }

    /**
     * 前K条是否命中
     * @param retrievedChunkKeys
     * @param relevantChunkKeys
     * @param k
     * @return
     */
    private boolean isHitAtK(List<String> retrievedChunkKeys,Set<String>relevantChunkKeys,int k){
        for(int i=0;i<Math.min(k,retrievedChunkKeys.size());i++){
            if(relevantChunkKeys.contains(retrievedChunkKeys.get(i)))return true;
        }
        return false;
    }

    /**
     * 前K条命中的正确切片数量 / 所有正确切片数量
     * @param retrievedChunkKeys
     * @param relevantChunkKeys
     * @param k
     * @return
     */
    private double recallAtK(List<String> retrievedChunkKeys,Set<String>relevantChunkKeys,int k){
        double cnt=0;
        if(relevantChunkKeys.isEmpty())return 0.0;
        for(int i=0;i<Math.min(k,retrievedChunkKeys.size());i++){
            if(relevantChunkKeys.contains(retrievedChunkKeys.get(i)))cnt++;
        }
        return cnt/(double) relevantChunkKeys.size();
    }

    private String chunkKey(Long documentId, Integer chunkIndex) {
        return documentId + "_" + chunkIndex;
    }

    private List<String> toCandidateKeys(List<RetrievalCandidate> candidates) {
        List<String> keys = new ArrayList<>();
        for (RetrievalCandidate candidate : candidates) {
            keys.add(chunkKey(candidate.getDocumentId(), candidate.getChunkIndex()));
        }
        return keys;
    }

    private List<String> toFusedCandidateKeys(List<FusedRetrievalCandidate> candidates) {
        List<String> keys = new ArrayList<>();
        for (FusedRetrievalCandidate candidate : candidates) {
            keys.add(chunkKey(candidate.getDocumentId(), candidate.getChunkIndex()));
        }
        return keys;
    }

    private EvaluationSummary evaluateStrategy(
            String strategy,
            SearchOperation searchOperation) throws IOException {
        List<EvaluationCase> cases = evaluationCases();
        List<Double> latenciesMs = new ArrayList<>();
        int hitCount = 0;
        double recallSum = 0.0;

        for (EvaluationCase evaluationCase : cases) {
            long startedAt = System.nanoTime();
            List<String> retrievedKeys = searchOperation.search(evaluationCase.getQuery());
            double latencyMs = (System.nanoTime() - startedAt) / 1_000_000.0;

            boolean hit = isHitAtK(
                    retrievedKeys,
                    evaluationCase.getRelevantChunkKeys(),
                    TOP_K
            );
            double recall = recallAtK(
                    retrievedKeys,
                    evaluationCase.getRelevantChunkKeys(),
                    TOP_K
            );

            if (hit) {
                hitCount++;
            }
            recallSum += recall;
            latenciesMs.add(latencyMs);

            System.out.printf(
                    Locale.ROOT,
                    "EVALUATION_CASE|strategy=%s|query=%s|expected=%s|actual=%s|hit=%s|recall=%.4f|latencyMs=%.3f%n",
                    strategy,
                    evaluationCase.getQuery(),
                    evaluationCase.getRelevantChunkKeys(),
                    retrievedKeys.subList(0, Math.min(TOP_K, retrievedKeys.size())),
                    hit,
                    recall,
                    latencyMs
            );
        }

        double averageLatencyMs = latenciesMs.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        Collections.sort(latenciesMs);
        int p95Index = Math.max(
                0,
                (int) Math.ceil(latenciesMs.size() * 0.95) - 1
        );
        double p95LatencyMs = latenciesMs.get(p95Index);

        EvaluationSummary summary = new EvaluationSummary(
                strategy,
                hitCount / (double) cases.size(),
                recallSum / cases.size(),
                averageLatencyMs,
                p95LatencyMs
        );
        printSummary(summary);
        return summary;
    }

    private void printSummary(EvaluationSummary summary) {
        System.out.printf(
                Locale.ROOT,
                "EVALUATION_SUMMARY|strategy=%s|hitAt%d=%.4f|recallAt%d=%.4f|avgLatencyMs=%.3f|p95LatencyMs=%.3f%n",
                summary.getStrategy(),
                TOP_K,
                summary.getHitAtK(),
                TOP_K,
                summary.getRecallAtK(),
                summary.getAverageLatencyMs(),
                summary.getP95LatencyMs()
        );
    }

    private void assertValidSummary(EvaluationSummary summary) {
        assertThat(summary.getHitAtK()).isBetween(0.0, 1.0);
        assertThat(summary.getRecallAtK()).isBetween(0.0, 1.0);
        assertThat(summary.getHitAtK()).isGreaterThan(0.0);
        assertThat(summary.getAverageLatencyMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(summary.getP95LatencyMs()).isGreaterThanOrEqualTo(0.0);
    }

    @Test  //Arrange（准备）→ Act（执行）→ Assert（断言）
    void calculatesHitsAndRecallAtK(){
            // Arrange：准备固定的输入和预期答案
            List<String> retrievedChunkKeys =
                    List.of("2_0", "1_0", "3_0");
            Set<String> relevantChunkKeys =
                    Set.of("1_0", "1_1");
            int k = 2;

            // Act：真正调用需要测试的方法
            boolean hit =
                    isHitAtK(retrievedChunkKeys, relevantChunkKeys, k);
            double recall =
                    recallAtK(retrievedChunkKeys, relevantChunkKeys, k);

            // Assert：检查实际结果是否符合预期
            assertThat(hit).isTrue();
            assertThat(recall).isEqualTo(0.5);
    }

    @Test
    void comparesRedisElasticsearchAndHybridRetrieval() throws IOException {
        assertThat(evaluationChunks()).hasSize(8);
        assertThat(evaluationCases()).hasSize(15);

        String warmUpQuery = evaluationCases().get(0).getQuery();
        vectorSearchService.searchVectorCandidates(
                warmUpQuery,
                CANDIDATE_LIMIT,
                MIN_VECTOR_SCORE
        );
        elasticsearchSearchService.searchBm25Candidates(
                warmUpQuery,
                CANDIDATE_LIMIT
        );
        hybridRetrievalService.search(
                warmUpQuery,
                CANDIDATE_LIMIT,
                MIN_VECTOR_SCORE,
                TOP_K
        );

        EvaluationSummary redisSummary = evaluateStrategy(
                "REDIS_VECTOR",
                query -> toCandidateKeys(
                        vectorSearchService.searchVectorCandidates(
                                query,
                                CANDIDATE_LIMIT,
                                MIN_VECTOR_SCORE
                        )
                )
        );
        EvaluationSummary elasticsearchSummary = evaluateStrategy(
                "ELASTICSEARCH_BM25",
                query -> toCandidateKeys(
                        elasticsearchSearchService.searchBm25Candidates(
                                query,
                                CANDIDATE_LIMIT
                        )
                )
        );
        EvaluationSummary hybridSummary = evaluateStrategy(
                "HYBRID_RRF",
                query -> toFusedCandidateKeys(
                        hybridRetrievalService.search(
                                query,
                                CANDIDATE_LIMIT,
                                MIN_VECTOR_SCORE,
                                TOP_K
                        )
                )
        );

        assertValidSummary(redisSummary);
        assertValidSummary(elasticsearchSummary);
        assertValidSummary(hybridSummary);
    }
}
