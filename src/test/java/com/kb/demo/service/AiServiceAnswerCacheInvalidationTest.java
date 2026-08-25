package com.kb.demo.service;

import com.kb.demo.config.ModelConfig;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.dto.RetrievalSource;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceAnswerCacheInvalidationTest {

    @Mock private ModelFactory modelFactory;
    @Mock private ModelConfig modelConfig;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private HybridRetrievalService hybridRetrievalService;
    @Mock private ChatMemoryStore chatMemoryStore;
    @Mock private ResponseEvaluationService responseEvaluationService;
    @Mock private AnalyticsService analyticsService;
    @Mock private DepartmentAccessService departmentAccessService;
    @Mock private ChatLanguageModel chatModel;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        aiService = new AiService(modelFactory, modelConfig, redisTemplate, hybridRetrievalService,
                chatMemoryStore, responseEvaluationService, analyticsService, departmentAccessService);
    }

    @Test
    void storesReverseIndexForEveryDocumentUsedByAnAnswer() throws Exception {
        RetrievalHit firstHit = hit(7L, 71L);
        RetrievalHit secondHitFromSameDocument = hit(7L, 72L);
        RetrievalHit thirdHit = hit(8L, 81L);
        when(hybridRetrievalService.searchHits("报销流程", 10, 5))
                .thenReturn(List.of(firstHit, secondHitFromSameDocument, thirdHit));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(departmentAccessService.currentScopeCacheKey()).thenReturn("dept-10");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(modelFactory.createModel("deepseek")).thenReturn(chatModel);
        when(chatModel.generate(anyString())).thenReturn("请先提交报销单");

        aiService.askQuestion("报销流程", "alice:default", "deepseek");

        String cacheKey = "ai:answer:alice:default:dept-10:" + "报销流程".hashCode() + ":deepseek";
        verify(valueOperations).set(cacheKey, "请先提交报销单", 5L, TimeUnit.MINUTES);
        verify(setOperations).add("ai:document-cache-keys:7", cacheKey);
        verify(setOperations).add("ai:document-cache-keys:8", cacheKey);
        verify(redisTemplate).expire("ai:document-cache-keys:7", 5L, TimeUnit.MINUTES);
        verify(redisTemplate).expire("ai:document-cache-keys:8", 5L, TimeUnit.MINUTES);
    }

    @Test
    void deletesOnlyAnswersThatReferenceTheUpdatedDocument() {
        Set<String> affectedAnswerKeys = Set.of("ai:answer:a", "ai:answer:b");
        when(setOperations.members("ai:document-cache-keys:7")).thenReturn(affectedAnswerKeys);

        aiService.invalidateAnswersByDocumentId(7L);

        verify(redisTemplate).delete(affectedAnswerKeys);
        verify(redisTemplate).delete("ai:document-cache-keys:7");
    }

    private RetrievalHit hit(Long documentId, Long chunkId) {
        return new RetrievalHit(documentId, chunkId, 0, "报销制度", "正文", 0.1,
                Set.of(RetrievalSource.REDIS_VECTOR));
    }
}
