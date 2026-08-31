package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
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
import static org.mockito.Mockito.when;

/**
 * 版本化构建 + 原子 activeVersion 切换的单元测试。
 *
 * 覆盖：stale candidate 拦截、activeVersion 切换后只返回新版本、
 * v2 chunk 在 activeVersion=1 时被过滤、RRF documentVersion 透传。
 */
@ExtendWith(MockitoExtension.class)
class DocumentVersioningTest {

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private RetrievalResultService retrievalResultService;

    // -----------------------------------------------------------------------
    // 1. BUILD 期间 v1 候选仍通过 assembleHits
    // -----------------------------------------------------------------------
    @Test
    void v1CandidatePassesDuringV2Build() {
        FusedRetrievalCandidate v1Candidate = candidate(1L, 0, 0.03, 1);

        DocumentChunk v1Chunk = chunk(10L, 1L, "Doc", 0, "v1 content", 1);
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(row(1L, 1)));
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(v1Chunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(v1Candidate));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getDocumentVersion()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // 2. validateRebuild 未通过时 activeVersion 不切换 → v2 chunk 被过滤
    // -----------------------------------------------------------------------
    @Test
    void v2ChunkIsFilteredWhenActiveVersionStillOne() {
        FusedRetrievalCandidate v2Candidate = candidate(1L, 0, 0.05, 2);

        DocumentChunk v2Chunk = chunk(20L, 1L, "Doc", 0, "v2 content", 2);
        // activeVersion=1，尚未 CAS 切换
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(row(1L, 1)));
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(v2Chunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(v2Candidate));

        assertThat(hits).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 3. CAS 成功后只返回 v2 chunks
    // -----------------------------------------------------------------------
    @Test
    void afterVersionSwitchOnlyV2ChunksAreServed() {
        FusedRetrievalCandidate v2Candidate = candidate(1L, 0, 0.05, 2);

        DocumentChunk v2Chunk = chunk(20L, 1L, "Doc", 0, "v2 content", 2);
        // activeVersion 已切换为 2
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(row(1L, 2)));
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(v2Chunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(v2Candidate));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getDocumentVersion()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // 4. v1 chunk 在 activeVersion=2 后被过滤（GC 前残留）
    // -----------------------------------------------------------------------
    @Test
    void v1ChunkIsFilteredAfterVersionSwitch() {
        FusedRetrievalCandidate v1Candidate = candidate(1L, 0, 0.03, 1);

        DocumentChunk v1Chunk = chunk(10L, 1L, "Doc", 0, "v1 content", 1);
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(row(1L, 2)));
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(v1Chunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(v1Candidate));

