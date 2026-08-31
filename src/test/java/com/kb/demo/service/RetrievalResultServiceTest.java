package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.dto.RetrievalSource;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalResultServiceTest {

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private RetrievalResultService retrievalResultService;

    @Test
    void assembleHitsPreservesRrfOrderAndFiltersCrossCombinationAndStaleCandidate() {
        FusedRetrievalCandidate firstCandidate = new FusedRetrievalCandidate(
                1L, 0, 0.032, Set.of(RetrievalSource.REDIS_VECTOR, RetrievalSource.ELASTICSEARCH_BM25));
        FusedRetrievalCandidate secondCandidate = new FusedRetrievalCandidate(
                2L, 1, 0.016, Set.of(RetrievalSource.ELASTICSEARCH_BM25));
        FusedRetrievalCandidate staleCandidate = new FusedRetrievalCandidate(
                3L, 9, 0.008, Set.of(RetrievalSource.REDIS_VECTOR));

        DocumentChunk firstChunk  = chunk(101L, 1L, "Document One", 0, "First chunk content", 1);
        DocumentChunk secondChunk = chunk(202L, 2L, "Document Two", 1, "Second chunk content", 1);
        DocumentChunk crossCombination = chunk(102L, 1L, "Document One", 1, "Cross combination must not be returned", 1);

        // activeVersion for all docs = 1
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(new Object[]{1L, 1}, new Object[]{2L, 1}, new Object[]{3L, 1}));

        Set<Long> documentIds = Set.of(1L, 2L, 3L);
        Set<Integer> chunkIndexes = Set.of(0, 1, 9);
        when(documentChunkRepository.findCandidateChunksWithDocument(documentIds, chunkIndexes))
                .thenReturn(List.of(secondChunk, crossCombination, firstChunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(
                List.of(firstCandidate, secondCandidate, staleCandidate));

        assertThat(hits)
                .extracting(RetrievalHit::getDocumentId, RetrievalHit::getChunkIndex)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 0),
                        org.assertj.core.groups.Tuple.tuple(2L, 1));
        assertThat(hits.get(0).getChunkId()).isEqualTo(101L);
        assertThat(hits.get(0).getDocumentTitle()).isEqualTo("Document One");
        assertThat(hits.get(0).getContent()).isEqualTo("First chunk content");
        assertThat(hits.get(0).getFusionScore()).isEqualTo(0.032);
        assertThat(hits.get(0).getSources())
                .containsExactlyInAnyOrder(RetrievalSource.REDIS_VECTOR, RetrievalSource.ELASTICSEARCH_BM25);

        verify(documentChunkRepository, times(1))
                .findCandidateChunksWithDocument(documentIds, chunkIndexes);
    }

    @Test
    void staleCandidateWithOldVersionIsFiltered() {
        // candidate points to v2 chunk but activeVersion is still 1
        FusedRetrievalCandidate stale = new FusedRetrievalCandidate(
                1L, 0, 0.05, Set.of(RetrievalSource.REDIS_VECTOR), 2);
        FusedRetrievalCandidate fresh = new FusedRetrievalCandidate(
                1L, 1, 0.04, Set.of(RetrievalSource.ELASTICSEARCH_BM25), 1);

        DocumentChunk v2Chunk = chunk(10L, 1L, "Doc", 0, "v2 content", 2);
        DocumentChunk v1Chunk = chunk(11L, 1L, "Doc", 1, "v1 content", 1);

        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(new Object[]{1L, 1}));
        when(documentChunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(v2Chunk, v1Chunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(stale, fresh));

        // v2Chunk has documentVersion=2 but activeVersion=1 → filtered out
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(11L);
        assertThat(hits.get(0).getDocumentVersion()).isEqualTo(1);
    }

    @Test
    void assembleHitsReturnsEmptyForEmptyCandidates() {
        assertThat(retrievalResultService.assembleHits(List.of())).isEmpty();
    }

    private DocumentChunk chunk(Long chunkId, Long documentId, String title,
                                Integer chunkIndex, String content, Integer version) {
        Document document = new Document();
        document.setId(documentId);
        document.setTitle(title);

        DocumentChunk chunk = new DocumentChunk(document, chunkIndex, content);
        chunk.setId(chunkId);
        chunk.setDocumentVersion(version);
        return chunk;
    }
}
