package com.kb.demo.evaluation;

import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalEvaluationV2Test {

    @Test
    void measuresHitRecallMrrAndDocumentHitSeparately() {
        List<RetrievalEvaluationV2.CandidateView> candidates = List.of(
                candidate("doc-a", "wrong-section", 1L, 0, 1),
                candidate("doc-b", "other", 2L, 0, 2),
                candidate("doc-a", "right-section", 1L, 1, 3));

        RetrievalEvaluationV2.MethodResult result = RetrievalEvaluationV2.metrics(candidates,
                Set.of("1_1", "1_2"), Set.of("1"));

        assertThat(result.hitAt1()).isFalse();
        assertThat(result.chunkHitAt3()).isTrue();
        assertThat(result.recallAt3()).isEqualTo(0.5);
        assertThat(result.mrr()).isEqualTo(1.0 / 3.0);
        assertThat(result.docHitAt3()).isTrue();
    }

    @Test
    void reportsTop1DisagreementAndTop3Overlap() {
        List<RetrievalEvaluationV2.CandidateView> vector = List.of(
                candidate("a", "one", 1L, 0, 1), candidate("a", "two", 1L, 1, 2));
        List<RetrievalEvaluationV2.CandidateView> bm25 = List.of(
                candidate("b", "one", 2L, 0, 1), candidate("a", "two", 1L, 1, 2));

        RetrievalEvaluationV2.Agreement agreement = RetrievalEvaluationV2.agreement(vector, bm25);

        assertThat(agreement.top1Agreement()).isFalse();
        assertThat(agreement.overlapAt3()).isEqualTo(1.0 / 3.0);
    }

    @Test
    void reportsActualRrfRankContributions() {
        Map<String, RetrievalEvaluationV2.RrfContribution> values = RetrievalEvaluationV2.contributions(
                List.of(new RetrievalCandidate(1L, 0, 0.91, 1, RetrievalSource.REDIS_VECTOR)),
                List.of(new RetrievalCandidate(1L, 0, 12.1, 2, RetrievalSource.ELASTICSEARCH_BM25)));

        RetrievalEvaluationV2.RrfContribution contribution = values.get("1_0");
        assertThat(contribution.vectorRank()).isEqualTo(1);
        assertThat(contribution.bm25Rank()).isEqualTo(2);
        assertThat(contribution.vectorContribution()).isEqualTo(1.0 / 61.0);
        assertThat(contribution.bm25Contribution()).isEqualTo(1.0 / 62.0);
    }

    @Test
    void validatesNoAnswerAndMultiChunkGoldSchema() {
        EvaluationCase noAnswer = new EvaluationCase("no-answer", "股票期权", EvaluationCategory.NO_ANSWER,
                List.of(), List.of(), Answerability.NO_ANSWER, null, "human fixture", "no evidence");
        EvaluationCase multiChunk = new EvaluationCase("multi", "请假规则", EvaluationCategory.MIXED,
                List.of("leave-policy"), List.of(new StableChunkReference("leave-policy", "annual-leave"),
                        new StableChunkReference("leave-policy", "sick-leave")),
                Answerability.ANSWERABLE, null, "human fixture", "two evidence sections");

        RetrievalEvaluationV2.validateCases(List.of(noAnswer, multiChunk), java.nio.file.Path.of("dataset.json"));
    }

    @Test
    void classifiesNoAnswerFalsePositiveWithoutChangingAnyThreshold() {
        EvaluationCase noAnswer = new EvaluationCase("no-answer", "股票期权", EvaluationCategory.NO_ANSWER,
                List.of(), List.of(), Answerability.NO_ANSWER, null, "human fixture", "no evidence");
        RetrievalEvaluationV2.MethodResult returnedEvidence = new RetrievalEvaluationV2.MethodResult(
                false, false, 0.0, 0.0, false, false, 0);

        Set<RetrievalEvaluationV2.BadCaseType> badCases = RetrievalEvaluationV2.classify(noAnswer,
                returnedEvidence, returnedEvidence, returnedEvidence,
                new RetrievalEvaluationV2.Agreement(false, 0.0, null, null), List.of(), List.of(),
                EvaluationFixture.current(), Set.of());

        assertThat(badCases).containsExactly(RetrievalEvaluationV2.BadCaseType.NO_ANSWER_FALSE_POSITIVE);
    }

    @Test
    void aggregatesCategoryCompatibleMethodMetrics() {
        RetrievalEvaluationV2.MethodResult hit = new RetrievalEvaluationV2.MethodResult(
                true, true, 0.5, 1.0, true, false, 1);
        RetrievalEvaluationV2.MethodResult miss = new RetrievalEvaluationV2.MethodResult(
                false, false, 0.0, 0.0, true, false, 0);

        RetrievalEvaluationV2.AggregateMetrics metrics = RetrievalEvaluationV2.aggregate(List.of(hit, miss));

        assertThat(metrics.sampleCount()).isEqualTo(2);
        assertThat(metrics.hitAt1()).isEqualTo(0.5);
        assertThat(metrics.chunkHitAt3()).isEqualTo(0.5);
        assertThat(metrics.recallAt3()).isEqualTo(0.25);
        assertThat(metrics.mrr()).isEqualTo(0.5);
        assertThat(metrics.docHitAt3()).isEqualTo(1.0);
    }

    private RetrievalEvaluationV2.CandidateView candidate(String docRef, String sectionRef,
            Long documentId, Integer chunkIndex, int rank) {
        return new RetrievalEvaluationV2.CandidateView(docRef, sectionRef, documentId, chunkIndex, rank,
                0.9, "TEST", null, null, null, null, 0.9);
    }
}
