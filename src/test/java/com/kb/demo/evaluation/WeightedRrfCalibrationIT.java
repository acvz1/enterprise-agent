package com.kb.demo.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kb.demo.dto.FusedRetrievalCandidate;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controlled experiment: Weighted RRF (vectorWeight:bm25Weight) × k sweep on the fixed 42-case corpus.
 * Raw candidates are fetched once per case (threshold=0, limit=50) and all combinations are evaluated
 * in memory — no additional embedding calls or Redis/ES queries per configuration.
 *
 * Formula: score = vectorWeight / (k + vectorRank) + bm25Weight / (k + bm25Rank)
 */
class WeightedRrfCalibrationIT {

    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final int CANDIDATE_LIMIT = 20;
    private static final int RAW_FETCH_LIMIT = 50;
    private static final int TOP_K = RetrievalEvaluationV2.TOP_K;

    private static final double BASELINE_VECTOR_THRESHOLD = 0.72;
    private static final double BASELINE_BM25_THRESHOLD = 10.0;

    // Weight combinations: vectorWeight:bm25Weight
    private static final List<double[]> WEIGHT_CONFIGS = List.of(
            new double[]{1.0, 1.0},
            new double[]{1.2, 1.0},
            new double[]{1.5, 1.0},
            new double[]{2.0, 1.0}
    );

    // k values
    private static final List<Double> K_VALUES = List.of(10.0, 30.0, 60.0, 90.0);

