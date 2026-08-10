package com.kb.demo.controller;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentVersion;
import com.kb.demo.service.DocumentService;
import com.kb.demo.service.DocumentVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentVersionControllerActorTest {

    @Mock
    private DocumentVersionService documentVersionService;
    @Mock
    private DocumentService documentService;
    @Mock
    private Authentication authentication;

    private DocumentVersionController controller;

    @BeforeEach
    void setUp() {
        controller = new DocumentVersionController();
        ReflectionTestUtils.setField(controller, "documentVersionService", documentVersionService);
        ReflectionTestUtils.setField(controller, "documentService", documentService);
        when(authentication.getName()).thenReturn("alice");
    }

    @Test
    void createVersionUsesAuthenticatedUsernameAsAuditActor() {
        DocumentVersion version = new DocumentVersion();
        version.setVersionNumber(2);
        Document document = new Document();
        when(documentVersionService.createVersion(7L, "update policy", "alice"))
                .thenReturn(version);
        when(documentService.getDocumentById(7L)).thenReturn(document);

        controller.createVersion(7L, "update policy", authentication);

        verify(documentVersionService).createVersion(7L, "update policy", "alice");
    }

    @Test
    void revertVersionUsesAuthenticatedUsernameAsAuditActor() {
        when(documentVersionService.revertToVersion(7L, 2, "alice"))
                .thenReturn(new Document());

        controller.revertToVersion(7L, 2, authentication);

        verify(documentVersionService).revertToVersion(7L, 2, "alice");
    }
}