        assertThat(hits).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 5. 多文档场景：每文档独立版本过滤
    // -----------------------------------------------------------------------
    @Test
    void perDocumentVersionFilterIsIndependent() {
        FusedRetrievalCandidate docA = candidate(1L, 0, 0.05, 2); // doc1 activeVersion=2
        FusedRetrievalCandidate docB = candidate(2L, 0, 0.04, 1); // doc2 activeVersion=1

        DocumentChunk chunkA = chunk(10L, 1L, "A", 0, "a content", 2);
        DocumentChunk chunkB = chunk(20L, 2L, "B", 0, "b content", 1);
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(row(1L, 2), row(2L, 1)));
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(chunkA, chunkB));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(docA, docB));

        assertThat(hits).hasSize(2);
        assertThat(hits).extracting(RetrievalHit::getDocumentId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    // -----------------------------------------------------------------------
    // 6. stale candidate（版本不匹配）与 cross-combination（chunk 不匹配）同时过滤
    // -----------------------------------------------------------------------
    @Test
    void staleCandidateAndCrossCombinationBothFiltered() {
        FusedRetrievalCandidate stale  = candidate(1L, 0, 0.05, 2); // v2 but activeVersion=1
        FusedRetrievalCandidate fresh  = candidate(1L, 1, 0.04, 1); // v1 correct
        FusedRetrievalCandidate cross  = candidate(2L, 0, 0.03, 1); // no matching chunk

        DocumentChunk v2Chunk   = chunk(10L, 1L, "Doc", 0, "v2",  2); // stale → filtered
        DocumentChunk v1Chunk   = chunk(11L, 1L, "Doc", 1, "v1",  1); // passes
        // no chunk for doc 2 chunkIndex 0 → cross filtered

        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(row(1L, 1), row(2L, 1)));
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(v2Chunk, v1Chunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(
                List.of(stale, fresh, cross));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(11L);
    }

    // -----------------------------------------------------------------------
    // 7. RRF: documentVersion 从 Redis 候选透传到 FusedRetrievalCandidate
    // -----------------------------------------------------------------------
    @Test
    void rrfPreservesDocumentVersionFromRedisCandidate() {
        RrfFusionService rrf = new RrfFusionService();
        List<RetrievalCandidate> redisCandidates = List.of(
                new RetrievalCandidate(1L, 0, 0.9, 1, RetrievalSource.REDIS_VECTOR, 2));
        List<RetrievalCandidate> esCandidates = List.of(
                new RetrievalCandidate(1L, 0, 8.0, 1, RetrievalSource.ELASTICSEARCH_BM25, null));

        List<FusedRetrievalCandidate> fused = rrf.fuse(redisCandidates, esCandidates, 5);

        assertThat(fused).hasSize(1);
        // Redis version wins (non-null takes priority)
        assertThat(fused.get(0).getDocumentVersion()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // 8. RRF: ES-only candidate 透传其版本
    // -----------------------------------------------------------------------
    @Test
    void rrfPreservesDocumentVersionFromEsOnlyCandidate() {
        RrfFusionService rrf = new RrfFusionService();
        List<RetrievalCandidate> esCandidates = List.of(
                new RetrievalCandidate(2L, 1, 5.0, 1, RetrievalSource.ELASTICSEARCH_BM25, 3));

        List<FusedRetrievalCandidate> fused = rrf.fuse(List.of(), esCandidates, 5);

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).getDocumentVersion()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // 9. RRF: 两路版本均为 null 时 FusedRetrievalCandidate.documentVersion 为 null
    // -----------------------------------------------------------------------
    @Test
    void rrfDocumentVersionIsNullWhenBothSourcesHaveNoVersion() {
        RrfFusionService rrf = new RrfFusionService();
        List<RetrievalCandidate> redis = List.of(
                new RetrievalCandidate(1L, 0, 0.9, 1, RetrievalSource.REDIS_VECTOR));
        List<RetrievalCandidate> es = List.of(
                new RetrievalCandidate(1L, 0, 5.0, 1, RetrievalSource.ELASTICSEARCH_BM25));

        List<FusedRetrievalCandidate> fused = rrf.fuse(redis, es, 5);

        assertThat(fused.get(0).getDocumentVersion()).isNull();
    }

    // -----------------------------------------------------------------------
    // 10. assembleHits: chunk.documentVersion 为 null 时不被过滤（向后兼容）
    // -----------------------------------------------------------------------
    @Test
    void chunkWithNullVersionIsNotFilteredForBackwardCompatibility() {
        FusedRetrievalCandidate candidate = candidate(1L, 0, 0.03, null);

        DocumentChunk nullVersionChunk = chunk(10L, 1L, "Doc", 0, "content", null);
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of(row(1L, 1)));
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(nullVersionChunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(candidate));

        // null documentVersion → passes through (version check skipped)
        assertThat(hits).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // 11. assembleHits: activeVersion 查不到的文档，chunk 不被过滤
    // -----------------------------------------------------------------------
    @Test
    void chunkIsNotFilteredWhenActiveVersionMissing() {
        FusedRetrievalCandidate candidate = candidate(99L, 0, 0.03, 1);

        DocumentChunk chunk = chunk(10L, 99L, "Unknown", 0, "content", 1);
        // doc 99 不在 activeVersionMap 中
        when(documentRepository.findActiveVersionsByIds(any()))
                .thenReturn(List.of());
        when(chunkRepository.findCandidateChunksWithDocument(any(), any()))
                .thenReturn(List.of(chunk));

        List<RetrievalHit> hits = retrievalResultService.assembleHits(List.of(candidate));

        assertThat(hits).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // 12. assembleHits 空候选列表直接返回空
    // -----------------------------------------------------------------------
    @Test
    void emptyInputReturnsEmpty() {
        assertThat(retrievalResultService.assembleHits(List.of())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private FusedRetrievalCandidate candidate(Long docId, Integer chunkIndex,
                                               double score, Integer version) {
        return new FusedRetrievalCandidate(
                docId, chunkIndex, score,
                Set.of(RetrievalSource.REDIS_VECTOR), version);
    }

    private DocumentChunk chunk(Long chunkId, Long docId, String title,
                                Integer chunkIndex, String content, Integer version) {
        Document doc = new Document();
        doc.setId(docId);
        doc.setTitle(title);
        DocumentChunk c = new DocumentChunk(doc, chunkIndex, content);
        c.setId(chunkId);
        c.setDocumentVersion(version);
        return c;
    }

    private Object[] row(Long docId, Integer activeVersion) {
        return new Object[]{docId, activeVersion};
    }
}
