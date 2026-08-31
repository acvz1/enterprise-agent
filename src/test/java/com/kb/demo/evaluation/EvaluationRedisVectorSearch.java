package com.kb.demo.evaluation;

import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import dev.langchain4j.data.embedding.Embedding;
import com.kb.demo.config.EmbeddingModelConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.RediSearchUtil;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test-only Redis Stack adapter. LangChain4j 0.34.0 fixes Redis JSON keys to the
 * global {@code embedding:} prefix, so changing only its index name cannot isolate
 * an evaluation corpus. This adapter keeps the same model, 384 dimensions, HNSW,
 * cosine KNN query and score conversion, but owns one run-specific key prefix.
 */
final class EvaluationRedisVectorSearch implements AutoCloseable {
    private static final int DIMENSION = EmbeddingModelConfig.EMBEDDING_DIMENSION;
    private static final String VECTOR_FIELD = "vector";
    private static final String SCORE_FIELD = "vector_score";

    private final JedisPooled client;
    private final String indexName;
    private final String keyPrefix;
    private final EmbeddingModel embeddingModel = new BgeSmallZhV15EmbeddingModel();

    EvaluationRedisVectorSearch(String host, int port, String namespace) {
        this.client = new JedisPooled(host, port);
        this.indexName = namespace + "-redis";
        this.keyPrefix = namespace + ":";
    }

    String indexName() {
        return indexName;
    }

    String keyPrefix() {
        return keyPrefix;
    }

    void create() {
        dropIndexIfPresent();
        client.ftCreate(indexName,
                FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(keyPrefix),
                List.of(
                        TextField.of("$.text").as("text").weight(1.0),
                        VectorField.builder()
                                .fieldName("$." + VECTOR_FIELD)
                                .algorithm(VectorField.VectorAlgorithm.HNSW)
                                .attributes(Map.of(
                                        "DIM", DIMENSION,
                                        "DISTANCE_METRIC", "COSINE",
                                        "TYPE", "FLOAT32",
                                        "INITIAL_CAP", 5))
                                .as(VECTOR_FIELD)
                                .build(),
                        TextField.of("$.documentId").as("documentId").weight(1.0),
                        TextField.of("$.chunkIndex").as("chunkIndex").weight(1.0)
                ));
    }

    void index(List<EvaluationFixture.FixtureChunk> chunks) {
        for (EvaluationFixture.FixtureChunk chunk : chunks) {
            indexDocument(chunk.documentId(), chunk.chunkIndex(), chunk.content());
        }
    }

    void indexDocument(Long documentId, Integer chunkIndex, String content) {
        Embedding embedding = embeddingModel.embed(content).content();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(VECTOR_FIELD, embedding.vector());
        document.put("text", content);
        document.put("documentId", String.valueOf(documentId));
        document.put("chunkIndex", String.valueOf(chunkIndex));
        client.jsonSetWithEscape(key(documentId, chunkIndex), Path2.of("$"), document);
    }

    List<RetrievalCandidate> search(String query, int maxResults, double minScore, Set<Long> allowedDocumentIds) {
        int fetchSize = allowedDocumentIds == null ? maxResults : maxResults * 3;
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        Query request = new Query(String.format("*=>[KNN %d @%s $BLOB AS %s ]", fetchSize, VECTOR_FIELD, SCORE_FIELD))
                .addParam("BLOB", RediSearchUtil.ToByteArray(queryEmbedding.vector()))
                .returnFields("documentId", "chunkIndex", SCORE_FIELD)
                .setSortBy(SCORE_FIELD, true)
                .dialect(2);
        SearchResult result = client.ftSearch(indexName, request);
        List<RetrievalCandidate> candidates = new ArrayList<>();
        for (Document document : result.getDocuments()) {
            double score = (2.0 - Double.parseDouble(document.getString(SCORE_FIELD))) / 2.0;
            if (score < minScore) {
                continue;
            }
            Long documentId = Long.valueOf(document.getString("documentId"));
            if (allowedDocumentIds != null && !allowedDocumentIds.contains(documentId)) {
                continue;
            }
            candidates.add(new RetrievalCandidate(documentId, Integer.valueOf(document.getString("chunkIndex")),
                    score, candidates.size() + 1, RetrievalSource.REDIS_VECTOR));
            if (candidates.size() == maxResults) {
                break;
            }
        }
        return candidates;
    }

    long keyCount() {
        return client.keys(keyPrefix + "*").size();
    }

    void cleanUp() {
        dropIndexIfPresent();
        for (String key : client.keys(keyPrefix + "*")) {
            client.del(key);
        }
    }

    private void dropIndexIfPresent() {
        try {
            client.ftDropIndex(indexName);
        } catch (redis.clients.jedis.exceptions.JedisDataException ignored) {
            // A missing test-owned index is already clean.
        }
    }

    private String key(Long documentId, Integer chunkIndex) {
        return keyPrefix + documentId + "-" + chunkIndex;
    }

    @Override
    public void close() {
        client.close();
    }
}
