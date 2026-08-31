package com.kb.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.dto.FusedRetrievalCandidate;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import com.kb.demo.config.EmbeddingModelConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 在相同语料、相同 15 个问题下比较不同 overlap 的混合检索结果。
 *
 * 评测索引只使用 960001~960004 这四个临时文档 ID，结束后会清理。
 */
class ChunkOverlapEvaluationIT {
    private static final Set<Long> EVALUATION_DOCUMENT_IDS =
            Set.of(960001L, 960002L, 960003L, 960004L);
    private static final int CHUNK_SIZE = 500;
    private static final int TOP_K = 3;
    private static final int CANDIDATE_LIMIT = 20;

    private ElasticsearchTransport transport;
    private ElasticsearchSearchService elasticsearchSearchService;
    private EmbeddingStore<TextSegment> redisEmbeddingStore;
    private JedisPooled redisCleanupClient;
    private VectorSearchService vectorSearchService;
    private HybridRetrievalService hybridRetrievalService;

    @BeforeEach
    void setUp() throws IOException {
        RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        elasticsearchSearchService = new ElasticsearchSearchService(new ElasticsearchClient(transport));
        elasticsearchSearchService.createIndexIfAbsent();

        redisCleanupClient = new JedisPooled("localhost", 6379);
        redisEmbeddingStore = RedisEmbeddingStore.builder()
                .host("localhost").port(6379).dimension(EmbeddingModelConfig.EMBEDDING_DIMENSION)
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
                vectorSearchService, elasticsearchSearchService, new RrfFusionService(),
                mock(RetrievalResultService.class), departmentAccessService);
        deleteEvaluationData();
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

    @Test
    void comparesOverlap10And50WithTheSame15Queries() throws IOException {
        OverlapSummary overlap10 = evaluateOverlap(10);
        OverlapSummary overlap50 = evaluateOverlap(50);

        assertThat(overlap10.hitAt3()).isBetween(0.0, 1.0);
        assertThat(overlap50.hitAt3()).isBetween(0.0, 1.0);
    }

    /**
     * 连续、没有句号或换行的文本会触发递归切分的硬切分。
     * 此时当前分割器不会让 overlap 回退，完整证据可能在所有 chunk 中都缺失。
     */
    @Test
    void exposesBoundaryEvidenceLossForUnbrokenText() {
        String evidence = "报销凭证必须同时包含客户编号项目编号和审批单号";
        String source = "甲".repeat(480) + evidence + "乙".repeat(500);

        List<TextSegment> overlap10Segments = DocumentSplitters.recursive(CHUNK_SIZE, 10)
                .split(Document.from(source));
        List<TextSegment> overlap50Segments = DocumentSplitters.recursive(CHUNK_SIZE, 50)
                .split(Document.from(source));
        boolean coveredByOverlap10 = overlap10Segments.stream()
                .anyMatch(segment -> segment.text().contains(evidence));
        boolean coveredByOverlap50 = overlap50Segments.stream()
                .anyMatch(segment -> segment.text().contains(evidence));

        System.out.printf(Locale.ROOT,
                "BOUNDARY_BAD_CASE|overlap10=%s|overlap50=%s|segments10=%s|segments50=%s%n",
                coveredByOverlap10, coveredByOverlap50,
                describeSegments(overlap10Segments), describeSegments(overlap50Segments));
        assertThat(coveredByOverlap10).isFalse();
        assertThat(coveredByOverlap50).isFalse();
    }

    private OverlapSummary evaluateOverlap(int overlap) throws IOException {
        deleteEvaluationData();
        Map<String, String> chunkContentByKey = indexEvaluationData(overlap);
        int hitCount = 0;

        for (EvaluationCase evaluationCase : evaluationCases()) {
            List<FusedRetrievalCandidate> candidates = hybridRetrievalService.search(
                    evaluationCase.query(), CANDIDATE_LIMIT, 0.0, TOP_K);
            boolean hit = candidates.stream().limit(TOP_K).anyMatch(candidate ->
                    candidate.getDocumentId().equals(evaluationCase.documentId())
                            && chunkContentByKey.get(chunkKey(candidate.getDocumentId(), candidate.getChunkIndex()))
                            .contains(evaluationCase.evidence()));
            if (hit) {
                hitCount++;
            }
        }

        OverlapSummary summary = new OverlapSummary(overlap,
                hitCount / (double) evaluationCases().size());
        System.out.printf(Locale.ROOT,
                "OVERLAP_EVALUATION|overlap=%d|queries=%d|hybridHitAt3=%.4f%n",
                summary.overlap(), evaluationCases().size(), summary.hitAt3());
        return summary;
    }

