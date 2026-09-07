package com.kb.demo.service;

import com.kb.demo.config.ModelConfig;
import com.kb.demo.dto.ClarificationCandidate;
import com.kb.demo.dto.PendingClarification;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.dto.RetrievalSource;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiServicePendingClarificationTest {

    @Test
    void allFollowUpUsesOriginalQueryAndSelectedDocumentsBeforeContextEnhancement() throws Exception {
        Fixture fixture = new Fixture();
        PendingClarification pending = fixture.pending();
        when(fixture.pendingStore.current("alice:one")).thenReturn(Optional.of(pending));
        when(fixture.resolver.resolve("都问", pending))
                .thenReturn(ClarificationFollowUpResolver.Resolution.all(pending.candidates()));
        when(fixture.hybrid.searchHitsInDocuments(eq("公司年假有多长"), eq(10), eq(5),
                eq(Set.of(1L, 2L)))).thenReturn(List.of(fixture.hit(1L, "员工手册")));
        when(fixture.modelFactory.createModel("deepseek")).thenReturn(fixture.model);
        when(fixture.model.generate(anyString())).thenReturn("年假以员工手册为准。");

        Map<String, Object> response = fixture.service.askQuestion("都问", "alice:one", "deepseek");

        assertThat(response.get("answer")).isEqualTo("年假以员工手册为准。");
        assertThat((List<RetrievalHit>) response.get("citations"))
                .extracting(RetrievalHit::getDocumentId).containsExactly(1L);
        verify(fixture.pendingStore).clear("alice:one");
        verifyNoInteractions(fixture.contextEnhancer);
    }

    @Test
    void noPendingDoesNotTreatAllAsClarificationFollowUp() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.pendingStore.current("alice:one")).thenReturn(Optional.empty());
        when(fixture.contextEnhancer.enhance(eq("都问"), any()))
                .thenReturn(ContextQueryEnhancer.Enhancement.none());
        when(fixture.hybrid.searchHits("都问", 10, 5)).thenReturn(List.of());

        Map<String, Object> response = fixture.service.askQuestion("都问", "alice:one", "deepseek");

        assertThat(response.get("answer")).isEqualTo("未找到当前账号可访问的知识库内容，无法基于证据回答该问题。");
        verify(fixture.hybrid).searchHits("都问", 10, 5);
    }

    @Test
    void unresolvedFollowUpKeepsPendingAndDoesNotFallBackToNormalRetrieval() throws Exception {
        Fixture fixture = new Fixture();
        PendingClarification pending = fixture.pending();
        when(fixture.pendingStore.current("alice:one")).thenReturn(Optional.of(pending));
        when(fixture.resolver.resolve("第三个", pending))
                .thenReturn(ClarificationFollowUpResolver.Resolution.unresolved());
        when(fixture.resolver.looksLikeNewQuestion("第三个")).thenReturn(false);
        when(fixture.resolver.retryPrompt(pending)).thenReturn("请从员工手册、差旅报销制度中选择：第一个、第二个，或都问。");

        Map<String, Object> response = fixture.service.askQuestion("第三个", "alice:one", "deepseek");

        assertThat((String) response.get("answer")).contains("第一个、第二个，或都问");
        verify(fixture.pendingStore, never()).clear("alice:one");
        verifyNoInteractions(fixture.hybrid, fixture.contextEnhancer);
    }

    private static class Fixture {
        final ModelFactory modelFactory = mock(ModelFactory.class);
        final HybridRetrievalService hybrid = mock(HybridRetrievalService.class);
        final ContextQueryEnhancer contextEnhancer = mock(ContextQueryEnhancer.class);
        final PendingClarificationStore pendingStore = mock(PendingClarificationStore.class);
        final ClarificationFollowUpResolver resolver = mock(ClarificationFollowUpResolver.class);
        final ChatLanguageModel model = mock(ChatLanguageModel.class);
        final AiService service = new AiService(modelFactory, mock(ModelConfig.class), mock(RedisTemplate.class), hybrid,
                mock(ChatMemoryStore.class), mock(ResponseEvaluationService.class), mock(AnalyticsService.class),
                mock(DepartmentAccessService.class), mock(AmbiguityDetectionService.class), contextEnhancer,
                mock(RetrievalContextStore.class), pendingStore, resolver);

        PendingClarification pending() {
            return new PendingClarification("公司年假有多长", List.of(
                    new ClarificationCandidate(1L, "员工手册"),
                    new ClarificationCandidate(2L, "差旅报销制度")), "2026-09-07T00:00:00Z");
        }

        RetrievalHit hit(Long documentId, String title) {
            return new RetrievalHit(documentId, documentId, 0, title, "年假正文", 0.03,
                    Set.of(RetrievalSource.REDIS_VECTOR));
        }
    }
}
