package com.kb.demo.service;

import com.kb.demo.entity.DocumentIndexSyncTask;
import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 版本化 REBUILD 切换 + GC 的幂等语义单元测试。
 *
 * 直接驱动 {@link DocumentChunkService#applyVersionSwitch} —— 这是从 runRebuild 尾部抽出的
 * 决策方法，隔离了 SWITCH / ALREADY_ACTIVE / CONFLICT 三种结局，避免每次重放误回收 serving 数据。
 */
@ExtendWith(MockitoExtension.class)
class DocumentChunkVersionSwitchTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private RedisVectorIndexService redisVectorIndexService;
    @Mock private ElasticsearchSearchService elasticsearchSearchService;
    @Mock private AiService aiService;
    @Mock private DocumentIndexSyncTaskService indexSyncTaskService;
    @Mock private RetrievalGenerationService retrievalGenerationService;

    @InjectMocks private DocumentChunkService documentChunkService;

    private static final long DOC = 7L;

    // -----------------------------------------------------------------------
    // 1. null -> v1：首次建索引成功，不 GC v1
    // -----------------------------------------------------------------------
    @Test
    void nullToV1_firstBuild_noGcAndNoGenerationBump() throws Exception {
        int chunks = documentChunkService.applyVersionSwitch(attempt(1), DOC, 3, null, 1);

        assertThat(chunks).isEqualTo(3);
        verify(indexSyncTaskService).markSuccess(anyAttempt());
        // 无旧版本可切：不执行 CAS、不 increment、不失效缓存
        verifyNoInteractions(documentRepository, retrievalGenerationService, aiService);
        assertNoGcOfAnyVersion();
    }

    // -----------------------------------------------------------------------
    // 2. v1 -> v2：真实 SWITCH，GC v1，保留 v2，generation +1
    // -----------------------------------------------------------------------
    @Test
    void v1ToV2_realSwitch_gcOldVersionKeepNewAndBumpGeneration() throws Exception {
        when(documentRepository.casActiveVersion(DOC, 1, 2)).thenReturn(1);

        int chunks = documentChunkService.applyVersionSwitch(attempt(2), DOC, 5, 1, 2);

        assertThat(chunks).isEqualTo(5);
        verify(indexSyncTaskService).markSuccess(anyAttempt());
        verify(retrievalGenerationService).incrementGeneration();
        verify(aiService).invalidateAnswersByDocumentId(DOC);
        // 只 GC 旧版本 v1，绝不动新版本 v2
        verifyGcOfVersion(1);
        assertNoGcOfVersion(2);
    }

    // -----------------------------------------------------------------------
    // 3. v1 -> v1：幂等 replay，不 GC v1，v1 数据保留，generation 不增
    // -----------------------------------------------------------------------
    @Test
    void v1ToV1_idempotentReplay_keepServingV1_noGc_noGenerationBump() throws Exception {
        int chunks = documentChunkService.applyVersionSwitch(attempt(1), DOC, 4, 1, 1);

        assertThat(chunks).isEqualTo(4);
        verify(indexSyncTaskService).markSuccess(anyAttempt());
        // v1 数据仍在 serving：不回收任何版本，也不做 CAS / generation
        verifyNoInteractions(documentRepository, retrievalGenerationService, aiService);
        assertNoGcOfAnyVersion();
    }

    // -----------------------------------------------------------------------
    // 4. CAS conflict：不 GC 任何版本，generation 不增
    // -----------------------------------------------------------------------
    @Test
    void casConflict_noGcOfAnyVersion_noGenerationBump() throws Exception {
        when(documentRepository.casActiveVersion(DOC, 1, 2)).thenReturn(0);

        int chunks = documentChunkService.applyVersionSwitch(attempt(2), DOC, 5, 1, 2);

        assertThat(chunks).isEqualTo(5);
        verify(indexSyncTaskService).markSuccess(anyAttempt());
        verifyNoInteractions(retrievalGenerationService, aiService);
        assertNoGcOfAnyVersion();
    }

    // -----------------------------------------------------------------------
    // 5. SWITCH 成功(v1->v2)后，任务再次 replay(v2 active, target v2)：不得删 v2
    // -----------------------------------------------------------------------
    @Test
    void postSwitchReplay_matchingActiveVersion_mustNotDeleteCurrentActiveVersion() throws Exception {
        // v1->v2 已切换，DB activeVersion=2；重放任务 targetVersion=2 → ALREADY_ACTIVE
        int chunks = documentChunkService.applyVersionSwitch(attempt(2), DOC, 5, 2, 2);

        assertThat(chunks).isEqualTo(5);
        verify(indexSyncTaskService).markSuccess(anyAttempt());
        verifyNoInteractions(documentRepository, retrievalGenerationService, aiService);
        assertNoGcOfAnyVersion();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private DocumentIndexSyncTaskService.SyncAttempt attempt(int targetVersion) {
        return new DocumentIndexSyncTaskService.SyncAttempt(
                DOC, DocumentIndexSyncTask.Operation.REBUILD, 1L, "tok", targetVersion);
    }

    private static DocumentIndexSyncTaskService.SyncAttempt anyAttempt() {
        return org.mockito.ArgumentMatchers.any(DocumentIndexSyncTaskService.SyncAttempt.class);
    }

    private void assertNoGcOfAnyVersion() throws Exception {
        assertNoGcOfVersion(1);
        assertNoGcOfVersion(2);
    }

    private void assertNoGcOfVersion(int version) throws Exception {
        verify(documentChunkRepository, never()).deleteByDocumentIdAndDocumentVersion(DOC, version);
        verify(redisVectorIndexService, never()).deleteByDocumentIdAndVersion(DOC, version);
        verify(elasticsearchSearchService, never()).deleteByDocumentIdAndVersion(DOC, version);
    }

    private void verifyGcOfVersion(int version) throws Exception {
        verify(documentChunkRepository).deleteByDocumentIdAndDocumentVersion(DOC, version);
        verify(redisVectorIndexService).deleteByDocumentIdAndVersion(DOC, version);
        verify(elasticsearchSearchService).deleteByDocumentIdAndVersion(DOC, version);
    }
}