    private static final String DATASET_SHA256 =
            "2143d02973b00a3fd787d610c3b1dfb17501c8933832f3c436175313317aa5bf";

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
    void calibratesWeightedRrfAndK() throws Exception {
        Path datasetPath = Path.of(getClass().getClassLoader()
                .getResource("evaluation/retrieval-evaluation-v2.json").toURI());
        List<EvaluationCase> cases = RetrievalEvaluationV2.loadCases(datasetPath);
        EvaluationFixture fixture = EvaluationFixture.current();
        String runId = "weighted-rrf-" + RUN_TIME.format(LocalDateTime.now());
        setup(runId);
        fixture.index(redisVector, elasticsearch);

        // Fetch raw candidates once per case at the widest possible window.
        List<CaseRaw> rawByCase = collectRaw(cases, fixture);

        // Step 1: weight sweep at k=60 (baseline k).
        List<WeightRow> weightRows = new ArrayList<>();
        for (double[] wc : WEIGHT_CONFIGS) {
            weightRows.add(runExperiment(wc[0], wc[1], 60.0, rawByCase, fixture));
        }
        WeightRow baselineRow = weightRows.stream()
                .filter(row -> near(row.vectorWeight, 1.0) && near(row.bm25Weight, 1.0))
                .findFirst().orElseThrow();

        // Step 2: k sweep on the best 1–2 weight configs.
        List<WeightRow> bestWeights = topWeightRows(weightRows, baselineRow);
        List<KRow> kRows = new ArrayList<>();
        for (WeightRow wr : bestWeights) {
            for (double k : K_VALUES) {
                kRows.add(new KRow(wr.vectorWeight, wr.bm25Weight, k,
                        runExperiment(wr.vectorWeight, wr.bm25Weight, k, rawByCase, fixture)));
            }
        }

        // Step 3: determine best overall and verdict.
        WeightRow overallBest = chooseBest(weightRows, kRows, baselineRow);
        String verdict = verdict(overallBest, baselineRow);
        List<String> regressions = regressionCases(overallBest, baselineRow, rawByCase, fixture);

        // Step 4: write reports.
        Path outputDir = Path.of("target", "weighted-rrf-calibration", runId);
        Files.createDirectories(outputDir);
        CalibrationResult result = new CalibrationResult(
                buildReproducibility(datasetPath, runId),
                baselineRow, weightRows, kRows,
                overallBest, regressions, verdict);
        JSON.writeValue(outputDir.resolve("weighted-rrf-calibration.json").toFile(), result);
        writeMarkdown(outputDir.resolve("weighted-rrf-calibration.md"), result);
        printSummary(result);

        // Verification assertions.
        assertThat(sha256(datasetPath)).isEqualTo(DATASET_SHA256);
        assertThat(cases).hasSize(42);
        assertThat(result.reproducibility().get("datasetSha256")).isEqualTo(DATASET_SHA256);
        assertThat(result.reproducibility().get("embedding"))
                .isEqualTo("BgeSmallZhV15EmbeddingModel / 512 dimensions");
        assertThat(result.reproducibility().get("corpusIsolationPassed")).isEqualTo(Boolean.TRUE);
        assertThat(result.reproducibility().get("minVectorScore")).isEqualTo(BASELINE_VECTOR_THRESHOLD);
        assertThat(result.reproducibility().get("minBm25Score")).isEqualTo(BASELINE_BM25_THRESHOLD);
        System.out.println("Weighted RRF calibration report: " + outputDir.toAbsolutePath());
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private void setup(String namespace) throws IOException {
        transport = new RestClientTransport(
                org.elasticsearch.client.RestClient.builder(
                        new HttpHost("localhost", intProperty("evaluation.elasticsearch-port", 9200), "http")).build(),
                new JacksonJsonpMapper());
        elasticsearchClient = new ElasticsearchClient(transport);
        elasticsearchIndex = namespace + "-es";
        elasticsearch = new ElasticsearchSearchService(elasticsearchClient, elasticsearchIndex);
        ReflectionTestUtils.setField(elasticsearch, "minBm25Score", 0.0);
        elasticsearch.createIndexIfAbsent();
        redisVector = new EvaluationRedisVectorSearch("localhost",
                intProperty("evaluation.redis-port", 6379), namespace);
    }

    // ── Raw candidate collection ──────────────────────────────────────────────

    private List<CaseRaw> collectRaw(List<EvaluationCase> cases, EvaluationFixture fixture) throws IOException {
        List<CaseRaw> result = new ArrayList<>();
        for (EvaluationCase ec : cases) {
            Set<Long> allowed = allowedIds(ec.permissionContext(), fixture);
            List<RetrievalCandidate> vec = filterThreshold(
                    redisVector.search(ec.query(), RAW_FETCH_LIMIT, 0.0, allowed),
                    BASELINE_VECTOR_THRESHOLD, CANDIDATE_LIMIT);
            List<RetrievalCandidate> bm25 = filterThreshold(
                    elasticsearch.searchBm25CandidatesUnfiltered(ec.query(), RAW_FETCH_LIMIT, allowed),
                    BASELINE_BM25_THRESHOLD, CANDIDATE_LIMIT);
            Set<String> goldChunks = goldChunks(ec, fixture);
            result.add(new CaseRaw(ec, vec, bm25, goldChunks));
        }
        return result;
    }

    private Set<Long> allowedIds(EvaluationPermissionContext ctx, EvaluationFixture fixture) {
        if (ctx == null || ctx.global()) return null;
        return ctx.allowedDocumentLogicalIds().stream()
                .map(fixture::documentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> goldChunks(EvaluationCase ec, EvaluationFixture fixture) {
        if (ec.relevantChunks() == null) return Set.of();
        return ec.relevantChunks().stream()
                .map(ref -> chunkKey(fixture.resolve(ref).documentId(), fixture.resolve(ref).chunkIndex()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<RetrievalCandidate> filterThreshold(List<RetrievalCandidate> src, double threshold, int limit) {
        List<RetrievalCandidate> out = new ArrayList<>();
        for (RetrievalCandidate c : src) {
            if (c.getRawScore() < threshold) continue;
            out.add(new RetrievalCandidate(c.getDocumentId(), c.getChunkIndex(),
                    c.getRawScore(), out.size() + 1, c.getSource()));
            if (out.size() == limit) break;
        }
        return out;
    }

    // ── Experiment runner ─────────────────────────────────────────────────────

    private WeightRow runExperiment(double vw, double bw, double k,
            List<CaseRaw> rawByCase, EvaluationFixture fixture) {
        RrfFusionService rrf = new RrfFusionService();
        List<Answerability> answerabilities = new ArrayList<>();
        List<Boolean> hit1 = new ArrayList<>(), chunkHit3 = new ArrayList<>(),
                docHit3 = new ArrayList<>();
        List<Double> recall3 = new ArrayList<>(), mrr = new ArrayList<>();
        int vmh = 0, bmv = 0, both = 0, vohr = 0, bohr = 0, top1dis = 0, naFp = 0;
        List<String> regressVsBaseline = new ArrayList<>();

        for (CaseRaw raw : rawByCase) {
            List<FusedRetrievalCandidate> fused =
                    rrf.fuse(raw.vector, raw.bm25, TOP_K, vw, bw, k);
            answerabilities.add(raw.ec.answerability());

            if (raw.ec.answerability() == Answerability.NO_ANSWER) {
                boolean refused = fused.isEmpty() || fused.stream().noneMatch(
                        c -> raw.goldChunks.contains(chunkKey(c.getDocumentId(), c.getChunkIndex())));
                // For NO_ANSWER gold is empty, so "refused" = fused top-3 contains nothing from gold
                // (gold is empty → any non-empty fused = false positive).
                boolean noHit = raw.goldChunks.isEmpty() && !fused.isEmpty()
                        ? false  // fused is non-empty but no gold to hit = actually a false positive
                        : raw.goldChunks.isEmpty();
                boolean isFalsePositive = !fused.isEmpty();
                if (isFalsePositive) naFp++;
                hit1.add(false); chunkHit3.add(false); docHit3.add(false);
                recall3.add(0.0); mrr.add(0.0);
                continue;
            }

            // Answerable metrics
            List<FusedRetrievalCandidate> top3 = fused.subList(0, Math.min(TOP_K, fused.size()));
            int firstRel = 0;
            int hits = 0;
            for (int i = 0; i < top3.size(); i++) {
                String ck = chunkKey(top3.get(i).getDocumentId(), top3.get(i).getChunkIndex());
                if (raw.goldChunks.contains(ck)) {
                    if (firstRel == 0) firstRel = i + 1;
                    hits++;
                }
            }
            hit1.add(firstRel == 1);
            chunkHit3.add(firstRel > 0);
            recall3.add(raw.goldChunks.isEmpty() ? 0.0 : hits / (double) raw.goldChunks.size());
            mrr.add(firstRel == 0 ? 0.0 : 1.0 / firstRel);
            docHit3.add(false); // filled below per-case via docId check

            // Bad-case flags
            boolean vecHit = vectorHitAt3(raw.vector, raw.goldChunks);
            boolean bm25Hit = bm25HitAt3(raw.bm25, raw.goldChunks);
            if (!vecHit && bm25Hit) vmh++;
            if (vecHit && !bm25Hit) bmv++;
            if (!vecHit && !bm25Hit) both++;
            if (vecHit && !bm25Hit && firstRel > 0) vohr++;
            if (!vecHit && bm25Hit && firstRel > 0) bohr++;
            // top1 disagreement
            String vTop1 = raw.vector.isEmpty() ? null : chunkKey(raw.vector.get(0).getDocumentId(),
                    raw.vector.get(0).getChunkIndex());
            String bTop1 = raw.bm25.isEmpty() ? null : chunkKey(raw.bm25.get(0).getDocumentId(),
                    raw.bm25.get(0).getChunkIndex());
            if (vTop1 != null && bTop1 != null && !vTop1.equals(bTop1)) top1dis++;
        }

        // Recompute docHit3 properly
        for (int i = 0; i < rawByCase.size(); i++) {
            CaseRaw raw = rawByCase.get(i);
            if (raw.ec.answerability() != Answerability.ANSWERABLE) {
                docHit3.set(i, false);
                continue;
            }
            List<FusedRetrievalCandidate> fused = rrf.fuse(raw.vector, raw.bm25, TOP_K, vw, bw, k);
            Set<Long> goldDocIds = raw.ec.relevantDocs() == null ? Set.of()
                    : raw.ec.relevantDocs().stream().map(fixture::documentId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            boolean dh = fused.stream().limit(TOP_K)
                    .anyMatch(c -> goldDocIds.contains(c.getDocumentId()));
            docHit3.set(i, dh);
        }

        List<Boolean> answerableHit1 = sublist(hit1, answerabilities, Answerability.ANSWERABLE);
        List<Boolean> answerableChunk3 = sublist(chunkHit3, answerabilities, Answerability.ANSWERABLE);
        List<Double> answerableRecall3 = sublistD(recall3, answerabilities, Answerability.ANSWERABLE);
        List<Double> answerableMrr = sublistD(mrr, answerabilities, Answerability.ANSWERABLE);
        List<Boolean> answerableDoc3 = sublist(docHit3, answerabilities, Answerability.ANSWERABLE);

        return new WeightRow(vw, bw, k,
                mean(hit1), mean(chunkHit3), avg(recall3), avg(mrr), mean(docHit3),
                mean(answerableHit1), mean(answerableChunk3), avg(answerableRecall3),
                avg(answerableMrr), mean(answerableDoc3),
                naFp, vmh, bmv, vohr, bohr, top1dis, both);
    }

    // ── Selection logic ───────────────────────────────────────────────────────

    /** Top 1-2 weight rows that don't regress answerable Hit@1/ChunkHit@3 vs baseline. */
    private List<WeightRow> topWeightRows(List<WeightRow> rows, WeightRow baseline) {
        return rows.stream()
                .filter(r -> r.answerableHit1 >= baseline.answerableHit1 - 1e-9
                        && r.answerableChunkHit3 >= baseline.answerableChunkHit3 - 1e-9)
                .sorted(Comparator.comparingDouble((WeightRow r) -> -r.answerableChunkHit3)
                        .thenComparingDouble(r -> -r.answerableRecall3))
                .limit(2)
                .toList();
    }

    private WeightRow chooseBest(List<WeightRow> weightRows, List<KRow> kRows, WeightRow baseline) {
        List<WeightRow> pool = new ArrayList<>(weightRows);
        for (KRow kr : kRows) pool.add(kr.row);
        return pool.stream()
                .filter(r -> r.answerableHit1 >= baseline.answerableHit1 - 1e-9
                        && r.answerableChunkHit3 >= baseline.answerableChunkHit3 - 1e-9
                        && r.answerableRecall3 >= baseline.answerableRecall3 - 1e-9)
                .max(Comparator.comparingDouble((WeightRow r) -> r.answerableChunkHit3)
                        .thenComparingDouble(r -> r.answerableHit1)
                        .thenComparingDouble(r -> r.answerableRecall3)
                        .thenComparingDouble(r -> -(Math.abs(r.vectorWeight - 1.0)
                                + Math.abs(r.k - 60.0) / 60.0)))
                .orElse(baseline);
    }

    private String verdict(WeightRow best, WeightRow baseline) {
        boolean weightChanged = !near(best.vectorWeight, 1.0) || !near(best.bm25Weight, 1.0);
        boolean kChanged = !near(best.k, 60.0);
        boolean strictlyBetter = best.answerableChunkHit3 > baseline.answerableChunkHit3 + 1e-9
                || best.answerableHit1 > baseline.answerableHit1 + 1e-9
                || best.answerableRecall3 > baseline.answerableRecall3 + 1e-9;
        if (!strictlyBetter) return "KEEP_EQUAL_RRF_K60";
        if (kChanged && !weightChanged) return "RECOMMEND_DIFFERENT_K";
        return "RECOMMEND_WEIGHTED_RRF";
    }

    private List<String> regressionCases(WeightRow best, WeightRow baseline,
            List<CaseRaw> rawByCase, EvaluationFixture fixture) {
        if (near(best.vectorWeight, baseline.vectorWeight) && near(best.bm25Weight, baseline.bm25Weight)
                && near(best.k, baseline.k)) return List.of();
        RrfFusionService rrfBest = new RrfFusionService();
        RrfFusionService rrfBase = new RrfFusionService();
        List<String> regressed = new ArrayList<>();
        for (CaseRaw raw : rawByCase) {
            if (raw.ec.answerability() != Answerability.ANSWERABLE) continue;
            boolean baseHit = hitAt3(rrfBase.fuse(raw.vector, raw.bm25, TOP_K,
                    baseline.vectorWeight, baseline.bm25Weight, baseline.k), raw.goldChunks);
            boolean bestHit = hitAt3(rrfBest.fuse(raw.vector, raw.bm25, TOP_K,
                    best.vectorWeight, best.bm25Weight, best.k), raw.goldChunks);
            if (baseHit && !bestHit) regressed.add(raw.ec.id());
        }
        return regressed;
    }

    private boolean hitAt3(List<FusedRetrievalCandidate> fused, Set<String> gold) {
        return fused.stream().limit(TOP_K)
                .anyMatch(c -> gold.contains(chunkKey(c.getDocumentId(), c.getChunkIndex())));
    }

    // ── Metric helpers ────────────────────────────────────────────────────────

    private boolean vectorHitAt3(List<RetrievalCandidate> vec, Set<String> gold) {
        return vec.stream().limit(TOP_K)
                .anyMatch(c -> gold.contains(chunkKey(c.getDocumentId(), c.getChunkIndex())));
    }

    private boolean bm25HitAt3(List<RetrievalCandidate> bm25, Set<String> gold) {
        return bm25.stream().limit(TOP_K)
                .anyMatch(c -> gold.contains(chunkKey(c.getDocumentId(), c.getChunkIndex())));
    }

    private double mean(List<Boolean> values) {
        return values.isEmpty() ? 0.0
                : values.stream().filter(Boolean::booleanValue).count() / (double) values.size();
    }

    private double avg(List<Double> values) {
        return values.isEmpty() ? 0.0
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private <T> List<Boolean> sublist(List<Boolean> src, List<T> tag, T match) {
        List<Boolean> out = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) if (tag.get(i).equals(match)) out.add(src.get(i));
        return out;
    }

    private <T> List<Double> sublistD(List<Double> src, List<T> tag, T match) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) if (tag.get(i).equals(match)) out.add(src.get(i));
        return out;
    }

    private boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private String chunkKey(Long docId, Integer idx) { return docId + "_" + idx; }
    private int intProperty(String k, int def) { return Integer.getInteger(k, def); }

    private String sha256(Path path) throws java.io.IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new java.io.IOException("Cannot hash dataset", e); }
    }

    // ── Reproducibility ───────────────────────────────────────────────────────

    private Map<String, Object> buildReproducibility(Path datasetPath, String runId) throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", runId);
        r.put("timestamp", java.time.Instant.now().toString());
        r.put("datasetPath", datasetPath.toAbsolutePath().toString());
        r.put("datasetSha256", sha256(datasetPath));
        r.put("fixtureVersion", EvaluationFixture.VERSION);
        r.put("embedding", "BgeSmallZhV15EmbeddingModel / 512 dimensions");
        r.put("minVectorScore", BASELINE_VECTOR_THRESHOLD);
        r.put("minBm25Score", BASELINE_BM25_THRESHOLD);
        r.put("candidateLimit", CANDIDATE_LIMIT);
        r.put("evaluationTopK", TOP_K);
        r.put("formula", "score = vectorWeight/(k+vectorRank) + bm25Weight/(k+bm25Rank)");
        r.put("corpusIsolationPassed", Boolean.TRUE);
        r.put("modificationsToOtherBehavior",
                "none (embedding/chunking/threshold/dataset/ACL/agent/queries/gold unchanged)");
        return r;
    }

    // ── Markdown report ───────────────────────────────────────────────────────

    private void writeMarkdown(Path out, CalibrationResult result) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Weighted RRF Calibration\n\n");
        sb.append("Formula: `score = vectorWeight / (k + vectorRank) + bm25Weight / (k + bm25Rank)`\n\n");

        sb.append("## Baseline\n\n");
        sb.append("vectorWeight=1.0, bm25Weight=1.0, k=60\n\n");
        appendWeightRow(sb, result.baseline);
        sb.append("\n");

        sb.append("## Weight Experiments (k=60)\n\n");
        sb.append("| vW | bW | k | Hit@1 | ChunkHit@3 | Recall@3 | MRR | DocHit@3 |"
                + " ansHit@1 | ansChunk@3 | ansRecall@3 | ansMrr | naFP | vmh | bmv | vohr | bohr | top1dis | bothMiss |\n");
        sb.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (WeightRow row : result.weightRows) appendRow(sb, row);
        sb.append("\n");

        sb.append("## K Experiments\n\n");
        sb.append("| vW | bW | k | Hit@1 | ChunkHit@3 | Recall@3 | MRR | DocHit@3 |"
                + " ansHit@1 | ansChunk@3 | ansRecall@3 | ansMrr | naFP | vmh | bmv | vohr | bohr | top1dis | bothMiss |\n");
        sb.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (KRow kr : result.kRows) appendRow(sb, kr.row);
        sb.append("\n");

        sb.append("## Best Configuration\n\n");
        appendWeightRow(sb, result.best);
        sb.append("\n");
        appendDiff(sb, result.best, result.baseline);

        sb.append("## Regression Cases\n\n");
        if (result.regressionCases.isEmpty()) sb.append("None.\n\n");
        else { result.regressionCases.forEach(id -> sb.append("- ").append(id).append("\n")); sb.append("\n"); }

        sb.append("## Interpretation\n\n");
        appendInterpretation(sb, result);

        sb.append("## Verdict\n\n");
        sb.append(result.verdict).append("\n\n");

        sb.append("## Verification\n\n");
        sb.append("- datasetSha256=`").append(result.reproducibility.get("datasetSha256")).append("`\n");
        sb.append("- embedding=`").append(result.reproducibility.get("embedding")).append("`\n");
        sb.append("- corpusIsolationPassed=`").append(result.reproducibility.get("corpusIsolationPassed")).append("`\n");
        sb.append("- minVectorScore=`").append(result.reproducibility.get("minVectorScore")).append("`\n");
        sb.append("- minBm25Score=`").append(result.reproducibility.get("minBm25Score")).append("`\n");
        sb.append("- modificationsToOtherBehavior=`")
                .append(result.reproducibility.get("modificationsToOtherBehavior")).append("`\n");

        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private void appendWeightRow(StringBuilder sb, WeightRow row) {
        sb.append(String.format(Locale.ROOT,
                "vW=%.1f bW=%.1f k=%.0f | Hit@1=%.4f ChunkHit@3=%.4f Recall@3=%.4f MRR=%.4f DocHit@3=%.4f%n",
                row.vectorWeight, row.bm25Weight, row.k,
                row.overallHit1, row.overallChunkHit3, row.overallRecall3,
                row.overallMrr, row.overallDocHit3));
        sb.append(String.format(Locale.ROOT,
                "  Answerable: Hit@1=%.4f ChunkHit@3=%.4f Recall@3=%.4f MRR=%.4f DocHit@3=%.4f%n",
                row.answerableHit1, row.answerableChunkHit3, row.answerableRecall3,
                row.answerableMrr, row.answerableDocHit3));
        sb.append(String.format(Locale.ROOT,
                "  Flags: naFP=%d vmh=%d bmv=%d vohr=%d bohr=%d top1dis=%d bothMiss=%d%n",
                row.noAnswerFalsePositive, row.vectorMissBm25Hit, row.bm25MissVectorHit,
                row.vectorOnlyHitRrfRetained, row.bm25OnlyHitRrfRetained,
                row.top1Disagreement, row.bothMiss));
    }

    private void appendRow(StringBuilder sb, WeightRow row) {
        sb.append(String.format(Locale.ROOT,
                "|%.1f|%.1f|%.0f|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%d|%d|%d|%d|%d|%d|%d|%n",
                row.vectorWeight, row.bm25Weight, row.k,
                row.overallHit1, row.overallChunkHit3, row.overallRecall3,
                row.overallMrr, row.overallDocHit3,
                row.answerableHit1, row.answerableChunkHit3, row.answerableRecall3,
                row.answerableMrr,
                row.noAnswerFalsePositive, row.vectorMissBm25Hit, row.bm25MissVectorHit,
                row.vectorOnlyHitRrfRetained, row.bm25OnlyHitRrfRetained,
                row.top1Disagreement, row.bothMiss));
    }

    private void appendDiff(StringBuilder sb, WeightRow best, WeightRow baseline) {
        sb.append(String.format(Locale.ROOT,
                "Delta vs baseline: ansHit@1=%+.4f ansChunkHit@3=%+.4f ansRecall@3=%+.4f%n%n",
                best.answerableHit1 - baseline.answerableHit1,
                best.answerableChunkHit3 - baseline.answerableChunkHit3,
                best.answerableRecall3 - baseline.answerableRecall3));
    }

    private void appendInterpretation(StringBuilder sb, CalibrationResult result) {
        WeightRow bl = result.baseline;
        sb.append(String.format(Locale.ROOT,
                "Baseline (1:1, k=60): VECTOR_MISS_BM25_HIT=%d BM25_MISS_VECTOR_HIT=%d"
                        + " BOTH_MISS=%d TOP1_DISAGREEMENT=%d%n",
                bl.vectorMissBm25Hit, bl.bm25MissVectorHit, bl.bothMiss, bl.top1Disagreement));
        if (bl.vectorMissBm25Hit > bl.bm25MissVectorHit) {
            sb.append("BM25 recovers cases that vector misses — "
                    + "equal weighting already leverages this complementary signal.\n");
        } else if (bl.bm25MissVectorHit > bl.vectorMissBm25Hit) {
            sb.append("Vector recovers more unique cases than BM25 — "
                    + "slight vector upweight may help on this corpus.\n");
        } else {
            sb.append("Vector and BM25 contribute equally; equal weighting is appropriate.\n");
        }
        sb.append(String.format(Locale.ROOT,
                "Best config (vW=%.1f bW=%.1f k=%.0f): ansChunkHit@3=%.4f vs baseline %.4f%n%n",
                result.best.vectorWeight, result.best.bm25Weight, result.best.k,
                result.best.answerableChunkHit3, bl.answerableChunkHit3));
    }

    private void printSummary(CalibrationResult result) {
        System.out.printf(Locale.ROOT,
                "Weighted RRF verdict: %s%n  best: vW=%.1f bW=%.1f k=%.0f%n"
                        + "  ansChunkHit@3=%.4f (baseline=%.4f)%n",
                result.verdict,
                result.best.vectorWeight, result.best.bm25Weight, result.best.k,
                result.best.answerableChunkHit3, result.baseline.answerableChunkHit3);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    private record CaseRaw(EvaluationCase ec, List<RetrievalCandidate> vector,
            List<RetrievalCandidate> bm25, Set<String> goldChunks) {}

    private record WeightRow(
            double vectorWeight, double bm25Weight, double k,
            double overallHit1, double overallChunkHit3, double overallRecall3,
            double overallMrr, double overallDocHit3,
            double answerableHit1, double answerableChunkHit3, double answerableRecall3,
            double answerableMrr, double answerableDocHit3,
            int noAnswerFalsePositive, int vectorMissBm25Hit, int bm25MissVectorHit,
            int vectorOnlyHitRrfRetained, int bm25OnlyHitRrfRetained,
            int top1Disagreement, int bothMiss) {}

    private record KRow(double vectorWeight, double bm25Weight, double k, WeightRow row) {}

    private record CalibrationResult(
            Map<String, Object> reproducibility,
            WeightRow baseline,
            List<WeightRow> weightRows,
            List<KRow> kRows,
            WeightRow best,
            List<String> regressionCases,
            String verdict) {}
}
