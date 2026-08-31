package com.kb.demo.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.service.ElasticsearchSearchService;
import com.kb.demo.service.RrfFusionService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import com.kb.demo.config.EmbeddingModelConfig;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves that an evaluation run owns its Redis prefix and Elasticsearch index. */
class RetrievalEvaluationCorpusIsolationIT {
    private static final long DEV_DOCUMENT_ID = 989999991L;
    private static final long RUN_B_ONLY_DOCUMENT_ID = 989999992L;

    private ElasticsearchTransport transport;
    private ElasticsearchClient client;
    private ElasticsearchSearchService productionElasticsearch;
    private String developmentEmbeddingKey;
    private final List<IsolatedRun> runs = new ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        try {
            for (IsolatedRun run : runs) run.cleanUp();
            if (developmentEmbeddingKey != null) {
                try (redis.clients.jedis.JedisPooled redis = new redis.clients.jedis.JedisPooled("localhost", 6379)) {
                    redis.del(developmentEmbeddingKey);
                }
            }
            if (productionElasticsearch != null) {
                productionElasticsearch.deleteByDocumentId(DEV_DOCUMENT_ID);
                productionElasticsearch.refreshIndex();
            }
        } finally {
            if (transport != null) transport.close();
        }
    }

    @Test
    void evaluation_ignores_existing_runtime_documents() throws Exception {
        setupClient();
        addDevelopmentContamination();
        IsolatedRun run = createRun("contamination");

        List<RetrievalCandidate> vector = run.redis.search("年假提前3个工作日申请", 20, 0.72, null);
        List<RetrievalCandidate> bm25 = run.elasticsearch.searchBm25CandidatesUnfiltered("年假提前3个工作日申请", 20, null);
        var hybrid = new RrfFusionService().fuse(vector, bm25, 60);

        assertNoDocument(vector, DEV_DOCUMENT_ID);
        assertNoDocument(bm25, DEV_DOCUMENT_ID);
        assertThat(hybrid).noneMatch(candidate -> candidate.getDocumentId().equals(DEV_DOCUMENT_ID));

        run.cleanUp();
        assertThat(productionElasticsearch.countByDocumentId(DEV_DOCUMENT_ID)).isEqualTo(1);
        try (redis.clients.jedis.JedisPooled redis = new redis.clients.jedis.JedisPooled("localhost", 6379)) {
            assertThat(redis.exists(developmentEmbeddingKey)).isTrue();
        }
    }

    @Test
    void evaluation_only_reads_current_fixture_namespace() throws Exception {
        setupClient();
        IsolatedRun runA = createRun("run-a");
        IsolatedRun runB = createRun("run-b");
        String marker = "跨运行隔离专属标记 跨运行隔离专属标记 跨运行隔离专属标记";
        runB.redis.indexDocument(RUN_B_ONLY_DOCUMENT_ID, 0, marker);
        runB.elasticsearch.indexChunk(new ElasticsearchChunkDocument(RUN_B_ONLY_DOCUMENT_ID, 0, marker));
        runB.elasticsearch.refreshIndex();

        assertNoDocument(runA.redis.search(marker, 20, 0.0, null), RUN_B_ONLY_DOCUMENT_ID);
        assertNoDocument(runA.elasticsearch.searchBm25CandidatesUnfiltered(marker, 20, null), RUN_B_ONLY_DOCUMENT_ID);
        assertThat(runB.elasticsearch.countByDocumentId(RUN_B_ONLY_DOCUMENT_ID)).isEqualTo(1);

        runA.cleanUp();
        assertThat(runB.redis.keyCount()).isEqualTo(13);
        assertThat(runB.elasticsearch.countByDocumentId(RUN_B_ONLY_DOCUMENT_ID)).isEqualTo(1);
    }

    private void setupClient() {
        transport = new RestClientTransport(
                org.elasticsearch.client.RestClient.builder(new HttpHost("localhost", 9200, "http")).build(),
                new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
        productionElasticsearch = new ElasticsearchSearchService(client);
    }

    private void addDevelopmentContamination() throws IOException {
        String content = "开发环境干扰文档：年假提前3个工作日申请，并且该文本不应进入 Evaluation。";
        productionElasticsearch.indexChunk(new ElasticsearchChunkDocument(DEV_DOCUMENT_ID, 0, content));
        productionElasticsearch.refreshIndex();
        var store = RedisEmbeddingStore.builder().host("localhost").port(6379)
                .dimension(EmbeddingModelConfig.EMBEDDING_DIMENSION)
                .indexName("document-embeddings").metadataKeys(List.of("documentId", "chunkIndex")).build();
        String embeddingId = store.add(new BgeSmallZhV15EmbeddingModel().embed(content).content(),
                TextSegment.from(content, new Metadata().put("documentId", DEV_DOCUMENT_ID).put("chunkIndex", 0)));
        developmentEmbeddingKey = "embedding:" + embeddingId;
    }

    private IsolatedRun createRun(String label) throws Exception {
        String namespace = "retrieval-eval-isolation-" + label + "-" + UUID.randomUUID().toString().replace("-", "");
        EvaluationFixture fixture = EvaluationFixture.current();
        EvaluationRedisVectorSearch redis = new EvaluationRedisVectorSearch("localhost", 6379, namespace);
        ElasticsearchSearchService elasticsearch = new ElasticsearchSearchService(client, namespace + "-es");
        ReflectionTestUtils.setField(elasticsearch, "minBm25Score", 10.0);
        fixture.index(redis, elasticsearch);
        IsolatedRun run = new IsolatedRun(redis, elasticsearch, namespace + "-es");
        runs.add(run);
        return run;
    }

    private void assertNoDocument(List<RetrievalCandidate> candidates, long documentId) {
        assertThat(candidates).noneMatch(candidate -> candidate.getDocumentId().equals(documentId));
    }

    private final class IsolatedRun {
        private final EvaluationRedisVectorSearch redis;
        private final ElasticsearchSearchService elasticsearch;
        private final String elasticsearchIndex;
        private boolean cleaned;

        private IsolatedRun(EvaluationRedisVectorSearch redis, ElasticsearchSearchService elasticsearch,
                String elasticsearchIndex) {
            this.redis = redis;
            this.elasticsearch = elasticsearch;
            this.elasticsearchIndex = elasticsearchIndex;
        }

        private void cleanUp() throws IOException {
            if (cleaned) return;
            redis.cleanUp();
            if (client.indices().exists(e -> e.index(elasticsearchIndex)).value()) {
                client.indices().delete(d -> d.index(elasticsearchIndex));
            }
            cleaned = true;
        }
    }
}
