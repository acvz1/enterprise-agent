package com.kb.demo.service;

import com.kb.demo.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentChunkCacheInvalidationTest {

    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private RedisVectorIndexService redisVectorIndexService;
    @Mock private ElasticsearchSearchService elasticsearchSearchService;
    @Mock private AiService aiService;
    @InjectMocks private DocumentChunkService documentChunkService;

    @Test
    void invalidatesAnswersBeforeDeletingDocumentChunks() throws Exception {
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(7L)).thenReturn(List.of());
        when(elasticsearchSearchService.deleteByDocumentId(7L)).thenReturn(0L);

        documentChunkService.deleteChunksByDocumentId(7L);

        InOrder inOrder = inOrder(aiService, redisVectorIndexService, documentChunkRepository);
        inOrder.verify(aiService).invalidateAnswersByDocumentId(7L);
        inOrder.verify(redisVectorIndexService).deleteByDocumentId(7L);
        inOrder.verify(documentChunkRepository).deleteByDocumentId(7L);
        verify(elasticsearchSearchService).deleteByDocumentId(7L);
    }
}
