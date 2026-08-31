package com.kb.demo.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.service.ElasticsearchSearchService;
import com.kb.demo.service.RrfFusionService;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline, self-cleaning E2E entry. It writes a 12-section fixture to the real local
 * Redis Stack and Elasticsearch, invokes the production candidate retrievers and
 * RrfFusionService, then removes only the 970xxx temporary fixture documents.
 */
class RetrievalEvaluationV2IT {
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ElasticsearchTransport transport;
    private ElasticsearchClient elasticsearchClient;
    private ElasticsearchSearchService elasticsearch;
    private EvaluationRedisVectorSearch redisVector;
    private String elasticsearchIndex;

    @AfterEach
    void cleanUp() throws IOException {
        try {
            if (redisVector != null) redisVector.cleanUp();
            if (elasticsearchClient != null && elasticsearchIndex != null
                    && elasticsearchClient.indices().exists(e -> e.index(elasticsearchIndex)).value()) {
                elasticsearchClient.indices().delete(d -> d.index(elasticsearchIndex));
            }
        } finally {
            if (redisVector != null) redisVector.close();
            if (transport != null) transport.close();
        }
    }

    @Test
    void runsVersionedRetrievalEvaluationAgainstRealRedisAndElasticsearch() throws Exception {
        Path datasetPath = datasetPath();
        List<EvaluationCase> cases = RetrievalEvaluationV2.loadCases(datasetPath);
        EvaluationFixture fixture = EvaluationFixture.current();
        RetrievalEvaluationV2.EvaluationConfig config = config();
        String runId = "retrieval-v2-" + RUN_TIME.format(LocalDateTime.now());
        setup(runId);
        fixture.index(redisVector, elasticsearch);
        assertThat(redisVector.keyCount()).isEqualTo(fixture.chunks().size());
        assertThat(elasticsearch.countByDocumentId(970001L)).isEqualTo(2);

        List<RetrievalEvaluationV2.CaseResult> results = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            Set<Long> allowedDocumentIds = allowedDocumentIds(evaluationCase.permissionContext(), fixture);
            List<RetrievalCandidate> vectorCandidates = redisVector.search(
                    evaluationCase.query(), config.candidateLimit(), config.minVectorScore(), allowedDocumentIds);
            List<RetrievalCandidate> bm25Candidates = elasticsearch.searchBm25Candidates(
                    evaluationCase.query(), config.candidateLimit(), allowedDocumentIds);
            List<RetrievalCandidate> unrestrictedVector = evaluationCase.permissionContext() == null ? List.of()
                    : redisVector.search(evaluationCase.query(), config.candidateLimit(), config.minVectorScore(), null);
            List<RetrievalCandidate> unrestrictedBm25 = evaluationCase.permissionContext() == null ? List.of()
                    : elasticsearch.searchBm25Candidates(evaluationCase.query(), config.candidateLimit(), null);
            results.add(RetrievalEvaluationV2.evaluate(evaluationCase, fixture, vectorCandidates, bm25Candidates,
                    new RrfFusionService(), unrestrictedVector, unrestrictedBm25));
        }

