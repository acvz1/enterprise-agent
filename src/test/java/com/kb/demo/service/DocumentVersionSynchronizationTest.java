package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentVersion;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.repository.DocumentVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentVersionSynchronizationTest {

    @Mock
    private DocumentVersionRepository documentVersionRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkService documentChunkService;
    @InjectMocks
    private DocumentVersionService documentVersionService;

    @Test
    void revertToVersionRebuildsRetrievalDataFromRestoredContent() {
        Document stored = document(7L, "当前标题", "当前正文");
        DocumentVersion targetVersion = new DocumentVersion();
        targetVersion.setTitle("历史标题");
        targetVersion.setContent("历史正文");

        when(documentRepository.existsById(7L)).thenReturn(true);
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(7L, 2))
                .thenReturn(targetVersion);
        when(documentRepository.findById(7L)).thenReturn(Optional.of(stored));
        when(documentRepository.save(stored)).thenReturn(stored);
        when(documentVersionRepository.findMaxVersionNumberByDocumentId(7L)).thenReturn(2);
        when(documentVersionRepository.save(any(DocumentVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        documentVersionService.revertToVersion(7L, 2, "tester");

        verify(documentChunkService).processDocument(7L);
    }

    private Document document(Long id, String title, String content) {
        Document document = new Document();
        document.setId(id);
        document.setTitle(title);
        document.setContent(content);
        return document;
    }
}
