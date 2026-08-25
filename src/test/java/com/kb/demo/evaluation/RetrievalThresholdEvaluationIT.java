package com.kb.demo.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.service.ElasticsearchSearchService;
import com.kb.demo.service.VectorSearchService;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线阈值评估入口。
 *
 * <p>运行示例：
 * {@code .\mvnw.cmd -Dit.test=RetrievalThresholdEvaluationIT
 * -Dretrieval.eval.dataset=D:\data\retrieval-threshold-dataset.json verify}
 *
 * <p>它只读取已有 Redis / Elasticsearch 索引；不会写入或删除任何业务数据。
 */
class RetrievalThresholdEvaluationIT {

    private static final int[] KS = {1, 3, 5};
    private static final DateTimeFormatter OUTPUT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ElasticsearchTransport transport;

    @AfterEach
    void closeTransport() throws IOException {
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void sweepsRedisAndElasticsearchThresholds() throws Exception {
        Path datasetPath = requiredDatasetPath();
        List<EvaluationCase> cases = loadDataset(datasetPath);
        validateDataset(cases, datasetPath);

        int candidateLimit = positiveIntProperty("retrieval.eval.candidate-limit", 50);
        int sweepPoints = positiveIntProperty("retrieval.eval.sweep-points", 21);
        Path outputDirectory = outputDirectory();
        Files.createDirectories(outputDirectory);

        VectorSearchService vectorSearchService = vectorSearchService();
        ElasticsearchSearchService elasticsearchSearchService = elasticsearchSearchService();

        Map<String, List<RetrievalCandidate>> redisRawCandidates = collectRedisRawCandidates(
                cases, vectorSearchService, candidateLimit);
        Map<String, List<RetrievalCandidate>> elasticsearchRawCandidates = collectElasticsearchRawCandidates(
                cases, elasticsearchSearchService, candidateLimit);

        List<BackendResult> results = List.of(
                evaluateBackend(Backend.REDIS_VECTOR, cases, redisRawCandidates, sweepPoints),
                evaluateBackend(Backend.ELASTICSEARCH_BM25, cases, elasticsearchRawCandidates, sweepPoints));

        writeRawCandidateSnapshot(outputDirectory, redisRawCandidates, elasticsearchRawCandidates);
        writeCsv(outputDirectory.resolve("redis-vector.csv"), results.get(0));
        writeCsv(outputDirectory.resolve("elasticsearch-bm25.csv"), results.get(1));
        writeJson(outputDirectory.resolve("threshold-metrics.json"), results);
        printTables(results);

        assertThat(results).allSatisfy(result -> assertThat(result.rows).isNotEmpty());
        System.out.printf("Threshold evaluation output: %s%n", outputDirectory.toAbsolutePath());
    }

    @Test
    void calculatesThresholdMetricsUsingDocumentIdAndChunkIndex() {
        EvaluationCase evaluationCase = new EvaluationCase();
        evaluationCase.query = "报销流程";
        evaluationCase.relevantChunks = List.of(
                relevantChunk(1L, 0),
                relevantChunk(1L, 1));
        Map<String, List<RetrievalCandidate>> rawCandidates = Map.of(
                evaluationCase.query, List.of(
                        candidate(1L, 0, 0.9),
                        candidate(2L, 0, 0.8),
                        candidate(1L, 1, 0.7)));

        MetricRow row = calculateMetrics(
                Backend.REDIS_VECTOR, 0.75, 3, List.of(evaluationCase), rawCandidates);

        assertThat(row.hitAtK).isEqualTo(1.0);
        assertThat(row.recallAtK).isEqualTo(0.5);
        assertThat(row.precisionAtK).isEqualTo(0.5);
        assertThat(row.refusalRate).isZero();
        assertThat(row.avgRetainedCandidates).isEqualTo(2.0);
        assertThat(row.avgReturnedChunks).isEqualTo(2.0);
    }

    private Path requiredDatasetPath() {
        String configuredPath = System.getProperty("retrieval.eval.dataset");
        Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
                "Set -Dretrieval.eval.dataset=<absolute path>; see src/test/resources/evaluation/retrieval-threshold-dataset.example.json");
        Path datasetPath = Path.of(configuredPath).toAbsolutePath().normalize();
        assertThat(datasetPath).exists().isRegularFile();
        return datasetPath;
    }

    private List<EvaluationCase> loadDataset(Path datasetPath) throws IOException {
        return objectMapper.readValue(datasetPath.toFile(), new TypeReference<>() { });
    }

    private void validateDataset(List<EvaluationCase> cases, Path datasetPath) {
        assertThat(cases).as("evaluation dataset %s", datasetPath).isNotEmpty();
        for (EvaluationCase evaluationCase : cases) {
            assertThat(evaluationCase.query).isNotBlank();
            assertThat(evaluationCase.relevantChunks).isNotEmpty();
            for (RelevantChunk relevantChunk : evaluationCase.relevantChunks) {
                assertThat(relevantChunk.documentId).isNotNull();
                assertThat(relevantChunk.chunkIndex).isNotNull();
            }
        }
    }

