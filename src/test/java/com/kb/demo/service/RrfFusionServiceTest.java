package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionServiceTest {

    private final RrfFusionService service = new RrfFusionService();

    @Test
    void shouldMergeSameChunkKeepSingleSourceChunksAndSortByFusionScore() {
        List<RetrievalCandidate> redisCandidates = List.of(
                candidate(1L, 0, 0.92, 1, RetrievalSource.REDIS_VECTOR),
                candidate(2L, 0, 0.81, 2, RetrievalSource.REDIS_VECTOR)
        );
        List<RetrievalCandidate> elasticsearchCandidates = List.of(
                candidate(3L, 0, 12.5, 1, RetrievalSource.ELASTICSEARCH_BM25),
                candidate(1L, 0, 8.2, 2, RetrievalSource.ELASTICSEARCH_BM25)
        );

        List<FusedRetrievalCandidate> result =
                service.fuse(redisCandidates, elasticsearchCandidates, 3);

        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(FusedRetrievalCandidate::getDocumentId)
                .containsExactly(1L, 3L, 2L);
        assertThat(result.get(0).getSources())
                .containsExactlyInAnyOrder(
                        RetrievalSource.REDIS_VECTOR,
                        RetrievalSource.ELASTICSEARCH_BM25);
        assertThat(result.get(1).getSources())
                .containsExactly(RetrievalSource.ELASTICSEARCH_BM25);
        assertThat(result.get(2).getSources())
                .containsExactly(RetrievalSource.REDIS_VECTOR);
    }

    @Test
    void shouldRespectTopKAndHandleNonPositiveTopK() {
        List<RetrievalCandidate> redisCandidates = List.of(
                candidate(1L, 0, 0.92, 1, RetrievalSource.REDIS_VECTOR),
                candidate(2L, 0, 0.81, 2, RetrievalSource.REDIS_VECTOR)
        );

        assertThat(service.fuse(redisCandidates, List.of(), 1))
                .hasSize(1);
        assertThat(service.fuse(redisCandidates, List.of(), 0))
                .isEmpty();
    }

    private RetrievalCandidate candidate(
            Long documentId,
            Integer chunkIndex,
            double rawScore,
            int rank,
            RetrievalSource source) {
        return new RetrievalCandidate(documentId, chunkIndex, rawScore, rank, source);
    }
}
