package com.kb.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import dev.langchain4j.data.document.Metadata;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 使用本地 Redis Stack 和 Elasticsearch 验证真实双路候选能够合并为同一个 chunk。
 */
class HybridRetrievalServiceIT {

    private static final long TEST_DOCUMENT_ID = 930001L;
    private static final int TEST_CHUNK_INDEX = 0;
    private static final String PROBE_TEXT =
            "hybridfusionprobe hybrid fusion retrieval evidence";

    private ElasticsearchTransport transport;
    private ElasticsearchSearchService elasticsearchSearchService;
    private EmbeddingStore<TextSegment> redisEmbeddingStore;
    private JedisPooled redisCleanupClient;

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
        deleteTestEmbeddingDocuments();
        redisEmbeddingStore = RedisEmbeddingStore.builder()
                .host("localhost")
                .port(6379)
                .dimension(EmbeddingModelConfig.EMBEDDING_DIMENSION)
                .indexName("document-embeddings")
                .metadataKeys(List.of("documentId", "chunkIndex"))
                .build();

        Metadata metadata = new Metadata()
                .put("documentId", TEST_DOCUMENT_ID)
                .put("chunkIndex", TEST_CHUNK_INDEX);
        TextSegment segment = TextSegment.from(PROBE_TEXT, metadata);
        EmbeddingModel embeddingModel = new BgeSmallZhV15EmbeddingModel();
        redisEmbeddingStore.add(
                embeddingModel.embed(segment.text()).content(),
                segment);

        elasticsearchSearchService.indexChunk(new ElasticsearchChunkDocument(
                TEST_DOCUMENT_ID,
                TEST_CHUNK_INDEX,
                PROBE_TEXT
        ));
        elasticsearchSearchService.refreshIndex();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (redisCleanupClient != null) {
            deleteTestEmbeddingDocuments();
        }
        if (elasticsearchSearchService != null) {
            elasticsearchSearchService.deleteByDocumentId(TEST_DOCUMENT_ID);
            elasticsearchSearchService.refreshIndex();
        }
        if (redisCleanupClient != null) {
            redisCleanupClient.close();
        }
        if (transport != null) {
            transport.close();
        }
    }

    private void deleteTestEmbeddingDocuments() {
        for (String key : redisCleanupClient.keys("embedding:*")) {
            Map<?, ?> storedEmbedding = redisCleanupClient.jsonGet(key, Map.class);
            if (storedEmbedding != null
                    && String.valueOf(TEST_DOCUMENT_ID)
                    .equals(String.valueOf(storedEmbedding.get("documentId")))) {
                redisCleanupClient.del(key);
            }
        }
    }

    @Test
    void searchMergesRedisAndElasticsearchCandidateForSameChunk() throws IOException {
        VectorSearchService vectorSearchService = new VectorSearchService();
        ReflectionTestUtils.setField(vectorSearchService, "redisHost", "localhost");
        ReflectionTestUtils.setField(vectorSearchService, "redisPort", 6379);

        DepartmentAccessService departmentAccessService = mock(DepartmentAccessService.class);
        when(departmentAccessService.currentScope())
                .thenReturn(new DepartmentAccessService.AccessScope(true, Set.of()));
        HybridRetrievalService service = new HybridRetrievalService(
                vectorSearchService,
                elasticsearchSearchService,
                new RrfFusionService(),
                mock(RetrievalResultService.class),
                departmentAccessService
        );

        List<FusedRetrievalCandidate> result =
                service.search(PROBE_TEXT, 10, 0.0, 10);

        FusedRetrievalCandidate merged = result.stream()
                .filter(candidate ->
                        candidate.getDocumentId().equals(TEST_DOCUMENT_ID)
                                && candidate.getChunkIndex().equals(TEST_CHUNK_INDEX))
                .findFirst()
                .orElseThrow();

        assertThat(merged.getSources())
                .containsExactlyInAnyOrder(
                        RetrievalSource.REDIS_VECTOR,
                        RetrievalSource.ELASTICSEARCH_BM25);
        assertThat(merged.getFusionScore()).isPositive();
    }
}