    private VectorSearchService vectorSearchService() {
        VectorSearchService service = new VectorSearchService();
        ReflectionTestUtils.setField(service, "redisHost",
                System.getProperty("retrieval.eval.redis-host", "localhost"));
        ReflectionTestUtils.setField(service, "redisPort",
                positiveIntProperty("retrieval.eval.redis-port", 6379));
        return service;
    }

    private ElasticsearchSearchService elasticsearchSearchService() {
        int port = positiveIntProperty("retrieval.eval.elasticsearch-port", 9200);
        transport = new RestClientTransport(
                org.elasticsearch.client.RestClient.builder(new HttpHost("localhost", port, "http")).build(),
                new JacksonJsonpMapper());
        return new ElasticsearchSearchService(new ElasticsearchClient(transport));
    }

    private Map<String, List<RetrievalCandidate>> collectRedisRawCandidates(
            List<EvaluationCase> cases, VectorSearchService service, int candidateLimit) {
        Map<String, List<RetrievalCandidate>> candidatesByQuery = new LinkedHashMap<>();
        for (EvaluationCase evaluationCase : cases) {
            List<RetrievalCandidate> candidates = service.searchVectorCandidates(
                    evaluationCase.query, candidateLimit, 0.0);
            assertDescendingByScore(candidates, evaluationCase.query);
            candidatesByQuery.put(evaluationCase.query, candidates);
        }
        return candidatesByQuery;
    }

    private Map<String, List<RetrievalCandidate>> collectElasticsearchRawCandidates(
            List<EvaluationCase> cases, ElasticsearchSearchService service, int candidateLimit) throws IOException {
        Map<String, List<RetrievalCandidate>> candidatesByQuery = new LinkedHashMap<>();
        for (EvaluationCase evaluationCase : cases) {
            List<RetrievalCandidate> candidates = service.searchBm25CandidatesUnfiltered(
                    evaluationCase.query, candidateLimit, null);
            assertDescendingByScore(candidates, evaluationCase.query);
            candidatesByQuery.put(evaluationCase.query, candidates);
        }
        return candidatesByQuery;
    }

    private void assertDescendingByScore(List<RetrievalCandidate> candidates, String query) {
        for (int index = 1; index < candidates.size(); index++) {
            assertThat(candidates.get(index - 1).getRawScore())
                    .as("raw candidates must remain score-descending, query=%s", query)
                    .isGreaterThanOrEqualTo(candidates.get(index).getRawScore());
        }
    }

    private BackendResult evaluateBackend(
            Backend backend,
            List<EvaluationCase> cases,
            Map<String, List<RetrievalCandidate>> rawCandidatesByQuery,
            int sweepPoints) {
        List<Double> thresholds = scoreDerivedThresholds(rawCandidatesByQuery.values(), sweepPoints);
        List<MetricRow> rows = new ArrayList<>();
        for (double threshold : thresholds) {
            for (int k : KS) {
                rows.add(calculateMetrics(backend, threshold, k, cases, rawCandidatesByQuery));
            }
        }
        return new BackendResult(backend.name(), thresholds, rows);
    }

    private List<Double> scoreDerivedThresholds(
            Iterable<List<RetrievalCandidate>> rawCandidateLists, int sweepPoints) {
        List<Double> scores = new ArrayList<>();
        for (List<RetrievalCandidate> candidates : rawCandidateLists) {
            candidates.forEach(candidate -> scores.add(candidate.getRawScore()));
        }
        assertThat(scores).as("At least one raw candidate is required to derive threshold range").isNotEmpty();

        scores.sort(Double::compareTo);
        int pointCount = Math.min(sweepPoints, scores.size());
        Set<Double> thresholds = new TreeSet<>();
        for (int point = 0; point < pointCount; point++) {
            int index = pointCount == 1
                    ? 0
                    : (int) Math.round(point * (scores.size() - 1.0) / (pointCount - 1.0));
            thresholds.add(scores.get(index));
        }
        return List.copyOf(thresholds);
    }

    private MetricRow calculateMetrics(
            Backend backend,
            double threshold,
            int k,
            List<EvaluationCase> cases,
            Map<String, List<RetrievalCandidate>> rawCandidatesByQuery) {
        int hitCount = 0;
        int refusalCount = 0;
        int returnedChunkCount = 0;
        int retainedCandidateCount = 0;
        double recallSum = 0.0;
        double precisionSum = 0.0;

        for (EvaluationCase evaluationCase : cases) {
            List<RetrievalCandidate> retained = rawCandidatesByQuery.get(evaluationCase.query).stream()
                    .filter(candidate -> candidate.getRawScore() >= threshold)
                    .toList();
            List<RetrievalCandidate> returned = retained.subList(0, Math.min(k, retained.size()));
            Set<String> relevantKeys = relevantChunkKeys(evaluationCase);
            long relevantReturned = returned.stream().map(this::chunkKey).filter(relevantKeys::contains).count();

            if (relevantReturned > 0) {
                hitCount++;
            }
            if (retained.isEmpty()) {
                refusalCount++;
            }
            recallSum += relevantReturned / (double) relevantKeys.size();
            precisionSum += returned.isEmpty() ? 0.0 : relevantReturned / (double) returned.size();
            retainedCandidateCount += retained.size();
            returnedChunkCount += returned.size();
        }

        int queryCount = cases.size();
        return new MetricRow(
                backend.name(), threshold, k,
                hitCount / (double) queryCount,
                recallSum / queryCount,
                precisionSum / queryCount,
                refusalCount / (double) queryCount,
                retainedCandidateCount / (double) queryCount,
                returnedChunkCount / (double) queryCount,
                queryCount);
    }

