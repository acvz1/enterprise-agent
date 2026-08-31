package com.kb.demo.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import com.kb.demo.service.RrfFusionService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Test-only evaluation engine. It deliberately depends on production candidate DTOs and RRF implementation. */
final class RetrievalEvaluationV2 {
    static final int TOP_K = 3;
    static final double RRF_K = 60.0;
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private RetrievalEvaluationV2() {
    }

    static List<EvaluationCase> loadCases(Path datasetPath) throws IOException {
        List<EvaluationCase> cases = JSON.readValue(datasetPath.toFile(), new TypeReference<>() { });
        validateCases(cases, datasetPath);
        return cases;
    }

    static void validateCases(List<EvaluationCase> cases, Path datasetPath) {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Evaluation dataset is empty: " + datasetPath);
        }
        Set<String> ids = new LinkedHashSet<>();
        for (EvaluationCase evaluationCase : cases) {
            if (blank(evaluationCase.id()) || blank(evaluationCase.query()) || evaluationCase.category() == null
                    || evaluationCase.answerability() == null || !ids.add(evaluationCase.id())) {
                throw new IllegalArgumentException("Invalid or duplicate evaluation case: " + evaluationCase);
            }
            if (evaluationCase.answerability() == Answerability.ANSWERABLE
                    && (evaluationCase.relevantDocs() == null || evaluationCase.relevantDocs().isEmpty()
                    || evaluationCase.relevantChunks() == null || evaluationCase.relevantChunks().isEmpty())) {
                throw new IllegalArgumentException("Answerable case must define reviewed gold: " + evaluationCase.id());
            }
            if (evaluationCase.answerability() == Answerability.NO_ANSWER
                    && ((evaluationCase.relevantDocs() != null && !evaluationCase.relevantDocs().isEmpty())
                    || (evaluationCase.relevantChunks() != null && !evaluationCase.relevantChunks().isEmpty()))) {
                throw new IllegalArgumentException("No-answer case cannot define gold evidence: " + evaluationCase.id());
            }
        }
    }

    static CaseResult evaluate(EvaluationCase evaluationCase, EvaluationFixture fixture,
            List<RetrievalCandidate> vectorCandidates, List<RetrievalCandidate> bm25Candidates,
            RrfFusionService rrfFusionService, List<RetrievalCandidate> unrestrictedVectorCandidates,
            List<RetrievalCandidate> unrestrictedBm25Candidates) {
        List<FusedRetrievalCandidate> fused = rrfFusionService.fuse(vectorCandidates, bm25Candidates, TOP_K);
        List<CandidateView> vector = candidateViews(vectorCandidates, fixture, Map.of());
        List<CandidateView> bm25 = candidateViews(bm25Candidates, fixture, Map.of());
        Map<String, RrfContribution> contributions = contributions(vectorCandidates, bm25Candidates);
        List<CandidateView> hybrid = fusedViews(fused, fixture, contributions);

        Set<String> goldChunks = evaluationCase.relevantChunks() == null ? Set.of() : evaluationCase.relevantChunks().stream()
                .map(reference -> runtimeChunkKey(fixture.resolve(reference)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> goldDocs = evaluationCase.relevantDocs() == null ? Set.of() : evaluationCase.relevantDocs().stream()
                .map(fixture::documentId).map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));

        MethodResult vectorResult = metrics(vector, goldChunks, goldDocs);
        MethodResult bm25Result = metrics(bm25, goldChunks, goldDocs);
        MethodResult hybridResult = metrics(hybrid, goldChunks, goldDocs);
        Agreement agreement = agreement(vector, bm25);
        Set<BadCaseType> badCases = classify(evaluationCase, vectorResult, bm25Result, hybridResult,
                agreement, unrestrictedVectorCandidates, unrestrictedBm25Candidates, fixture, goldChunks);

        return new CaseResult(evaluationCase.id(), evaluationCase.query(), evaluationCase.category(),
                evaluationCase.answerability(), evaluationCase.permissionContext(),
                List.copyOf(goldChunks), List.copyOf(goldDocs), vector, bm25, hybrid,
                vectorResult, bm25Result, hybridResult, agreement, List.copyOf(badCases));
    }

    static Map<String, RrfContribution> contributions(List<RetrievalCandidate> vector,
            List<RetrievalCandidate> bm25) {
        Map<String, RrfContribution> result = new LinkedHashMap<>();
        addContributions(result, vector, RetrievalSource.REDIS_VECTOR);
        addContributions(result, bm25, RetrievalSource.ELASTICSEARCH_BM25);
        return result;
    }

    private static void addContributions(Map<String, RrfContribution> result,
            List<RetrievalCandidate> candidates, RetrievalSource expectedSource) {
        for (RetrievalCandidate candidate : candidates) {
            if (candidate.getSource() != expectedSource) {
                throw new IllegalArgumentException("Unexpected source in RRF diagnostics: " + candidate.getSource());
            }
            String key = runtimeChunkKey(candidate.getDocumentId(), candidate.getChunkIndex());
            RrfContribution previous = result.getOrDefault(key, RrfContribution.empty());
            double value = 1.0 / (RRF_K + candidate.getRank());
            result.put(key, expectedSource == RetrievalSource.REDIS_VECTOR
                    ? previous.withVector(candidate.getRank(), value)
                    : previous.withBm25(candidate.getRank(), value));
        }
    }

    static MethodResult metrics(List<CandidateView> candidates, Set<String> goldChunks, Set<String> goldDocs) {
        List<CandidateView> top = candidates.subList(0, Math.min(TOP_K, candidates.size()));
        int firstRelevantRank = 0;
        for (CandidateView candidate : top) {
            if (goldChunks.contains(candidate.runtimeChunkKey())) {
                firstRelevantRank = candidate.rank();
                break;
            }
        }
        long chunkRelevantCount = top.stream().filter(candidate -> goldChunks.contains(candidate.runtimeChunkKey())).count();
        boolean docHitAt3 = top.stream().anyMatch(candidate -> goldDocs.contains(String.valueOf(candidate.documentId())));
        return new MethodResult(
                firstRelevantRank == 1,
                firstRelevantRank > 0,
                goldChunks.isEmpty() ? 0.0 : chunkRelevantCount / (double) goldChunks.size(),
                firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank,
                docHitAt3,
                top.isEmpty(),
                firstRelevantRank);
    }

    static List<CandidateView> candidateViews(List<RetrievalCandidate> candidates,
            EvaluationFixture fixture, Map<String, RrfContribution> ignored) {
        List<CandidateView> views = new ArrayList<>();
        for (RetrievalCandidate candidate : candidates) {
            views.add(new CandidateView(fixture.logicalDocumentId(candidate.getDocumentId()),
                    fixture.sectionId(candidate.getDocumentId(), candidate.getChunkIndex()), candidate.getDocumentId(),
                    candidate.getChunkIndex(), candidate.getRank(), candidate.getRawScore(), candidate.getSource().name(),
                    null, null, null, null, candidate.getRawScore()));
        }
        return List.copyOf(views.subList(0, Math.min(TOP_K, views.size())));
    }

    static List<CandidateView> fusedViews(List<FusedRetrievalCandidate> candidates, EvaluationFixture fixture,
            Map<String, RrfContribution> contributions) {
        List<CandidateView> views = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            FusedRetrievalCandidate candidate = candidates.get(index);
            String key = runtimeChunkKey(candidate.getDocumentId(), candidate.getChunkIndex());
            RrfContribution contribution = contributions.getOrDefault(key, RrfContribution.empty());
            views.add(new CandidateView(fixture.logicalDocumentId(candidate.getDocumentId()),
                    fixture.sectionId(candidate.getDocumentId(), candidate.getChunkIndex()), candidate.getDocumentId(),
                    candidate.getChunkIndex(), index + 1, candidate.getFusionScore(), "HYBRID_RRF",
                    contribution.vectorRank(), contribution.bm25Rank(),
                    contribution.vectorContribution(), contribution.bm25Contribution(), candidate.getFusionScore()));
        }
        return List.copyOf(views);
    }

    static Agreement agreement(List<CandidateView> vector, List<CandidateView> bm25) {
        String vectorTop1 = vector.isEmpty() ? null : vector.get(0).runtimeChunkKey();
        String bm25Top1 = bm25.isEmpty() ? null : bm25.get(0).runtimeChunkKey();
        Set<String> vectorTop3 = topKeys(vector);
        Set<String> bm25Top3 = topKeys(bm25);
        Set<String> intersection = new LinkedHashSet<>(vectorTop3);
        intersection.retainAll(bm25Top3);
        Set<String> union = new LinkedHashSet<>(vectorTop3);
        union.addAll(bm25Top3);
        return new Agreement(vectorTop1 != null && vectorTop1.equals(bm25Top1),
                union.isEmpty() ? 1.0 : intersection.size() / (double) union.size(),
                vectorTop1, bm25Top1);
    }

    static Set<BadCaseType> classify(EvaluationCase evaluationCase, MethodResult vector, MethodResult bm25,
            MethodResult hybrid, Agreement agreement, List<RetrievalCandidate> unrestrictedVector,
            List<RetrievalCandidate> unrestrictedBm25, EvaluationFixture fixture, Set<String> goldChunks) {
        Set<BadCaseType> result = EnumSet.noneOf(BadCaseType.class);
        if (evaluationCase.answerability() == Answerability.NO_ANSWER) {
            if (!hybrid.refused()) result.add(BadCaseType.NO_ANSWER_FALSE_POSITIVE);
            return result;
        }
        if (!vector.chunkHitAt3() && bm25.chunkHitAt3()) result.add(BadCaseType.VECTOR_MISS_BM25_HIT);
        if (vector.chunkHitAt3() && !bm25.chunkHitAt3()) result.add(BadCaseType.BM25_MISS_VECTOR_HIT);
        if (!vector.chunkHitAt3() && !bm25.chunkHitAt3()) result.add(BadCaseType.BOTH_MISS);
        if (vector.chunkHitAt3() && bm25.chunkHitAt3()) {
            result.add(BadCaseType.BOTH_HIT);
            if (!hybrid.chunkHitAt3()) result.add(BadCaseType.BOTH_HIT_RRF_DROPPED);
        }
        if (vector.chunkHitAt3() && !bm25.chunkHitAt3() && hybrid.chunkHitAt3())
            result.add(BadCaseType.VECTOR_ONLY_HIT_RRF_RETAINED);
        if (bm25.chunkHitAt3() && !vector.chunkHitAt3() && hybrid.chunkHitAt3())
            result.add(BadCaseType.BM25_ONLY_HIT_RRF_RETAINED);
        if (hybrid.docHitAt3() && !hybrid.chunkHitAt3()) result.add(BadCaseType.DOC_HIT_CHUNK_MISS);
        if (!agreement.top1Agreement() && agreement.vectorTop1() != null && agreement.bm25Top1() != null)
            result.add(BadCaseType.TOP1_DISAGREEMENT);
        if (agreement.overlapAt3() == 0.0 && agreement.vectorTop1() != null && agreement.bm25Top1() != null)
            result.add(BadCaseType.HIGH_RETRIEVAL_DISAGREEMENT);
        if (evaluationCase.permissionContext() != null && !evaluationCase.permissionContext().expectedAccess()) {
            boolean globallyRelevant = candidateViews(unrestrictedVector, fixture, Map.of()).stream()
                    .anyMatch(candidate -> goldChunks.contains(candidate.runtimeChunkKey()))
                    || candidateViews(unrestrictedBm25, fixture, Map.of()).stream()
                    .anyMatch(candidate -> goldChunks.contains(candidate.runtimeChunkKey()));
            if (globallyRelevant && !hybrid.chunkHitAt3()) result.add(BadCaseType.PERMISSION_FILTERED_RELEVANT_RESULT);
        }
        return result;
    }

    static RunReport report(String runId, Path datasetPath, EvaluationFixture fixture, EvaluationConfig config,
            List<CaseResult> results, CorpusManifest corpusManifest) throws IOException {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (EvaluationCategory category : EvaluationCategory.values()) {
            int count = (int) results.stream().filter(result -> result.category() == category).count();
            if (count > 0) distribution.put(category.name(), count);
        }
        Map<String, AggregateMetrics> overall = aggregateByMethod(results);
        Map<String, Map<String, AggregateMetrics>> categories = new LinkedHashMap<>();
        for (String category : distribution.keySet()) {
            categories.put(category, aggregateByMethod(results.stream()
                    .filter(result -> result.category().name().equals(category)).toList()));
        }
        AgreementSummary agreement = new AgreementSummary(
                ratio(results, result -> !result.agreement().top1Agreement()
                        && result.agreement().vectorTop1() != null && result.agreement().bm25Top1() != null),
                average(results.stream().map(result -> result.agreement().overlapAt3()).toList()));
        NoAnswerSummary noAnswer = noAnswer(results);
        PermissionSummary permission = permission(results);
        Map<String, Integer> badCases = new LinkedHashMap<>();
        for (BadCaseType type : BadCaseType.values()) {
            int count = (int) results.stream().filter(result -> result.badCases().contains(type)).count();
            if (count > 0) badCases.put(type.name(), count);
        }
        Map<String, Object> reproducibility = new LinkedHashMap<>();
        reproducibility.put("runId", runId);
        reproducibility.put("timestamp", Instant.now().toString());
        reproducibility.put("datasetPath", datasetPath.toAbsolutePath().toString());
        reproducibility.put("datasetSha256", sha256(datasetPath));
        reproducibility.put("fixtureVersion", EvaluationFixture.VERSION);
        reproducibility.put("fixtureDocumentIds", EvaluationFixture.DOCUMENT_IDS);
        reproducibility.put("fixtureChunkCount", corpusManifest.fixtureChunkCount());
        reproducibility.put("redisVectorIndex", corpusManifest.redisIndex());
        reproducibility.put("redisKeyPrefix", corpusManifest.redisKeyPrefix());
        reproducibility.put("elasticsearchIndex", corpusManifest.elasticsearchIndex());
        reproducibility.put("redisFixtureKeyCount", corpusManifest.redisFixtureKeyCount());
        reproducibility.put("candidateDocumentIds", corpusManifest.candidateDocumentIds());
        reproducibility.put("nonFixtureCandidateDocumentIds", corpusManifest.nonFixtureCandidateDocumentIds());
        reproducibility.put("corpusIsolationPassed", corpusManifest.nonFixtureCandidateDocumentIds().isEmpty());
        reproducibility.put("chunkStrategy", "fixture sections map one-to-one to chunkIndex; no production chunking is changed");
        reproducibility.put("embedding", "BgeSmallZhV15EmbeddingModel / 512 dimensions");
        reproducibility.put("vectorTopK", config.candidateLimit());
        reproducibility.put("bm25TopK", config.candidateLimit());
        reproducibility.put("evaluationTopK", TOP_K);
        reproducibility.put("rrfK", RRF_K);
        reproducibility.put("minVectorScore", config.minVectorScore());
        reproducibility.put("minBm25Score", config.minBm25Score());
        reproducibility.put("retrievalModes", List.of("VECTOR_ONLY", "BM25_ONLY", "HYBRID_RRF"));
        reproducibility.put("gitVersion", gitVersion());
        return new RunReport(reproducibility, distribution, overall, categories, agreement, noAnswer,
                permission, badCases, results);
    }

    static Map<String, AggregateMetrics> aggregateByMethod(Collection<CaseResult> results) {
        return Map.of(
                "VECTOR_ONLY", aggregate(results.stream().map(CaseResult::vector).toList()),
                "BM25_ONLY", aggregate(results.stream().map(CaseResult::bm25).toList()),
                "HYBRID_RRF", aggregate(results.stream().map(CaseResult::hybrid).toList()));
    }

    static AggregateMetrics aggregate(List<MethodResult> values) {
        if (values.isEmpty()) return new AggregateMetrics(0, 0, 0, 0, 0, 0);
        return new AggregateMetrics(values.size(), ratio(values, MethodResult::hitAt1), ratio(values, MethodResult::chunkHitAt3),
                average(values.stream().map(MethodResult::recallAt3).toList()),
                average(values.stream().map(MethodResult::mrr).toList()), ratio(values, MethodResult::docHitAt3));
    }

    static BaselineComparison compare(RunReport current, Path baselinePath) throws IOException {
        RunReport baseline = JSON.readValue(baselinePath.toFile(), RunReport.class);
        Map<String, MetricDelta> delta = new LinkedHashMap<>();
        for (String method : List.of("VECTOR_ONLY", "BM25_ONLY", "HYBRID_RRF")) {
            AggregateMetrics before = baseline.overall().get(method);
            AggregateMetrics after = current.overall().get(method);
            if (before != null && after != null) delta.put(method, new MetricDelta(before, after));
        }
        Map<String, CaseResult> previousCases = baseline.caseResults().stream()
                .collect(Collectors.toMap(CaseResult::caseId, result -> result));
        List<String> fixed = new ArrayList<>();
        List<String> regressed = new ArrayList<>();
        List<String> rankImproved = new ArrayList<>();
        List<String> rankRegressed = new ArrayList<>();
        for (CaseResult currentCase : current.caseResults()) {
            CaseResult previous = previousCases.get(currentCase.caseId());
            if (previous == null) continue;
            if (!previous.hybrid().chunkHitAt3() && currentCase.hybrid().chunkHitAt3()) fixed.add(currentCase.caseId());
            if (previous.hybrid().chunkHitAt3() && !currentCase.hybrid().chunkHitAt3()) regressed.add(currentCase.caseId());
            if (currentCase.hybrid().firstRelevantRank() > 0 && previous.hybrid().firstRelevantRank() > 0) {
                if (currentCase.hybrid().firstRelevantRank() < previous.hybrid().firstRelevantRank()) rankImproved.add(currentCase.caseId());
                if (currentCase.hybrid().firstRelevantRank() > previous.hybrid().firstRelevantRank()) rankRegressed.add(currentCase.caseId());
            }
        }
        return new BaselineComparison(baselinePath.toString(), delta, fixed, regressed, rankImproved, rankRegressed);
    }

    static void writeReports(Path directory, RunReport report, Optional<BaselineComparison> comparison) throws IOException {
        Files.createDirectories(directory);
        JSON.writeValue(directory.resolve("evaluation-report.json").toFile(), report);
        comparison.ifPresent(value -> {
            try { JSON.writeValue(directory.resolve("baseline-comparison.json").toFile(), value); }
            catch (IOException exception) { throw new EvaluationWriteException(exception); }
        });
        try {
            Files.writeString(directory.resolve("evaluation-summary.md"), markdown(report, comparison), StandardCharsets.UTF_8);
        } catch (EvaluationWriteException exception) {
            throw exception.getCause();
        }
    }

    static String markdown(RunReport report, Optional<BaselineComparison> comparison) {
        StringBuilder out = new StringBuilder("# RAG Evaluation Run\n\n## Configuration\n\n");
        report.reproducibility().forEach((key, value) -> out.append("- ").append(key).append(": `")
                .append(value).append("`\n"));
        out.append("\n## Dataset Distribution\n\n");
        report.datasetDistribution().forEach((category, count) -> out.append("- ").append(category).append(": ").append(count).append('\n'));
        out.append("\n## Overall Retrieval Metrics\n\n| Method | Hit@1 | Chunk Hit@3 | Recall@3 | MRR | Doc Hit@3 |\n|---|---:|---:|---:|---:|---:|\n");
        report.overall().forEach((method, metric) -> metricRow(out, method, metric));
        out.append("\n## Document vs Chunk Retrieval\n\nDoc Hit@3 only proves the document was found; Chunk Hit@3 proves the reviewed evidence section was found.\n");
        out.append("\n## Metrics by Query Category\n");
        report.byCategory().forEach((category, metrics) -> {
            out.append("\n### ").append(category).append("\n\n| Method | Hit@1 | Chunk Hit@3 | Recall@3 | MRR | Doc Hit@3 |\n|---|---:|---:|---:|---:|---:|\n");
            metrics.forEach((method, metric) -> metricRow(out, method, metric));
        });
        out.append("\n## Vector vs BM25 Agreement\n\n- Top1 disagreement rate: ").append(percent(report.agreement().top1DisagreementRate()))
                .append("\n- Average overlap@3 (Jaccard): ").append(percent(report.agreement().averageOverlapAt3())).append('\n');
        out.append("\n## Hybrid / RRF Analysis\n\nEach hybrid candidate in JSON records real vector/BM25 ranks, each contribution `1 / (60 + rank)`, final RRF score and final rank.\n");
        out.append("\n## No-answer Evaluation\n\n- Cases: ").append(report.noAnswer().sampleCount())
                .append("\n- Correct refusal rate: ").append(percent(report.noAnswer().correctRefusalRate()))
                .append("\n- False positive rate: ").append(percent(report.noAnswer().falsePositiveRate())).append('\n');
        out.append("\n## Permission-sensitive Evaluation\n\n- Cases: ").append(report.permission().sampleCount())
                .append("\n- Authorized Hit@3: ").append(percent(report.permission().authorizedHitAt3()))
                .append("\n- Expected-denied cases without hidden-gold hit: ").append(percent(report.permission().deniedFilteredRate())).append('\n');
        if (comparison.isPresent()) {
            out.append("\n## Baseline Comparison\n\n| Method | Metric | Before | After | Delta |\n|---|---|---:|---:|---:|\n");
            comparison.get().metricDelta().forEach((method, delta) -> deltaRows(out, method, delta));
        }
        out.append("\n## Fixed Cases\n\n").append(comparison.map(value -> listOrNone(value.fixedCases())).orElse("No baseline supplied.\n"));
        out.append("\n## Regressed Cases\n\n").append(comparison.map(value -> listOrNone(value.regressedCases())).orElse("No baseline supplied.\n"));
        out.append("\n## Bad Cases\n\n");
        if (report.badCaseCounts().isEmpty()) out.append("None.\n");
        else report.badCaseCounts().forEach((type, count) -> out.append("- ").append(type).append(": ").append(count).append('\n'));
        return out.toString();
    }

    private static void metricRow(StringBuilder out, String method, AggregateMetrics metric) {
        out.append('|').append(method).append('|').append(decimal(metric.hitAt1())).append('|')
                .append(decimal(metric.chunkHitAt3())).append('|').append(decimal(metric.recallAt3())).append('|')
                .append(decimal(metric.mrr())).append('|').append(decimal(metric.docHitAt3())).append("|\n");
    }

    private static void deltaRows(StringBuilder out, String method, MetricDelta delta) {
        deltaRow(out, method, "Hit@1", delta.before().hitAt1(), delta.after().hitAt1());
        deltaRow(out, method, "Chunk Hit@3", delta.before().chunkHitAt3(), delta.after().chunkHitAt3());
        deltaRow(out, method, "Recall@3", delta.before().recallAt3(), delta.after().recallAt3());
        deltaRow(out, method, "MRR", delta.before().mrr(), delta.after().mrr());
    }

    private static void deltaRow(StringBuilder out, String method, String metric, double before, double after) {
        out.append('|').append(method).append('|').append(metric).append('|').append(decimal(before)).append('|')
                .append(decimal(after)).append('|').append(decimal(after - before)).append("|\n");
    }

    private static NoAnswerSummary noAnswer(List<CaseResult> results) {
        List<CaseResult> values = results.stream().filter(result -> result.answerability() == Answerability.NO_ANSWER).toList();
        return new NoAnswerSummary(values.size(), ratio(values, result -> result.hybrid().refused()),
                ratio(values, result -> !result.hybrid().refused()));
    }

    private static PermissionSummary permission(List<CaseResult> results) {
        List<CaseResult> values = results.stream().filter(result -> result.permissionContext() != null).toList();
        List<CaseResult> authorized = values.stream().filter(result -> result.permissionContext().expectedAccess()).toList();
        List<CaseResult> denied = values.stream().filter(result -> !result.permissionContext().expectedAccess()).toList();
        return new PermissionSummary(values.size(), ratio(authorized, result -> result.hybrid().chunkHitAt3()),
                ratio(denied, result -> !result.hybrid().chunkHitAt3()));
    }

    private static <T> double ratio(Collection<T> values, java.util.function.Predicate<T> predicate) {
        return values.isEmpty() ? 0.0 : values.stream().filter(predicate).count() / (double) values.size();
    }

    private static double average(Collection<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static Set<String> topKeys(List<CandidateView> candidates) {
        return candidates.stream().limit(TOP_K).map(CandidateView::runtimeChunkKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String runtimeChunkKey(EvaluationFixture.FixtureChunk chunk) {
        return runtimeChunkKey(chunk.documentId(), chunk.chunkIndex());
    }

    private static String runtimeChunkKey(Long documentId, Integer chunkIndex) {
        return documentId + "_" + chunkIndex;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String sha256(Path path) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception exception) {
            throw new IOException("Cannot hash evaluation dataset", exception);
        }
    }

    private static String gitVersion() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? value : "unavailable";
        } catch (Exception ignored) { return "unavailable"; }
    }

    private static String decimal(double value) { return String.format(Locale.ROOT, "%.4f", value); }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.2f%%", value * 100); }
    private static String listOrNone(List<String> values) { return values.isEmpty() ? "None.\n" : values.stream().map(value -> "- " + value).collect(Collectors.joining("\n", "", "\n")); }

    enum BadCaseType {
        VECTOR_MISS_BM25_HIT, BM25_MISS_VECTOR_HIT, BOTH_MISS, BOTH_HIT, BOTH_HIT_RRF_DROPPED,
        VECTOR_ONLY_HIT_RRF_RETAINED, BM25_ONLY_HIT_RRF_RETAINED, DOC_HIT_CHUNK_MISS,
        TOP1_DISAGREEMENT, HIGH_RETRIEVAL_DISAGREEMENT, PERMISSION_FILTERED_RELEVANT_RESULT,
        NO_ANSWER_FALSE_POSITIVE
    }

    record EvaluationConfig(int candidateLimit, double minVectorScore, double minBm25Score) { }
    record CorpusManifest(String redisIndex, String redisKeyPrefix, String elasticsearchIndex,
            long redisFixtureKeyCount, int fixtureChunkCount, Set<Long> candidateDocumentIds,
            Set<Long> nonFixtureCandidateDocumentIds) { }

    static CorpusManifest corpusManifest(String redisIndex, String redisKeyPrefix, String elasticsearchIndex,
            long redisFixtureKeyCount, int fixtureChunkCount, Set<Long> candidateDocumentIds,
            Set<Long> nonFixtureCandidateDocumentIds) {
        return new CorpusManifest(redisIndex, redisKeyPrefix, elasticsearchIndex, redisFixtureKeyCount,
                fixtureChunkCount, Set.copyOf(candidateDocumentIds), Set.copyOf(nonFixtureCandidateDocumentIds));
    }
    record CandidateView(String documentRef, String sectionRef, Long documentId, Integer chunkIndex, int rank,
            double rawScore, String source, Integer vectorRank, Integer bm25Rank, Double vectorContribution,
            Double bm25Contribution, double finalScore) {
        String runtimeChunkKey() { return RetrievalEvaluationV2.runtimeChunkKey(documentId, chunkIndex); }
    }
    record RrfContribution(Integer vectorRank, Integer bm25Rank, double vectorContribution, double bm25Contribution) {
        static RrfContribution empty() { return new RrfContribution(null, null, 0.0, 0.0); }
        RrfContribution withVector(int rank, double contribution) { return new RrfContribution(rank, bm25Rank, contribution, bm25Contribution); }
        RrfContribution withBm25(int rank, double contribution) { return new RrfContribution(vectorRank, rank, vectorContribution, contribution); }
    }
    record MethodResult(boolean hitAt1, boolean chunkHitAt3, double recallAt3, double mrr,
            boolean docHitAt3, boolean refused, int firstRelevantRank) { }
    record Agreement(boolean top1Agreement, double overlapAt3, String vectorTop1, String bm25Top1) { }
    record CaseResult(String caseId, String query, EvaluationCategory category, Answerability answerability,
            EvaluationPermissionContext permissionContext, List<String> goldChunks, List<String> goldDocs,
            List<CandidateView> vectorTopK, List<CandidateView> bm25TopK, List<CandidateView> hybridTopK,
            MethodResult vector, MethodResult bm25, MethodResult hybrid, Agreement agreement, List<BadCaseType> badCases) { }
    record AggregateMetrics(int sampleCount, double hitAt1, double chunkHitAt3, double recallAt3, double mrr, double docHitAt3) { }
    record AgreementSummary(double top1DisagreementRate, double averageOverlapAt3) { }
    record NoAnswerSummary(int sampleCount, double correctRefusalRate, double falsePositiveRate) { }
    record PermissionSummary(int sampleCount, double authorizedHitAt3, double deniedFilteredRate) { }
    /** Retrieval and generation are intentionally separated; no LLM judge is introduced in this iteration. */
    record GenerationEvaluationResult(boolean retrievalSuccess, boolean answerGenerated,
            boolean noAnswerTriggered, boolean citationPresent, boolean citationMatchesRetrievedContext) { }
    record RunReport(Map<String, Object> reproducibility, Map<String, Integer> datasetDistribution,
            Map<String, AggregateMetrics> overall, Map<String, Map<String, AggregateMetrics>> byCategory,
            AgreementSummary agreement, NoAnswerSummary noAnswer, PermissionSummary permission,
            Map<String, Integer> badCaseCounts, List<CaseResult> caseResults) { }
    record MetricDelta(AggregateMetrics before, AggregateMetrics after) { }
    record BaselineComparison(String baselinePath, Map<String, MetricDelta> metricDelta, List<String> fixedCases,
            List<String> regressedCases, List<String> rankImprovedCases, List<String> rankRegressedCases) { }
    private static final class EvaluationWriteException extends RuntimeException {
        EvaluationWriteException(IOException cause) { super(cause); }
        @Override public IOException getCause() { return (IOException) super.getCause(); }
    }
}
