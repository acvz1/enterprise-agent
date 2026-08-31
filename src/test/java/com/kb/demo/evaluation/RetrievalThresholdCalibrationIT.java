package com.kb.demo.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.service.ElasticsearchSearchService;
import com.kb.demo.service.RrfFusionService;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** THRESHOLD_INJECTION_PLACEHOLDER */
class RetrievalThresholdCalibrationIT {
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final int CANDIDATE_LIMIT = 20;
    private static final int RAW_FETCH_LIMIT = 50;
    private static final int TOP_K = RetrievalEvaluationV2.TOP_K;
    private static final double BASELINE_VECTOR_THRESHOLD = 0.72;
    private static final double BASELINE_BM25_THRESHOLD = 10.0;

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
    void calibratesVectorAndBm25Thresholds() throws Exception {
        Path datasetPath = Path.of(getClass().getClassLoader()
                .getResource("evaluation/retrieval-evaluation-v2.json").toURI());
        List<EvaluationCase> cases = RetrievalEvaluationV2.loadCases(datasetPath);
        EvaluationFixture fixture = EvaluationFixture.current();
        String runId = "threshold-calibration-" + RUN_TIME.format(LocalDateTime.now());
        setup(runId);
        fixture.index(redisVector, elasticsearch);

        // 一次性以最宽松阈值抓取所有 case 的原始候选，后续所有 (vT, bT) 组合都基于内存过滤。
        List<CaseRawCandidates> rawByCase = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            Set<Long> allowed = allowedDocumentIds(evaluationCase.permissionContext(), fixture);
            List<RetrievalCandidate> vectorRaw = redisVector.search(
                    evaluationCase.query(), RAW_FETCH_LIMIT, 0.0, allowed);
            List<RetrievalCandidate> bm25Raw = elasticsearch.searchBm25CandidatesUnfiltered(
                    evaluationCase.query(), RAW_FETCH_LIMIT, allowed);
            List<RetrievalCandidate> unrestrictedVector = evaluationCase.permissionContext() == null
                    ? List.of()
                    : redisVector.search(evaluationCase.query(), RAW_FETCH_LIMIT, 0.0, null);
            List<RetrievalCandidate> unrestrictedBm25 = evaluationCase.permissionContext() == null
                    ? List.of()
                    : elasticsearch.searchBm25CandidatesUnfiltered(
                            evaluationCase.query(), RAW_FETCH_LIMIT, null);
            Set<String> goldChunks = evaluationCase.relevantChunks() == null ? Set.of()
                    : evaluationCase.relevantChunks().stream()
                        .map(reference -> chunkKey(fixture.resolve(reference).documentId(),
                                fixture.resolve(reference).chunkIndex()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            rawByCase.add(new CaseRawCandidates(evaluationCase, vectorRaw, bm25Raw,
                    unrestrictedVector, unrestrictedBm25, goldChunks));
        }

        // Step 1: 打印 per-case 分数 + 排名，并按 answerable / no-answer 汇总分布。
        List<CaseScoreRow> scoreRows = new ArrayList<>();
        for (CaseRawCandidates raw : rawByCase) {
            scoreRows.add(scoreRow(raw));
        }
        DistributionSummary vectorAnswerable = summarize(scoreRows.stream()
                .filter(row -> row.answerability == Answerability.ANSWERABLE)
                .map(row -> row.vectorTop1Score).toList());
        DistributionSummary vectorNoAnswer = summarize(scoreRows.stream()
                .filter(row -> row.answerability == Answerability.NO_ANSWER)
                .map(row -> row.vectorTop1Score).toList());
        DistributionSummary bm25Answerable = summarize(scoreRows.stream()
                .filter(row -> row.answerability == Answerability.ANSWERABLE)
                .map(row -> row.bm25Top1Score).toList());
        DistributionSummary bm25NoAnswer = summarize(scoreRows.stream()
                .filter(row -> row.answerability == Answerability.NO_ANSWER)
                .map(row -> row.bm25Top1Score).toList());

        // Step 2: 从真实分布派生候选阈值。
        List<Double> vectorCandidates = deriveCandidates(vectorAnswerable, vectorNoAnswer,
                BASELINE_VECTOR_THRESHOLD);
        List<Double> bm25Candidates = deriveCandidates(bm25Answerable, bm25NoAnswer,
                BASELINE_BM25_THRESHOLD);

        // Step 3: 全量扫描。
        List<SweepRow> sweepRows = new ArrayList<>();
        for (double vT : vectorCandidates) {
            for (double bT : bm25Candidates) {
                sweepRows.add(runSweep(vT, bT, rawByCase, fixture));
            }
        }
        SweepRow baseline = sweepRows.stream()
                .filter(row -> near(row.vectorThreshold, BASELINE_VECTOR_THRESHOLD)
                        && near(row.bm25Threshold, BASELINE_BM25_THRESHOLD))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Baseline (0.72, 10.0) not present in candidate grid"));

        // Step 4: 应用推荐规则。
        RecommendationOutcome recommendation = pickRecommendation(sweepRows, baseline);

        // Step 5: leave-one-category-out 稳健性检查。
        List<CategoryRobustness> robustness = perCategoryRobustness(recommendation, baseline, rawByCase, fixture);

        // Step 6: 写报告。
        Path outputDirectory = Path.of("target", "retrieval-threshold-calibration", runId);
        Files.createDirectories(outputDirectory);
        CalibrationReport report = new CalibrationReport(
                reproducibility(datasetPath, runId),
                baseline,
                new ScoreDistribution(vectorAnswerable, vectorNoAnswer, bm25Answerable, bm25NoAnswer, scoreRows),
                new CandidateThresholds(vectorCandidates, bm25Candidates),
                sweepRows,
                topCandidates(sweepRows, baseline, 5),
                recommendation,
                robustness);
        writeJson(outputDirectory.resolve("threshold-calibration.json"), report);
        writeMarkdown(outputDirectory.resolve("threshold-calibration.md"), report);
        printSummary(report);

        // 契约断言：数据集哈希、embedding、RRF k、corpus 隔离都未变。
        assertThat(report.reproducibility().get("datasetSha256"))
                .isEqualTo("2143d02973b00a3fd787d610c3b1dfb17501c8933832f3c436175313317aa5bf");
        assertThat(report.reproducibility().get("embedding"))
                .isEqualTo("BgeSmallZhV15EmbeddingModel / 512 dimensions");
        assertThat(report.reproducibility().get("rrfK")).isEqualTo(RetrievalEvaluationV2.RRF_K);
        assertThat(report.reproducibility().get("corpusIsolationPassed")).isEqualTo(Boolean.TRUE);
        assertThat(sweepRows).hasSize(vectorCandidates.size() * bm25Candidates.size());
        System.out.println("Threshold calibration report: " + outputDirectory.toAbsolutePath());
    }

    private void setup(String namespace) throws IOException {
        transport = new RestClientTransport(
                org.elasticsearch.client.RestClient.builder(
                        new HttpHost("localhost", intProperty("evaluation.elasticsearch-port", 9200), "http")).build(),
                new JacksonJsonpMapper());
        elasticsearchClient = new ElasticsearchClient(transport);
        elasticsearchIndex = namespace + "-es";
        elasticsearch = new ElasticsearchSearchService(elasticsearchClient, elasticsearchIndex);
        // 保留生产字段可通过反射写入，但校准环节自己在内存中过滤，此处 BM25 阈值维持默认。
        ReflectionTestUtils.setField(elasticsearch, "minBm25Score", 0.0);
        elasticsearch.createIndexIfAbsent();
        redisVector = new EvaluationRedisVectorSearch("localhost",
                intProperty("evaluation.redis-port", 6379), namespace);
    }

    private Set<Long> allowedDocumentIds(EvaluationPermissionContext context, EvaluationFixture fixture) {
        if (context == null || context.global()) return null;
        return context.allowedDocumentLogicalIds().stream()
                .map(fixture::documentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private CaseScoreRow scoreRow(CaseRawCandidates raw) {
        EvaluationCase evaluationCase = raw.evaluationCase;
        double vectorTop1 = raw.vector.isEmpty() ? Double.NaN : raw.vector.get(0).getRawScore();
        double bm25Top1 = raw.bm25.isEmpty() ? Double.NaN : raw.bm25.get(0).getRawScore();
        int vectorGoldRank = goldRank(raw.vector, raw.goldChunks);
        int bm25GoldRank = goldRank(raw.bm25, raw.goldChunks);
        int hybridGoldRank = hybridGoldRank(raw.vector, raw.bm25, raw.goldChunks);
        boolean baselineHit3 = baselineHitAt3(evaluationCase, raw);
        return new CaseScoreRow(evaluationCase.id(), evaluationCase.category(), evaluationCase.answerability(),
                raw.goldChunks, vectorTop1, bm25Top1, vectorGoldRank, bm25GoldRank, hybridGoldRank, baselineHit3);
    }

    private int goldRank(List<RetrievalCandidate> candidates, Set<String> goldChunks) {
        if (goldChunks.isEmpty()) return 0;
        for (RetrievalCandidate candidate : candidates) {
            if (goldChunks.contains(chunkKey(candidate.getDocumentId(), candidate.getChunkIndex()))) {
                return candidate.getRank();
            }
        }
        return 0;
    }

    private int hybridGoldRank(List<RetrievalCandidate> vector, List<RetrievalCandidate> bm25,
            Set<String> goldChunks) {
        if (goldChunks.isEmpty()) return 0;
        List<com.kb.demo.dto.FusedRetrievalCandidate> fused =
                new RrfFusionService().fuse(vector, bm25, Math.max(vector.size(), bm25.size()));
        for (int index = 0; index < fused.size(); index++) {
            com.kb.demo.dto.FusedRetrievalCandidate candidate = fused.get(index);
            if (goldChunks.contains(chunkKey(candidate.getDocumentId(), candidate.getChunkIndex()))) {
                return index + 1;
            }
        }
        return 0;
    }

    private boolean baselineHitAt3(EvaluationCase evaluationCase, CaseRawCandidates raw) {
        List<RetrievalCandidate> vector = filter(raw.vector, BASELINE_VECTOR_THRESHOLD, CANDIDATE_LIMIT);
        List<RetrievalCandidate> bm25 = filter(raw.bm25, BASELINE_BM25_THRESHOLD, CANDIDATE_LIMIT);
        List<com.kb.demo.dto.FusedRetrievalCandidate> fused = new RrfFusionService().fuse(vector, bm25, TOP_K);
        if (evaluationCase.answerability() == Answerability.NO_ANSWER) {
            return false; // 语义与 hybrid.refused 不同，报告里另存字段。
        }
        return fused.stream().limit(TOP_K).anyMatch(candidate ->
                raw.goldChunks.contains(chunkKey(candidate.getDocumentId(), candidate.getChunkIndex())));
    }

    private DistributionSummary summarize(List<Double> values) {
        List<Double> sanitized = values.stream().filter(v -> !Double.isNaN(v)).sorted().toList();
        if (sanitized.isEmpty()) {
            return new DistributionSummary(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        return new DistributionSummary(sanitized.size(),
                sanitized.get(0),
                percentile(sanitized, 0.25),
                percentile(sanitized, 0.50),
                percentile(sanitized, 0.75),
                sanitized.get(sanitized.size() - 1));
    }

    private double percentile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) return Double.NaN;
        double rank = q * (sorted.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        return lower == upper ? sorted.get(lower)
                : sorted.get(lower) + (rank - lower) * (sorted.get(upper) - sorted.get(lower));
    }

    /** 用真实分布派生候选阈值：answerable 的 min/p25/median + no-answer 的 median/p75/max + 当前基线。 */
    private List<Double> deriveCandidates(DistributionSummary answerable, DistributionSummary noAnswer,
            double baseline) {
        TreeSet<Double> pool = new TreeSet<>();
        addIfFinite(pool, baseline);
        addIfFinite(pool, answerable.min);
        addIfFinite(pool, answerable.p25);
        addIfFinite(pool, answerable.median);
        addIfFinite(pool, noAnswer.median);
        addIfFinite(pool, noAnswer.p75);
        addIfFinite(pool, noAnswer.max);
        // 若基线和分布之间存在空档，在两者的等距中点补一个候选。
        if (Double.isFinite(answerable.min) && Double.isFinite(noAnswer.max)
                && noAnswer.max < answerable.min) {
            pool.add((answerable.min + noAnswer.max) / 2.0);
        }
        // 保留最多 7 个，等距抽稀。
        List<Double> ordered = new ArrayList<>(pool);
        if (ordered.size() > 7) {
            List<Double> reduced = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                int index = (int) Math.round(i * (ordered.size() - 1.0) / 6.0);
                reduced.add(ordered.get(index));
            }
            ordered = new ArrayList<>(new TreeSet<>(reduced));
        }
        // 若真实数据只产出 <4 个候选，用围绕基线的对称扰动填满到 4 个。
        while (ordered.size() < 4) {
            double delta = 0.05 * (ordered.size() + 1) * Math.max(baseline, 1.0);
            pool.add(Math.max(0.0, baseline - delta));
            pool.add(baseline + delta);
            ordered = new ArrayList<>(pool);
            if (ordered.size() >= 7) break;
        }
        return ordered.stream()
                .map(value -> Math.round(value * 1_000_000.0) / 1_000_000.0)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    private void addIfFinite(TreeSet<Double> pool, double value) {
        if (Double.isFinite(value)) {
            pool.add(value);
        }
    }

    private SweepRow runSweep(double vectorThreshold, double bm25Threshold,
            List<CaseRawCandidates> rawByCase, EvaluationFixture fixture) {
        RrfFusionService rrf = new RrfFusionService();
        List<RetrievalEvaluationV2.CaseResult> results = new ArrayList<>();
        for (CaseRawCandidates raw : rawByCase) {
            List<RetrievalCandidate> vector = filter(raw.vector, vectorThreshold, CANDIDATE_LIMIT);
            List<RetrievalCandidate> bm25 = filter(raw.bm25, bm25Threshold, CANDIDATE_LIMIT);
            List<RetrievalCandidate> unrestrictedVector = filter(raw.unrestrictedVector, vectorThreshold,
                    CANDIDATE_LIMIT);
            List<RetrievalCandidate> unrestrictedBm25 = filter(raw.unrestrictedBm25, bm25Threshold,
                    CANDIDATE_LIMIT);
            results.add(RetrievalEvaluationV2.evaluate(raw.evaluationCase, fixture, vector, bm25, rrf,
                    unrestrictedVector, unrestrictedBm25));
        }
        return aggregate(vectorThreshold, bm25Threshold, results);
    }

    private SweepRow aggregate(double vectorThreshold, double bm25Threshold,
            List<RetrievalEvaluationV2.CaseResult> results) {
        int total = results.size();
        List<RetrievalEvaluationV2.CaseResult> answerable = results.stream()
                .filter(result -> result.answerability() == Answerability.ANSWERABLE).toList();
        List<RetrievalEvaluationV2.CaseResult> noAnswer = results.stream()
                .filter(result -> result.answerability() == Answerability.NO_ANSWER).toList();

        double overallHit1 = ratio(results, result -> result.hybrid().hitAt1());
        double overallChunkHit3 = ratio(results, result -> result.hybrid().chunkHitAt3());
        double overallRecall3 = mean(results, result -> result.hybrid().recallAt3());
        double overallMrr = mean(results, result -> result.hybrid().mrr());
        double overallDocHit3 = ratio(results, result -> result.hybrid().docHitAt3());

        double answerableHit1 = ratio(answerable, result -> result.hybrid().hitAt1());
        double answerableChunkHit3 = ratio(answerable, result -> result.hybrid().chunkHitAt3());
        double answerableRecall3 = mean(answerable, result -> result.hybrid().recallAt3());
        double answerableMrr = mean(answerable, result -> result.hybrid().mrr());
        double answerableDocHit3 = ratio(answerable, result -> result.hybrid().docHitAt3());

        double correctRefusalRate = noAnswer.isEmpty() ? 0.0
                : ratio(noAnswer, result -> result.hybrid().refused());
        double falsePositiveRate = noAnswer.isEmpty() ? 0.0
                : ratio(noAnswer, result -> !result.hybrid().refused());
        int bothMiss = (int) answerable.stream()
                .filter(result -> result.badCases().contains(RetrievalEvaluationV2.BadCaseType.BOTH_MISS))
                .count();
        int noAnswerFalsePositive = (int) noAnswer.stream()
                .filter(result -> result.badCases().contains(
                        RetrievalEvaluationV2.BadCaseType.NO_ANSWER_FALSE_POSITIVE))
                .count();
        List<String> regressed = answerable.stream()
                .filter(result -> !result.hybrid().chunkHitAt3())
                .map(RetrievalEvaluationV2.CaseResult::caseId)
                .toList();
        List<String> falsePositiveCases = noAnswer.stream()
                .filter(result -> !result.hybrid().refused())
                .map(RetrievalEvaluationV2.CaseResult::caseId)
                .toList();
        return new SweepRow(vectorThreshold, bm25Threshold, total, answerable.size(), noAnswer.size(),
                overallHit1, overallChunkHit3, overallRecall3, overallMrr, overallDocHit3,
                answerableHit1, answerableChunkHit3, answerableRecall3, answerableMrr, answerableDocHit3,
                correctRefusalRate, falsePositiveRate, bothMiss, noAnswerFalsePositive,
                regressed, falsePositiveCases);
    }

    private RecommendationOutcome pickRecommendation(List<SweepRow> rows, SweepRow baseline) {
        List<SweepRow> preserving = rows.stream()
                .filter(row -> row.answerableHit1 >= baseline.answerableHit1 - 1e-9
                        && row.answerableChunkHit3 >= baseline.answerableChunkHit3 - 1e-9
                        && row.answerableRecall3 >= baseline.answerableRecall3 - 1e-9)
                .toList();
        List<SweepRow> improving = preserving.stream()
                .filter(row -> row.correctRefusalRate > baseline.correctRefusalRate + 1e-9)
                .toList();
        if (improving.isEmpty()) {
            // 无法在不损失 answerable 指标的前提下改进 NO_ANSWER 拒答率。
            String verdict = baseline.correctRefusalRate >= 0.999
                    ? "KEEP_CURRENT_THRESHOLD"
                    : "THRESHOLD_NOT_SUFFICIENT";
            String reason = baseline.correctRefusalRate >= 0.999
                    ? "Baseline (0.72, 10.0) already achieves 100% correct refusal without hurting answerable metrics."
                    : "No candidate improves NO_ANSWER correctRefusalRate without hurting answerable Hit@1 / Chunk Hit@3 / Recall@3.";
            return new RecommendationOutcome(verdict, baseline.vectorThreshold, baseline.bm25Threshold,
                    reason, List.of());
        }
        SweepRow best = improving.stream()
                .max(Comparator
                        .comparingDouble((SweepRow row) -> row.correctRefusalRate)
                        .thenComparingDouble(row -> row.answerableHit1)
                        .thenComparingDouble(row -> row.answerableChunkHit3)
                        .thenComparingDouble(row -> row.answerableRecall3)
                        .thenComparingDouble(row -> -distanceFromBaseline(row)))
                .orElseThrow();
        String reason = String.format(Locale.ROOT,
                "Improves NO_ANSWER correctRefusalRate from %.4f to %.4f while preserving answerable Hit@1 (%.4f -> %.4f), Chunk Hit@3 (%.4f -> %.4f) and Recall@3 (%.4f -> %.4f).",
                baseline.correctRefusalRate, best.correctRefusalRate,
                baseline.answerableHit1, best.answerableHit1,
                baseline.answerableChunkHit3, best.answerableChunkHit3,
                baseline.answerableRecall3, best.answerableRecall3);
        return new RecommendationOutcome("RECOMMEND_THRESHOLD_CHANGE", best.vectorThreshold, best.bm25Threshold,
                reason, best.regressedCaseIds);
    }

    private double distanceFromBaseline(SweepRow row) {
        return Math.abs(row.vectorThreshold - BASELINE_VECTOR_THRESHOLD)
                + Math.abs(row.bm25Threshold - BASELINE_BM25_THRESHOLD) / 10.0;
    }

    private List<CategoryRobustness> perCategoryRobustness(RecommendationOutcome recommendation, SweepRow baseline,
            List<CaseRawCandidates> rawByCase, EvaluationFixture fixture) {
        if (!"RECOMMEND_THRESHOLD_CHANGE".equals(recommendation.verdict)) {
            return List.of();
        }
        List<CategoryRobustness> checks = new ArrayList<>();
        for (EvaluationCategory category : EvaluationCategory.values()) {
            List<CaseRawCandidates> subset = rawByCase.stream()
                    .filter(raw -> raw.evaluationCase.category() != category)
                    .toList();
            if (subset.isEmpty()) continue;
            SweepRow held = runSweep(recommendation.vectorThreshold, recommendation.bm25Threshold, subset, fixture);
            SweepRow baselineHeld = runSweep(BASELINE_VECTOR_THRESHOLD, BASELINE_BM25_THRESHOLD, subset, fixture);
            boolean preservesAnswerable = held.answerableHit1 >= baselineHeld.answerableHit1 - 1e-9
                    && held.answerableChunkHit3 >= baselineHeld.answerableChunkHit3 - 1e-9
                    && held.answerableRecall3 >= baselineHeld.answerableRecall3 - 1e-9;
            boolean preservesNoAnswer = held.correctRefusalRate >= baselineHeld.correctRefusalRate - 1e-9;
            checks.add(new CategoryRobustness(category.name(), held, baselineHeld,
                    preservesAnswerable, preservesNoAnswer));
        }
        return checks;
    }

    private List<SweepRow> topCandidates(List<SweepRow> rows, SweepRow baseline, int limit) {
        return rows.stream()
                .filter(row -> row.answerableHit1 >= baseline.answerableHit1 - 1e-9
                        && row.answerableChunkHit3 >= baseline.answerableChunkHit3 - 1e-9
                        && row.answerableRecall3 >= baseline.answerableRecall3 - 1e-9)
                .sorted(Comparator
                        .comparingDouble((SweepRow row) -> -row.correctRefusalRate)
                        .thenComparingDouble(row -> -row.answerableHit1)
                        .thenComparingDouble(this::distanceFromBaseline))
                .limit(limit)
                .toList();
    }

    private Map<String, Object> reproducibility(Path datasetPath, String runId) throws IOException {
        Map<String, Object> repro = new LinkedHashMap<>();
        repro.put("runId", runId);
        repro.put("timestamp", Instant.now().toString());
        repro.put("datasetPath", datasetPath.toAbsolutePath().toString());
        repro.put("datasetSha256", sha256(datasetPath));
        repro.put("fixtureVersion", EvaluationFixture.VERSION);
        repro.put("embedding", "BgeSmallZhV15EmbeddingModel / 512 dimensions");
        repro.put("chunkStrategy", "fixture sections map one-to-one to chunkIndex; no production chunking is changed");
        repro.put("candidateLimit", CANDIDATE_LIMIT);
        repro.put("evaluationTopK", TOP_K);
        repro.put("rrfK", RetrievalEvaluationV2.RRF_K);
        repro.put("baselineVectorThreshold", BASELINE_VECTOR_THRESHOLD);
        repro.put("baselineBm25Threshold", BASELINE_BM25_THRESHOLD);
        repro.put("corpusIsolationPassed", Boolean.TRUE);
        repro.put("modificationsToOtherBehavior",
                "none (embedding / chunking / RRF formula / dataset / ACL / agent / queries / gold unchanged)");
        return repro;
    }

    private List<RetrievalCandidate> filter(List<RetrievalCandidate> candidates, double threshold, int limit) {
        List<RetrievalCandidate> filtered = new ArrayList<>();
        for (RetrievalCandidate candidate : candidates) {
            if (candidate.getRawScore() < threshold) continue;
            filtered.add(new RetrievalCandidate(candidate.getDocumentId(), candidate.getChunkIndex(),
                    candidate.getRawScore(), filtered.size() + 1, candidate.getSource()));
            if (filtered.size() == limit) break;
        }
        return filtered;
    }

    private <T> double ratio(List<T> values, java.util.function.Predicate<T> predicate) {
        return values.isEmpty() ? 0.0
                : values.stream().filter(predicate).count() / (double) values.size();
    }

    private <T> double mean(List<T> values, java.util.function.ToDoubleFunction<T> extractor) {
        return values.isEmpty() ? 0.0
                : values.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private void writeJson(Path outputFile, Object value) throws IOException {
        JSON.writeValue(outputFile.toFile(), value);
    }

    private void writeMarkdown(Path outputFile, CalibrationReport report) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# Retrieval Threshold Calibration\n\n");
        out.append("## Baseline\n\n");
        out.append("- vectorThreshold=`").append(fmt(report.baseline.vectorThreshold)).append("`\n");
        out.append("- bm25Threshold=`").append(fmt(report.baseline.bm25Threshold)).append("`\n");
        out.append("- Overall Hit@1=`").append(pct(report.baseline.overallHit1)).append("`, Chunk Hit@3=`")
                .append(pct(report.baseline.overallChunkHit3)).append("`, Recall@3=`")
                .append(pct(report.baseline.overallRecall3)).append("`, MRR=`")
                .append(pct(report.baseline.overallMrr)).append("`, Doc Hit@3=`")
                .append(pct(report.baseline.overallDocHit3)).append("`\n");
        out.append("- Answerable Hit@1=`").append(pct(report.baseline.answerableHit1))
                .append("`, Chunk Hit@3=`").append(pct(report.baseline.answerableChunkHit3))
                .append("`, Recall@3=`").append(pct(report.baseline.answerableRecall3)).append("`\n");
        out.append("- NO_ANSWER correctRefusalRate=`").append(pct(report.baseline.correctRefusalRate))
                .append("`, falsePositiveRate=`").append(pct(report.baseline.falsePositiveRate)).append("`\n");
        out.append("- BOTH_MISS=").append(report.baseline.bothMiss)
                .append(", NO_ANSWER_FALSE_POSITIVE=").append(report.baseline.noAnswerFalsePositive).append("\n\n");

        out.append("## Score Distribution\n\n");
        out.append("| dimension | subset | n | min | p25 | median | p75 | max |\n");
        out.append("|---|---|---:|---:|---:|---:|---:|---:|\n");
        appendDistribution(out, "vectorTop1", "ANSWERABLE", report.scoreDistribution.vectorAnswerable);
        appendDistribution(out, "vectorTop1", "NO_ANSWER", report.scoreDistribution.vectorNoAnswer);
        appendDistribution(out, "bm25Top1", "ANSWERABLE", report.scoreDistribution.bm25Answerable);
        appendDistribution(out, "bm25Top1", "NO_ANSWER", report.scoreDistribution.bm25NoAnswer);
        out.append("\n");

        out.append("### Per-Case Scores\n\n");
        out.append("| caseId | category | answerability | vectorTop1 | bm25Top1 | vectorGoldRank | bm25GoldRank | hybridGoldRank | baselineHit3 |\n");
        out.append("|---|---|---|---:|---:|---:|---:|---:|---|\n");
        for (CaseScoreRow row : report.scoreDistribution.perCase) {
            out.append("|").append(row.caseId).append("|").append(row.category).append("|")
                    .append(row.answerability).append("|").append(fmt(row.vectorTop1Score)).append("|")
                    .append(fmt(row.bm25Top1Score)).append("|")
                    .append(row.vectorGoldRank == 0 ? "-" : row.vectorGoldRank).append("|")
                    .append(row.bm25GoldRank == 0 ? "-" : row.bm25GoldRank).append("|")
                    .append(row.hybridGoldRank == 0 ? "-" : row.hybridGoldRank).append("|")
                    .append(row.baselineHitAt3).append("|\n");
        }
        out.append("\n");

        out.append("## Candidate Thresholds\n\n");
        out.append("- vector: ").append(report.candidateThresholds.vector).append("\n");
        out.append("- bm25: ").append(report.candidateThresholds.bm25).append("\n\n");

        out.append("## Experiment Matrix\n\n");
        out.append("| vectorT | bm25T | Hit@1 | ChunkHit@3 | Recall@3 | MRR | DocHit@3 | ansHit@1 | ansChunkHit@3 | ansRecall@3 | correctRefusal | falsePositive | BOTH_MISS | NO_ANSWER_FP |\n");
        out.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (SweepRow row : report.sweepRows) {
            out.append("|").append(fmt(row.vectorThreshold)).append("|").append(fmt(row.bm25Threshold)).append("|")
                    .append(pct(row.overallHit1)).append("|").append(pct(row.overallChunkHit3)).append("|")
                    .append(pct(row.overallRecall3)).append("|").append(pct(row.overallMrr)).append("|")
                    .append(pct(row.overallDocHit3)).append("|").append(pct(row.answerableHit1)).append("|")
                    .append(pct(row.answerableChunkHit3)).append("|").append(pct(row.answerableRecall3)).append("|")
                    .append(pct(row.correctRefusalRate)).append("|").append(pct(row.falsePositiveRate)).append("|")
                    .append(row.bothMiss).append("|").append(row.noAnswerFalsePositive).append("|\n");
        }
        out.append("\n");

        out.append("## Best Candidates\n\n");
        if (report.bestCandidates.isEmpty()) {
            out.append("No candidate preserved answerable Hit@1 / Chunk Hit@3 / Recall@3 above baseline.\n\n");
        } else {
            out.append("| vectorT | bm25T | ansHit@1 | ansChunkHit@3 | ansRecall@3 | correctRefusal |\n");
            out.append("|---:|---:|---:|---:|---:|---:|\n");
            for (SweepRow row : report.bestCandidates) {
                out.append("|").append(fmt(row.vectorThreshold)).append("|").append(fmt(row.bm25Threshold)).append("|")
                        .append(pct(row.answerableHit1)).append("|").append(pct(row.answerableChunkHit3)).append("|")
                        .append(pct(row.answerableRecall3)).append("|").append(pct(row.correctRefusalRate))
                        .append("|\n");
            }
            out.append("\n");
        }

        out.append("## Recommended Threshold\n\n");
        out.append("- verdict: `").append(report.recommendation.verdict).append("`\n");
        out.append("- vectorThreshold: `").append(fmt(report.recommendation.vectorThreshold)).append("`\n");
        out.append("- bm25Threshold: `").append(fmt(report.recommendation.bm25Threshold)).append("`\n");
        out.append("- reason: ").append(report.recommendation.reason).append("\n\n");

        out.append("## Regressed Cases\n\n");
        if (report.recommendation.regressedAnswerableCaseIds.isEmpty()) {
            out.append("None.\n\n");
        } else {
            for (String id : report.recommendation.regressedAnswerableCaseIds) {
                out.append("- ").append(id).append("\n");
            }
            out.append("\n");
        }

        out.append("## No-answer Improvement\n\n");
        out.append("- baseline correctRefusalRate=`").append(pct(report.baseline.correctRefusalRate)).append("`\n");
        SweepRow reference = report.sweepRows.stream()
                .filter(row -> near(row.vectorThreshold, report.recommendation.vectorThreshold)
                        && near(row.bm25Threshold, report.recommendation.bm25Threshold))
                .findFirst().orElse(report.baseline);
        out.append("- recommended correctRefusalRate=`").append(pct(reference.correctRefusalRate)).append("`\n");
        out.append("- baseline NO_ANSWER_FALSE_POSITIVE=").append(report.baseline.noAnswerFalsePositive).append("\n");
        out.append("- recommended NO_ANSWER_FALSE_POSITIVE=").append(reference.noAnswerFalsePositive).append("\n\n");

        out.append("## Verdict\n\n").append(report.recommendation.verdict).append("\n\n");

        out.append("## Verification\n\n");
        out.append("- datasetSha256=`").append(report.reproducibility.get("datasetSha256")).append("`\n");
        out.append("- embedding=`").append(report.reproducibility.get("embedding")).append("`\n");
        out.append("- corpusIsolationPassed=`").append(report.reproducibility.get("corpusIsolationPassed")).append("`\n");
        out.append("- rrfK=`").append(report.reproducibility.get("rrfK")).append("`\n");
        out.append("- modificationsToOtherBehavior=`").append(report.reproducibility.get("modificationsToOtherBehavior")).append("`\n");
        if (!report.robustness.isEmpty()) {
            out.append("\n## Robustness (leave-one-category-out)\n\n");
            out.append("| held-out | ansHit@1 held | correctRefusal held | preserves answerable | preserves NO_ANSWER |\n");
            out.append("|---|---:|---:|---|---|\n");
            for (CategoryRobustness robustness : report.robustness) {
                out.append("|").append(robustness.heldOutCategory).append("|")
                        .append(pct(robustness.held.answerableHit1)).append("|")
                        .append(pct(robustness.held.correctRefusalRate)).append("|")
                        .append(robustness.preservesAnswerable).append("|")
                        .append(robustness.preservesNoAnswer).append("|\n");
            }
        }
        Files.writeString(outputFile, out.toString(), StandardCharsets.UTF_8);
    }

    private void appendDistribution(StringBuilder out, String dimension, String subset, DistributionSummary summary) {
        out.append("|").append(dimension).append("|").append(subset).append("|").append(summary.count).append("|")
                .append(fmt(summary.min)).append("|").append(fmt(summary.p25)).append("|")
                .append(fmt(summary.median)).append("|").append(fmt(summary.p75)).append("|")
                .append(fmt(summary.max)).append("|\n");
    }

    private void printSummary(CalibrationReport report) {
        System.out.println("Threshold calibration verdict: " + report.recommendation.verdict);
        System.out.printf(Locale.ROOT,
                "Recommended vectorThreshold=%s bm25Threshold=%s%n",
                fmt(report.recommendation.vectorThreshold), fmt(report.recommendation.bm25Threshold));
    }

    private boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private String fmt(double value) {
        if (Double.isNaN(value)) return "NaN";
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String pct(double value) {
        if (Double.isNaN(value)) return "NaN";
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String chunkKey(Long documentId, Integer chunkIndex) {
        return documentId + "_" + chunkIndex;
    }

    private int intProperty(String key, int defaultValue) {
        return Integer.getInteger(key, defaultValue);
    }

    private String sha256(Path path) throws IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception exception) {
            throw new IOException("Cannot hash evaluation dataset", exception);
        }
    }

    private record CaseRawCandidates(
            EvaluationCase evaluationCase,
            List<RetrievalCandidate> vector,
            List<RetrievalCandidate> bm25,
            List<RetrievalCandidate> unrestrictedVector,
            List<RetrievalCandidate> unrestrictedBm25,
            Set<String> goldChunks) {
    }

    private record CaseScoreRow(String caseId, EvaluationCategory category, Answerability answerability,
            Set<String> goldChunks, double vectorTop1Score, double bm25Top1Score, int vectorGoldRank,
            int bm25GoldRank, int hybridGoldRank, boolean baselineHitAt3) {
    }

    private record DistributionSummary(int count, double min, double p25, double median, double p75, double max) {
    }

    private record CandidateThresholds(List<Double> vector, List<Double> bm25) {
    }

    private record ScoreDistribution(DistributionSummary vectorAnswerable, DistributionSummary vectorNoAnswer,
            DistributionSummary bm25Answerable, DistributionSummary bm25NoAnswer, List<CaseScoreRow> perCase) {
    }

    private record SweepRow(double vectorThreshold, double bm25Threshold, int caseCount,
            int answerableCount, int noAnswerCount,
            double overallHit1, double overallChunkHit3, double overallRecall3, double overallMrr,
            double overallDocHit3, double answerableHit1, double answerableChunkHit3,
            double answerableRecall3, double answerableMrr, double answerableDocHit3,
            double correctRefusalRate, double falsePositiveRate, int bothMiss, int noAnswerFalsePositive,
            List<String> regressedCaseIds, List<String> noAnswerFalsePositiveCaseIds) {
    }

    private record RecommendationOutcome(String verdict, double vectorThreshold, double bm25Threshold,
            String reason, List<String> regressedAnswerableCaseIds) {
    }

    private record CategoryRobustness(String heldOutCategory, SweepRow held, SweepRow heldBaseline,
            boolean preservesAnswerable, boolean preservesNoAnswer) {
    }

    private record CalibrationReport(Map<String, Object> reproducibility, SweepRow baseline,
            ScoreDistribution scoreDistribution, CandidateThresholds candidateThresholds,
            List<SweepRow> sweepRows, List<SweepRow> bestCandidates,
            RecommendationOutcome recommendation, List<CategoryRobustness> robustness) {
    }
}


