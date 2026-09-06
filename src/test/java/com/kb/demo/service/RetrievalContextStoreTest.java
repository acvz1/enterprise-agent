package com.kb.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalContextStoreTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RetrievalContextStore store;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RetrievalContextStore(redisTemplate);
        ReflectionTestUtils.setField(store, "contextTtlMinutes", 15L);
    }

    @Test
    void sameUserSameSession_returnsLastKbQuery() {
        when(valueOperations.get("retrieval:context:alice:chat-1")).thenReturn("正式员工年假是多少？");

        assertThat(store.current("alice:chat-1")).isEqualTo("正式员工年假是多少？");
    }

    @Test
    void sameUserDifferentSession_doNotContaminate() {
        store.record("alice:chat-1", "正式员工年假是多少？");
        store.record("alice:chat-2", "报销流程是什么？");

        when(valueOperations.get("retrieval:context:alice:chat-1")).thenReturn("正式员工年假是多少？");
        when(valueOperations.get("retrieval:context:alice:chat-2")).thenReturn("报销流程是什么？");

        assertThat(store.current("alice:chat-1")).isEqualTo("正式员工年假是多少？");
        assertThat(store.current("alice:chat-2")).isEqualTo("报销流程是什么？");
    }

    @Test
    void differentUserSameSessionString_doNotContaminate() {
        store.record("alice:chat-1", "正式员工年假是多少？");
        store.record("bob:chat-1", "打印机怎么安装？");

        verify(valueOperations).set(eq("retrieval:context:alice:chat-1"), eq("正式员工年假是多少？"), anyLong(), any());
        verify(valueOperations).set(eq("retrieval:context:bob:chat-1"), eq("打印机怎么安装？"), anyLong(), any());
    }

    @Test
    void blankQuery_notRecorded_andNullSessionId_returnsNull() {
        store.record("alice:chat-1", "   ");
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());

        assertThat(store.current(null)).isNull();
        assertThat(store.current("  ")).isNull();
    }
}
