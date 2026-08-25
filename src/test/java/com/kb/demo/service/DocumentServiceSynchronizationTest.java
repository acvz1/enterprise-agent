package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.repository.DocumentVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class DocumentServiceSynchronizationTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionService documentVersionService;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private DocumentChunkService documentChunkService;

    @Mock
    private AiService aiService;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void updateDocumentWithVersionRebuildsRetrievalDataWhenContentChanges() {
        Document stored = document(7L, "制度", "旧正文");
        Document update = document(null, "制度", "新正文");
        stubExistingDocument(stored);

        documentService.updateDocumentWithVersion(7L, update);

        verify(documentChunkService).processDocument(7L);
    }

    @Test
    void updateDocumentWithVersionDoesNotRebuildRetrievalDataWhenOnlyTitleChanges() {
        Document stored = document(7L, "旧标题", "正文不变");
        Document update = document(null, "新标题", "正文不变");
        stubExistingDocument(stored);

        documentService.updateDocumentWithVersion(7L, update);

        verify(documentChunkService, never()).processDocument(any());
    }

    @Test
    void saveDocumentWithProcessingBuildsRetrievalData() {
        Document document = document(7L, "新制度", "制度正文");
        when(documentRepository.save(document)).thenReturn(document);

        documentService.saveDocument(document, true);

        verify(documentChunkService).processDocument(7L);
    }

    @Test
    void saveDocumentDoesNotHideRetrievalSynchronizationFailure() {
        Document document = document(7L, "新制度", "制度正文");
        when(documentRepository.save(document)).thenReturn(document);
        doThrow(new IllegalStateException("Elasticsearch 不可用"))
                .when(documentChunkService).processDocument(7L);

        assertThatThrownBy(() -> documentService.saveDocument(document, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Elasticsearch 不可用");
    }

    private void stubExistingDocument(Document stored) {
        when(documentVersionService.getDocumentVersionRepository())
                .thenReturn(documentVersionRepository);
        when(documentRepository.findById(7L)).thenReturn(Optional.of(stored));
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentVersionRepository.findMaxVersionNumberByDocumentId(7L)).thenReturn(2);
    }

    private Document document(Long id, String title, String content) {
        Document document = new Document();
        document.setId(id);
        document.setTitle(title);
        document.setContent(content);
        return document;
    }
}