    private Map<String, String> indexEvaluationData(int overlap) throws IOException {
        DocumentSplitter splitter = DocumentSplitters.recursive(CHUNK_SIZE, overlap);
        EmbeddingModel embeddingModel = new BgeSmallZhV15EmbeddingModel();
        List<ElasticsearchChunkDocument> elasticsearchDocuments = new ArrayList<>();
        Map<String, String> chunkContentByKey = new HashMap<>();

        for (Map.Entry<Long, String> document : evaluationDocuments().entrySet()) {
            List<TextSegment> segments = splitter.split(Document.from(document.getValue()));
            for (int index = 0; index < segments.size(); index++) {
                TextSegment segment = segments.get(index);
                Metadata metadata = new Metadata()
                        .put("documentId", document.getKey())
                        .put("chunkIndex", index);
                TextSegment indexedSegment = TextSegment.from(segment.text(), metadata);
                redisEmbeddingStore.add(embeddingModel.embed(indexedSegment.text()).content(), indexedSegment);
                elasticsearchDocuments.add(new ElasticsearchChunkDocument(
                        document.getKey(), index, indexedSegment.text()));
                chunkContentByKey.put(chunkKey(document.getKey(), index), indexedSegment.text());
            }
        }

        elasticsearchSearchService.indexChunks(elasticsearchDocuments);
        elasticsearchSearchService.refreshIndex();
        return chunkContentByKey;
    }

    private Map<Long, String> evaluationDocuments() {
        return Map.of(
                960001L, document("员工请假制度说明。", "年假需至少提前3个工作日在OA系统提交，并由直属主管审批。",
                        "病假超过1天需上传医院证明，病假不扣除年假额度。"),
                960002L, document("差旅与费用报销制度说明。", "差旅报销应在行程结束后30天内提交，逾期需部门负责人补充说明。",
                        "单笔费用超过5000元时，需要部门负责人和财务负责人两级审批。"),
                960003L, document("生产环境变更管理制度说明。", "生产数据库变更必须提交工单，经过研发负责人和DBA双人审核后方可执行。",
                        "紧急变更应先电话通知值班负责人，执行后24小时内补齐工单和复盘记录。"),
                960004L, document("企业账号与网络访问指引。", "连续5次输入错误密码会锁定企业账号30分钟。",
                        "VPN登录失败时先确认企业账号未锁定，再检查网络并重新获取验证码。")
        );
    }

    private String document(String title, String firstRule, String secondRule) {
        String filler = "本段为企业制度背景说明，用于描述适用范围、责任边界和日常操作要求，不包含需要检索的具体结论。";
        return title + filler.repeat(9) + firstRule + filler.repeat(9) + secondRule + filler.repeat(9);
    }

    private List<EvaluationCase> evaluationCases() {
        return List.of(
                new EvaluationCase("年假要提前几个工作日申请", 960001L, "年假需至少提前3个工作日"),
                new EvaluationCase("病假超过一天需要提交什么", 960001L, "病假超过1天需上传医院证明"),
                new EvaluationCase("差旅报销必须在多少天内提交", 960002L, "行程结束后30天内提交"),
                new EvaluationCase("单笔费用超过5000元需要几级审批", 960002L, "两级审批"),
                new EvaluationCase("生产数据库变更需要谁审核", 960003L, "研发负责人和DBA双人审核"),
                new EvaluationCase("紧急变更后多久补齐工单", 960003L, "24小时内补齐工单"),
                new EvaluationCase("密码连续输错5次会怎样", 960004L, "锁定企业账号30分钟"),
                new EvaluationCase("VPN登录失败应如何排查", 960004L, "检查网络并重新获取验证码"),
                new EvaluationCase("想休年假需要提前多久走流程", 960001L, "年假需至少提前3个工作日"),
                new EvaluationCase("出差回来最晚什么时候报销", 960002L, "行程结束后30天内提交"),
                new EvaluationCase("线上库修改要经过哪些人批准", 960003L, "研发负责人和DBA双人审核"),
                new EvaluationCase("企业网络连不上时账号和验证码该怎么检查", 960004L, "检查网络并重新获取验证码"),
                new EvaluationCase("报销时限和大额费用审批规则是什么", 960002L, "行程结束后30天内提交"),
                new EvaluationCase("普通与紧急数据库变更分别怎么处理", 960003L, "生产数据库变更必须提交工单"),
                new EvaluationCase("请假时年假和病假分别有什么要求", 960001L, "年假需至少提前3个工作日")
        );
    }

    private void deleteEvaluationData() throws IOException {
        for (String redisKey : redisCleanupClient.keys("embedding:*")) {
            Map<?, ?> storedEmbedding = redisCleanupClient.jsonGet(redisKey, Map.class);
            if (storedEmbedding != null && EVALUATION_DOCUMENT_IDS.stream()
                    .map(String::valueOf)
                    .anyMatch(String.valueOf(storedEmbedding.get("documentId"))::equals)) {
                redisCleanupClient.del(redisKey);
            }
        }
        for (Long documentId : EVALUATION_DOCUMENT_IDS) {
            elasticsearchSearchService.deleteByDocumentId(documentId);
        }
        elasticsearchSearchService.refreshIndex();
    }

    private String chunkKey(Long documentId, Integer chunkIndex) {
        return documentId + "_" + chunkIndex;
    }

    private String describeSegments(List<TextSegment> segments) {
        return segments.stream()
                .map(segment -> segment.text().length() + ":"
                        + segment.text().indexOf("报销凭证") + ":"
                        + segment.text().indexOf("审批单号"))
                .toList().toString();
    }

    private record EvaluationCase(String query, Long documentId, String evidence) {
    }

    private record OverlapSummary(int overlap, double hitAt3) {
    }
}
