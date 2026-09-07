package com.kb.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.ClarificationCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingClarificationStoreTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> values;

    private PendingClarificationStore store;
    private final Map<String, String> redis = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        store = new PendingClarificationStore(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(store, "clarificationTtlMinutes", 5L);
    }

    @Test
    void isolatesPendingClarificationByScopedSessionAndSetsTtl() {
        store.save("alice:session-a", "公司年假有多长", List.of(
                new ClarificationCandidate(1L, "员工手册"),
                new ClarificationCandidate(2L, "差旅报销制度")));

        assertThat(store.current("alice:session-a")).isPresent();
        assertThat(store.current("alice:session-b")).isEmpty();
        assertThat(store.current("bob:session-a")).isEmpty();
        verify(values).set(eq("pending:clarification:alice:session-a"), anyString(),
                eq(5L), eq(TimeUnit.MINUTES));
    }
}
