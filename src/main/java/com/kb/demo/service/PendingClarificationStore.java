package com.kb.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.ClarificationCandidate;
import com.kb.demo.dto.PendingClarification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 独立保存澄清选择状态；不能与最近检索 query 的上下文状态混用。 */
@Service
public class PendingClarificationStore {

    private static final String KEY_PREFIX = "pending:clarification:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.query-understanding.clarification-ttl-minutes:5}")
    private long clarificationTtlMinutes;

    public PendingClarificationStore(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<PendingClarification> current(String scopedSessionId) {
        if (scopedSessionId == null || scopedSessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key(scopedSessionId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, PendingClarification.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void save(String scopedSessionId, String originalQuery, List<ClarificationCandidate> candidates) {
        if (scopedSessionId == null || scopedSessionId.isBlank()
                || originalQuery == null || originalQuery.isBlank()
                || candidates == null || candidates.size() < 2) {
            return;
        }
        try {
            PendingClarification pending = new PendingClarification(
                    originalQuery, candidates, Instant.now().toString());
            redisTemplate.opsForValue().set(
                    key(scopedSessionId), objectMapper.writeValueAsString(pending),
                    clarificationTtlMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException ignored) {
            // 澄清状态写入失败不影响当前请求；下一轮会按普通 Query 处理。
        }
    }

    public void clear(String scopedSessionId) {
        if (scopedSessionId == null || scopedSessionId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(key(scopedSessionId));
        } catch (Exception ignored) {
            // 删除失败由 TTL 兜底，不影响当前答案返回。
        }
    }

    static String key(String scopedSessionId) {
        return KEY_PREFIX + scopedSessionId;
    }
}
