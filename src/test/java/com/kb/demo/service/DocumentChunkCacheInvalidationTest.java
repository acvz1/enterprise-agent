package com.kb.demo.service;

import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.entity.DocumentIndexSyncTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentChunkCacheInvalidationTest {

    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private RedisVectorIndexService redisVectorIndexService;
    @Mock private ElasticsearchSearchService elasticsearchSearchService;
    @Mock private AiService aiService;
    @Mock private DocumentIndexSyncTaskService indexSyncTaskService;
    @Mock private ApplicationContext applicationContext;
    @InjectMocks private DocumentChunkService documentChunkService;

    @Test
    void invalidatesAnswersBeforeDeletingDocumentChunks() throws Exception {
        when(elasticsearchSearchService.deleteByDocumentId(7L)).thenReturn(0L);
        when(elasticsearchSearchService.countByDocumentId(7L)).thenReturn(0L);
        when(redisVectorIndexService.countByDocumentId(7L)).thenReturn(0L);
        when(documentChunkRepository.countByDocumentId(7L)).thenReturn(0L);
        when(indexSyncTaskService.claim(7L)).thenReturn(Optional.of(
                new DocumentIndexSyncTaskService.SyncAttempt(
                        7L, DocumentIndexSyncTask.Operation.DELETE, 1L, "attempt-1")));
        when(applicationContext.getBean(DocumentChunkService.class)).thenReturn(documentChunkService);

        documentChunkService.deleteChunksByDocumentId(7L);

        InOrder inOrder = inOrder(aiService, redisVectorIndexService, documentChunkRepository);
        inOrder.verify(aiService).invalidateAnswersByDocumentId(7L);
        inOrder.verify(redisVectorIndexService).deleteByDocumentId(7L);
        inOrder.verify(documentChunkRepository).deleteByDocumentId(7L);
        verify(elasticsearchSearchService).deleteByDocumentId(7L);
        verify(elasticsearchSearchService).refreshIndex();
        verify(indexSyncTaskService).markSuccess(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onlyWritesOutboxBeforeTheBusinessTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            documentChunkService.deleteChunksByDocumentId(7L);

            verify(indexSyncTaskService).request(7L, DocumentIndexSyncTask.Operation.DELETE);
            verifyNoInteractions(redisVectorIndexService, elasticsearchSearchService, aiService);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
