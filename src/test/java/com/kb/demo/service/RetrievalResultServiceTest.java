package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.dto.RetrievalSource;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalResultServiceTest {

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @InjectMocks
    private RetrievalResultService retrievalResultService;

    @Test
    void assembleHitsPreservesRrfOrderAndFiltersCrossCombinationAndStaleCandidate() {
        FusedRetrievalCandidate firstCandidate = new FusedRetrievalCandidate(
                1L,
                0,
                0.032,
                Set.of(
                        RetrievalSource.REDIS_VECTOR,
                        RetrievalSource.ELASTICSEARCH_BM25));
        FusedRetrievalCandidate secondCandidate = new FusedRetrievalCandidate(
                2L,
                1,
                0.016,
                Set.of(RetrievalSource.ELASTICSEARCH_BM25));
        FusedRetrievalCandidate staleCandidate = new FusedRetrievalCandidate(
                3L,
                9,
                0.008,
                Set.of(RetrievalSource.REDIS_VECTOR));

        DocumentChunk firstChunk =
                chunk(101L, 1L, "Document One", 0, "First chunk content");
        DocumentChunk secondChunk =
                chunk(202L, 2L, "Document Two", 1, "Second chunk content");
        DocumentChunk crossCombination =
                chunk(102L, 1L, "Document One", 1, "Cross combination must not be returned");

        Set<Long> documentIds = Set.of(1L, 2L, 3L);
        Set<Integer> chunkIndexes = Set.of(0, 1, 9);
        when(documentChunkRepository.findCandidateChunksWithDocument(
                documentIds,
                chunkIndexes))
                .thenReturn(List.of(
                        secondChunk,
                        crossCombination,
                        firstChunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(
                List.of(firstCandidate, secondCandidate, staleCandidate));

        assertThat(hits)
                .extracting(
                        RetrievalHit::getDocumentId,
                        RetrievalHit::getChunkIndex)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 0),
                        org.assertj.core.groups.Tuple.tuple(2L, 1));
        assertThat(hits.get(0).getChunkId()).isEqualTo(101L);
        assertThat(hits.get(0).getDocumentTitle()).isEqualTo("Document One");
        assertThat(hits.get(0).getContent()).isEqualTo("First chunk content");
        assertThat(hits.get(0).getFusionScore()).isEqualTo(0.032);
        assertThat(hits.get(0).getSources())
                .containsExactlyInAnyOrder(
                        RetrievalSource.REDIS_VECTOR,
                        RetrievalSource.ELASTICSEARCH_BM25);

        verify(documentChunkRepository, times(1))
                .findCandidateChunksWithDocument(documentIds, chunkIndexes);
    }

    private DocumentChunk chunk(
            Long chunkId,
            Long documentId,
            String title,
            Integer chunkIndex,
            String content) {
        Document document = new Document();
        document.setId(documentId);
        document.setTitle(title);

        DocumentChunk chunk = new DocumentChunk(document, chunkIndex, content);
        chunk.setId(chunkId);
        return chunk;
    }
}
