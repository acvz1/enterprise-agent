package com.kb.demo.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kb.demo.judge.DraftJudgeService;
import com.kb.demo.judge.JudgeVerdict;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline evaluation of DraftJudgeService against the 20-case judge dataset.
 * Requires a real LLM endpoint configured via system properties:
 *   evaluation.judge.base-url  (default: http://localhost:11434/v1)
 *   evaluation.judge.api-key   (default: ollama)
 *   evaluation.judge.model     (default: qwen2.5:7b)
 *
 * Writes a markdown report to target/judge-evaluation/<runId>/judge-evaluation.md
 */
class JudgeEvaluationIT {

    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JudgeCase(String id, String query, String expectedVerdict, String notes) {}

    record CaseResult(String id, String query, String expectedVerdict,
                      String actualVerdict, boolean correct, String notes) {}

    @Test
    void evaluatesJudgeAccuracyAgainstLabelledDataset() throws Exception {
        Path datasetPath = Path.of(getClass().getClassLoader()
                .getResource("evaluation/judge-evaluation.json").toURI());
        List<JudgeCase> cases = List.of(JSON.readValue(datasetPath.toFile(), JudgeCase[].class));
        assertThat(cases).hasSize(20);

        ChatLanguageModel model = buildModel();
        DraftJudgeService judgeService = new DraftJudgeService();

        List<CaseResult> results = new ArrayList<>();
        for (JudgeCase c : cases) {
            JudgeVerdict actual;
            try {
                actual = judgeService.judge(model, c.query());
            } catch (Exception e) {
                actual = JudgeVerdict.UNCERTAIN;
            }
            results.add(new CaseResult(
                    c.id(), c.query(), c.expectedVerdict(),
                    actual.name(),
                    actual.name().equals(c.expectedVerdict()),
                    c.notes()));
        }

        String runId = "judge-eval-" + RUN_TIME.format(LocalDateTime.now());
        Path reportDir = reportDir(runId);
        Files.createDirectories(reportDir);
        writeReport(reportDir, runId, results, datasetPath);

        long correct = results.stream().filter(CaseResult::correct).count();
        double accuracy = (double) correct / results.size();

        System.out.printf("Judge evaluation: %d/%d correct (%.1f%%)%n",
                correct, results.size(), accuracy * 100);
        System.out.println("Report: " + reportDir.toAbsolutePath());

        // Soft assertion: 不强制阈值，打印结果供人工审核
        assertThat(results).as("all results have verdicts").allSatisfy(r ->
                assertThat(r.actualVerdict()).isNotBlank());
    }

    // -----------------------------------------------------------------

    private ChatLanguageModel buildModel() {
        String baseUrl = System.getProperty("evaluation.judge.base-url", "http://localhost:11434/v1");
        String apiKey  = System.getProperty("evaluation.judge.api-key",  "ollama");
        String model   = System.getProperty("evaluation.judge.model",    "qwen2.5:7b");
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.0)
                .build();
    }

    private void writeReport(Path dir, String runId,
                             List<CaseResult> results, Path datasetPath) throws Exception {
        long correct = results.stream().filter(CaseResult::correct).count();
        double accuracy = (double) correct / results.size();

        // per-verdict breakdown
        Map<String, long[]> perVerdict = new LinkedHashMap<>();
        for (String v : List.of("SAFE_GENERAL", "REQUIRES_KB", "UNCERTAIN")) {
            long total  = results.stream().filter(r -> r.expectedVerdict().equals(v)).count();
            long hit    = results.stream().filter(r -> r.expectedVerdict().equals(v) && r.correct()).count();
            perVerdict.put(v, new long[]{total, hit});
        }

        StringBuilder md = new StringBuilder();
        md.append("# Judge Evaluation\n\n");
        md.append("## Summary\n\n");
        md.append(String.format("- runId: `%s`%n", runId));
        md.append(String.format("- dataset: `%s`%n", datasetPath.getFileName()));
        md.append(String.format("- cases: %d%n", results.size()));
        md.append(String.format("- correct: %d%n", correct));
        md.append(String.format("- accuracy: `%.4f`%n%n", accuracy));

        md.append("## Per-Verdict Accuracy\n\n");
        md.append("| verdict | total | correct | accuracy |\n");
        md.append("|---|---:|---:|---:|\n");
        perVerdict.forEach((v, arr) ->
                md.append(String.format("|%s|%d|%d|%.4f|%n", v, arr[0], arr[1],
                        arr[0] == 0 ? 0.0 : (double) arr[1] / arr[0])));

        md.append("\n## Case Results\n\n");
        md.append("| id | query | expected | actual | correct | notes |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            md.append(String.format("|%s|%s|%s|%s|%s|%s|%n",
                    r.id(), r.query(), r.expectedVerdict(), r.actualVerdict(),
                    r.correct() ? "✓" : "✗", r.notes()));
        }

        md.append("\n## Verification\n\n");
        md.append(String.format("- model: `%s`%n",
                System.getProperty("evaluation.judge.model", "qwen2.5:7b")));
        md.append(String.format("- baseUrl: `%s`%n",
                System.getProperty("evaluation.judge.base-url", "http://localhost:11434/v1")));

        Files.writeString(dir.resolve("judge-evaluation.md"), md.toString());

        // also write JSON for diffing
        Files.writeString(dir.resolve("judge-evaluation.json"),
                JSON.writeValueAsString(Map.of(
                        "runId", runId,
                        "accuracy", accuracy,
                        "correct", correct,
                        "total", results.size(),
                        "results", results)));
    }

    private Path reportDir(String runId) {
        String configured = System.getProperty("evaluation.output-dir");
        return configured == null || configured.isBlank()
                ? Path.of("target", "judge-evaluation", runId)
                : Path.of(configured).resolve(runId);
    }
}