        Set<Long> candidateDocumentIds = candidateDocumentIds(results);
        Set<Long> nonFixtureCandidateIds = candidateDocumentIds.stream()
                .filter(documentId -> !EvaluationFixture.DOCUMENT_IDS.contains(documentId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertThat(nonFixtureCandidateIds).as("evaluation candidate leakage").isEmpty();
        RetrievalEvaluationV2.RunReport report = RetrievalEvaluationV2.report(runId, datasetPath, fixture, config, results,
                RetrievalEvaluationV2.corpusManifest(redisVector.indexName(), redisVector.keyPrefix(), elasticsearchIndex,
                        redisVector.keyCount(), fixture.chunks().size(), candidateDocumentIds, nonFixtureCandidateIds));
        Optional<RetrievalEvaluationV2.BaselineComparison> comparison = baselineComparison(report);
        Path reportDirectory = reportDirectory(runId);
        RetrievalEvaluationV2.writeReports(reportDirectory, report, comparison);

        assertThat(cases).hasSize(30);
        Map<EvaluationCategory, Long> categoryCounts = cases.stream()
                .collect(Collectors.groupingBy(EvaluationCase::category, Collectors.counting()));
        assertThat(categoryCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                EvaluationCategory.KEYWORD_EXACT, 4L,
                EvaluationCategory.SEMANTIC_PARAPHRASE, 5L,
                EvaluationCategory.MIXED, 5L,
                EvaluationCategory.AMBIGUOUS, 3L,
                EvaluationCategory.PERMISSION_SENSITIVE, 3L,
                EvaluationCategory.NO_ANSWER, 4L,
                EvaluationCategory.LEGACY_REGRESSION, 6L));
        assertThat(cases).extracting(EvaluationCase::id).contains(
                "legacy-06", "legacy-09", "legacy-11", "legacy-12", "legacy-13", "legacy-15");
        assertThat(report.overall()).containsKeys("VECTOR_ONLY", "BM25_ONLY", "HYBRID_RRF");
        assertThat(report.caseResults()).allSatisfy(result -> assertThat(result.vectorTopK()).allSatisfy(
                candidate -> assertThat(candidate.rank()).isPositive()));
        System.out.println("Retrieval Evaluation V2 report: " + reportDirectory.toAbsolutePath());
    }

    private void setup(String namespace) throws IOException {
        transport = new RestClientTransport(
                org.elasticsearch.client.RestClient.builder(new HttpHost("localhost", elasticsearchPort(), "http")).build(),
                new JacksonJsonpMapper());
        elasticsearchClient = new ElasticsearchClient(transport);
        elasticsearchIndex = namespace + "-es";
        elasticsearch = new ElasticsearchSearchService(elasticsearchClient, elasticsearchIndex);
        ReflectionTestUtils.setField(elasticsearch, "minBm25Score", doubleProperty("evaluation.min-bm25-score", 10.0));
        elasticsearch.createIndexIfAbsent();
        redisVector = new EvaluationRedisVectorSearch("localhost", redisPort(), namespace);
    }

    private RetrievalEvaluationV2.EvaluationConfig config() {
        return new RetrievalEvaluationV2.EvaluationConfig(
                intProperty("evaluation.candidate-limit", 20),
                doubleProperty("evaluation.min-vector-score", 0.72),
                doubleProperty("evaluation.min-bm25-score", 10.0));
    }

    private Set<Long> allowedDocumentIds(EvaluationPermissionContext context, EvaluationFixture fixture) {
        if (context == null || context.global()) return null;
        return context.allowedDocumentLogicalIds().stream().map(fixture::documentId).collect(java.util.stream.Collectors.toSet());
    }

    private Set<Long> candidateDocumentIds(List<RetrievalEvaluationV2.CaseResult> results) {
        return results.stream().flatMap(result -> Stream.of(result.vectorTopK(), result.bm25TopK(), result.hybridTopK()))
                .flatMap(List::stream).map(RetrievalEvaluationV2.CandidateView::documentId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Optional<RetrievalEvaluationV2.BaselineComparison> baselineComparison(RetrievalEvaluationV2.RunReport report)
            throws IOException {
        String value = System.getProperty("evaluation.baseline");
        Path baseline = value == null || value.isBlank()
                ? Path.of("target", "retrieval-evaluation-v2", "retrieval-v2-20260831-163555", "evaluation-report.json")
                : Path.of(value);
        return java.nio.file.Files.isRegularFile(baseline)
                ? Optional.of(RetrievalEvaluationV2.compare(report, baseline.toAbsolutePath().normalize()))
                : Optional.empty();
    }

    private Path datasetPath() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("evaluation/retrieval-evaluation-v2.json").toURI());
    }

    private Path reportDirectory(String runId) {
        String configured = System.getProperty("evaluation.output-dir");
        return configured == null || configured.isBlank()
                ? Path.of("target", "retrieval-evaluation-v2", runId)
                : Path.of(configured).resolve(runId);
    }

    private int elasticsearchPort() { return intProperty("evaluation.elasticsearch-port", 9200); }
    private int redisPort() { return intProperty("evaluation.redis-port", 6379); }
    private int intProperty(String key, int defaultValue) { return Integer.getInteger(key, defaultValue); }
    private double doubleProperty(String key, double defaultValue) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : Double.parseDouble(value);
    }
}
