package com.kb.demo.service;

import com.kb.demo.config.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiServiceEvidenceGuardTest {

    @Test
    void returnsHardRefusalWithoutCallingModelWhenNoAuthorizedEvidenceExists() throws Exception {
        ModelFactory modelFactory = mock(ModelFactory.class);
        HybridRetrievalService hybridRetrievalService = mock(HybridRetrievalService.class);
        DepartmentAccessService departmentAccessService = mock(DepartmentAccessService.class);
        when(hybridRetrievalService.searchHits("报销流程", 10, 5)).thenReturn(List.of());
        when(departmentAccessService.currentScopeCacheKey()).thenReturn("dept-10");

        AiService service = new AiService(
                modelFactory, mock(ModelConfig.class), mock(RedisTemplate.class), hybridRetrievalService,
                mock(ChatMemoryStore.class), mock(ResponseEvaluationService.class), mock(AnalyticsService.class),
                departmentAccessService);

        Map<String, Object> result = service.askQuestion("报销流程", "alice:default", "deepseek");

        assertThat(result.get("answer")).isEqualTo("未找到当前账号可访问的知识库内容，无法基于证据回答该问题。");
        assertThat(result.get("citations")).isEqualTo(List.of());
        verifyNoInteractions(modelFactory);
    }
}
