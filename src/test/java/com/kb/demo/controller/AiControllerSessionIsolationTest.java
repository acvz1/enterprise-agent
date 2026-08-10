package com.kb.demo.controller;

import com.kb.demo.service.AiService;
import com.kb.demo.service.MetricsService;
import com.kb.demo.service.ResponseEvaluationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiControllerSessionIsolationTest {

    @Mock
    private AiService aiService;
    @Mock
    private MetricsService metricsService;
    @Mock
    private ResponseEvaluationService responseEvaluationService;

    private AiController controller;

    @BeforeEach
    void setUp() {
        controller = new AiController();
        ReflectionTestUtils.setField(controller, "aiService", aiService);
        ReflectionTestUtils.setField(controller, "metricsService", metricsService);
        ReflectionTestUtils.setField(controller, "responseEvaluationService", responseEvaluationService);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("alice", null, java.util.List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        controller.shutdown();
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopesExplicitClientSessionToAuthenticatedUser() {
        when(aiService.askQuestion("问题", "alice:session-1")).thenReturn(Map.of());

        controller.ask(Map.of("question", "问题", "sessionId", "session-1"));

        verify(aiService).askQuestion("问题", "alice:session-1");
    }

    @Test
    void usesPerUserDefaultWhenClientSessionIsMissing() {
        when(aiService.askQuestion("问题", "alice:default")).thenReturn(Map.of());

        controller.ask(Map.of("question", "问题"));

        verify(aiService).askQuestion("问题", "alice:default");
    }
}
