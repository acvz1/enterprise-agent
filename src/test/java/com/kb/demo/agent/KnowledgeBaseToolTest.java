package com.kb.demo.agent;

import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.service.HybridRetrievalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseToolTest {

    @Mock
    private HybridRetrievalService hybridRetrievalService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsBeforeRetrievalWhenDocumentReadPermissionIsMissing() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "qa-only-user",
                        null,
                        List.of(new SimpleGrantedAuthority("qa:ask"))));
        KnowledgeBaseTool tool = new KnowledgeBaseTool(hybridRetrievalService);

        List<RetrievalHit> result = tool.searchKnowledgeBase("内部制度是什么");

        assertThat(result).isEmpty();
        verify(hybridRetrievalService, never()).searchHits("内部制度是什么", 10, 5);
    }

    @Test
    void retrievesKnowledgeWhenDocumentReadPermissionExists() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "reader",
                        null,
                        List.of(new SimpleGrantedAuthority("document:read"))));
        RetrievalHit hit = new RetrievalHit();
        when(hybridRetrievalService.searchHits("内部制度是什么", 10, 5))
                .thenReturn(List.of(hit));
        KnowledgeBaseTool tool = new KnowledgeBaseTool(hybridRetrievalService);

        List<RetrievalHit> result = tool.searchKnowledgeBase("内部制度是什么");

        assertThat(result).containsExactly(hit);
        verify(hybridRetrievalService).searchHits("内部制度是什么", 10, 5);
    }
}
