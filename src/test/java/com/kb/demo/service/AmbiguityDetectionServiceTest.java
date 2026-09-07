package com.kb.demo.service;

import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.dto.RetrievalSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AmbiguityDetectionServiceTest {

    private AmbiguityDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AmbiguityDetectionService();
        ReflectionTestUtils.setField(service, "ambiguityScoreGap", 0.05);
        ReflectionTestUtils.setField(service, "minDocuments", 2);
        ReflectionTestUtils.setField(service, "minRelevanceScore", 0.80);
    }

    private RetrievalHit hit(long docId, long chunkId, String title, double score) {
        return new RetrievalHit(docId, chunkId, 0, title, "正文", score,
                Set.of(RetrievalSource.REDIS_VECTOR));
    }

    @Test
    void multipleTopicsCloseScore_returnsClarification() {
        List<RetrievalHit> hits = List.of(
                hit(1L, 11L, "差旅报销", 0.90),
                hit(2L, 21L, "住宿报销", 0.89),
                hit(3L, 31L, "餐饮报销", 0.88));

        Optional<String> result = service.detectClarification(hits);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("差旅报销", "住宿报销", "餐饮报销");
    }

    @Test
    void closeScoresButSecondTopicBelowMinimumRelevance_doesNotClarify() {
        List<RetrievalHit> hits = List.of(
                hit(1L, 11L, "员工手册", 0.81),
                hit(2L, 21L, "差旅报销制度", 0.79));

        Optional<String> result = service.detectClarification(hits);

        assertThat(result).isEmpty();
    }

    @Test
    void multipleChunksSameDocument_doesNotClarify() {
        List<RetrievalHit> hits = List.of(
                hit(1L, 11L, "报销制度", 0.90),
                hit(1L, 12L, "报销制度", 0.88),
                hit(1L, 13L, "报销制度", 0.85));

        Optional<String> result = service.detectClarification(hits);

        assertThat(result).isEmpty();
    }

    @Test
    void clearWinner_doesNotClarify() {
        List<RetrievalHit> hits = List.of(
                hit(1L, 11L, "差旅报销", 0.95),
                hit(2L, 21L, "住宿报销", 0.86));

        Optional<String> result = service.detectClarification(hits);

        assertThat(result).isEmpty();
    }
}