    private Set<String> relevantChunkKeys(EvaluationCase evaluationCase) {
        Set<String> keys = new LinkedHashSet<>();
        for (RelevantChunk relevantChunk : evaluationCase.relevantChunks) {
            keys.add(chunkKey(relevantChunk.documentId, relevantChunk.chunkIndex));
        }
        return keys;
    }

    private String chunkKey(RetrievalCandidate candidate) {
        return chunkKey(candidate.getDocumentId(), candidate.getChunkIndex());
    }

    private String chunkKey(Long documentId, Integer chunkIndex) {
        return documentId + "_" + chunkIndex;
    }

    private void writeRawCandidateSnapshot(
            Path outputDirectory,
            Map<String, List<RetrievalCandidate>> redisCandidates,
            Map<String, List<RetrievalCandidate>> elasticsearchCandidates) throws IOException {
        writeJson(outputDirectory.resolve("raw-candidates.json"), Map.of(
                "redisVector", redisCandidates,
                "elasticsearchBm25", elasticsearchCandidates));
    }

    private void writeCsv(Path outputFile, BackendResult result) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write("backend,threshold,k,hitAtK,recallAtK,precisionAtK,refusalRate,avgRetainedCandidates,avgReturnedChunks,queryCount");
            writer.newLine();
            for (MetricRow row : result.rows) {
                writer.write(String.format(Locale.ROOT,
                        "%s,%.12f,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d",
                        row.backend, row.threshold, row.k, row.hitAtK, row.recallAtK,
                        row.precisionAtK, row.refusalRate, row.avgRetainedCandidates,
                        row.avgReturnedChunks, row.queryCount));
                writer.newLine();
            }
        }
    }

    private RelevantChunk relevantChunk(Long documentId, Integer chunkIndex) {
        RelevantChunk relevantChunk = new RelevantChunk();
        relevantChunk.documentId = documentId;
        relevantChunk.chunkIndex = chunkIndex;
        return relevantChunk;
    }

    private RetrievalCandidate candidate(Long documentId, Integer chunkIndex, double score) {
        return new RetrievalCandidate(documentId, chunkIndex, score, 1, null);
    }

    private void writeJson(Path outputFile, Object value) throws IOException {
        objectMapper.writeValue(outputFile.toFile(), value);
    }

    private void printTables(List<BackendResult> results) {
        for (BackendResult result : results) {
            for (int k : KS) {
                System.out.printf("%n%s (K=%d)%n", result.backend, k);
                System.out.println("threshold | Hit@K | Recall@K | Precision@K | RefusalRate | AvgReturnedChunks");
                System.out.println("------------------------------------------------------------------------------------");
                result.rows.stream()
                        .filter(row -> row.k == k)
                        .sorted(Comparator.comparingDouble(row -> row.threshold))
                        .forEach(row -> System.out.printf(Locale.ROOT,
                                "%.6f | %.4f | %.4f | %.4f | %.4f | %.2f%n",
                                row.threshold, row.hitAtK, row.recallAtK,
                                row.precisionAtK, row.refusalRate, row.avgReturnedChunks));
            }
        }
    }

    private int positiveIntProperty(String key, int defaultValue) {
        int value = Integer.getInteger(key, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
        return value;
    }

    private Path outputDirectory() {
        String configuredOutputDirectory = System.getProperty("retrieval.eval.output-dir");
        if (configuredOutputDirectory != null && !configuredOutputDirectory.isBlank()) {
            return Path.of(configuredOutputDirectory).toAbsolutePath().normalize();
        }
        return Path.of("target", "retrieval-threshold-evaluation",
                OUTPUT_TIME_FORMAT.format(LocalDateTime.now()));
    }

    private enum Backend {
        REDIS_VECTOR,
        ELASTICSEARCH_BM25
    }

    public static class EvaluationCase {
        public String query;
        public List<RelevantChunk> relevantChunks;
    }

    public static class RelevantChunk {
        public Long documentId;
        public Integer chunkIndex;
    }

    private record BackendResult(String backend, List<Double> thresholds, List<MetricRow> rows) {
    }

    private record MetricRow(
            String backend,
            double threshold,
            int k,
            double hitAtK,
            double recallAtK,
            double precisionAtK,
            double refusalRate,
            double avgRetainedCandidates,
            double avgReturnedChunks,
            int queryCount) {
    }
}
