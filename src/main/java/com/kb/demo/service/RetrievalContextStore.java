package com.kb.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 记录/读取「最近一次成功进行知识库检索的 query」，按「用户 + 会话」隔离。
 *
 * 调用方需传入已包含用户命名空间的 scoped sessionId（形如 {@code username:sessionId}），
 * 避免同一用户不同聊天会话、或不同用户相同会话字符串之间串上下文。
 *
 * 仅当检索命中（返回非空）时才记录，避免闲聊/无关 query 污染后续短 Query 的补全上下文。
 */
@Service
public class RetrievalContextStore {

    private static final String KEY_PREFIX = "retrieval:context:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.query-understanding.context-ttl-minutes:15}")
    private long contextTtlMinutes;

    public RetrievalContextStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 指定会话最近一次成功检索的 query；无记录返回 null。 */
    public String current(String scopedSessionId) {
        if (scopedSessionId == null || scopedSessionId.isBlank()) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + scopedSessionId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 记录指定会话最近一次成功检索 query（命中知识库后调用）。 */
    public void record(String scopedSessionId, String retrievalQuery) {
        if (scopedSessionId == null || scopedSessionId.isBlank()
                || retrievalQuery == null || retrievalQuery.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + scopedSessionId, retrievalQuery, contextTtlMinutes, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // 上下文记录失败不影响主流程
        }
    }
}
