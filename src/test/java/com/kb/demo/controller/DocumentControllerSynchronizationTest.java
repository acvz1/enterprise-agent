package com.kb.demo.controller;

import com.kb.demo.dto.DocumentCreateDTO;
import com.kb.demo.entity.Document;
import com.kb.demo.service.DocumentCategoryTagService;
import com.kb.demo.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerSynchronizationTest {

    @Mock
    private DocumentService documentService;
    @Mock
    private DocumentCategoryTagService documentCategoryTagService;

    private DocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new DocumentController();
        ReflectionTestUtils.setField(controller, "documentService", documentService);
        ReflectionTestUtils.setField(controller, "documentCategoryTagService", documentCategoryTagService);
        when(documentService.saveDocument(any(Document.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void plainDocumentCreationRequestsImmediateRetrievalSynchronization() {
        Document document = new Document();

        controller.createDocument(document);

        verify(documentService).saveDocument(document, true);
    }

    @Test
    void categorizedDocumentCreationRequestsImmediateRetrievalSynchronization() {
        DocumentCreateDTO request = new DocumentCreateDTO();
        request.setTitle("制度");
        request.setContent("正文");

        controller.createDocumentWithCategoriesAndTags(request);

        verify(documentService).saveDocument(any(Document.class), org.mockito.ArgumentMatchers.eq(true));
    }
}
