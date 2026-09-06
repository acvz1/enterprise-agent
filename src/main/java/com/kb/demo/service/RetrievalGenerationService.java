package com.kb.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 管理全局检索 generation 计数器（Redis INCR）。
 *
 * 计数器语义：每次文档 activeVersion CAS 切换成功后自增一次。
 * 检索缓存键携带当前 generation，文档更新后旧缓存 key 自然失效。
 *
 * 原子性：Redis INCR 是原子操作，并发 SWITCH 各自 INCR，不会重叠。
 * 幂等性：casActiveVersion 返回 0（并发冲突/重复提交）时不调用 increment，防止误增。
 */
@Service
public class RetrievalGenerationService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalGenerationService.class);
    static final String GENERATION_KEY = "retrieval:generation";

    private final StringRedisTemplate redisTemplate;

    public RetrievalGenerationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 读取当前 generation。每次检索请求在入口处调用一次，整个请求链路使用同一值。
     * Redis 不可达时返回 "0"，缓存 key 退化为固定前缀，不影响正确性（只影响 TTL 失效精度）。
     */
    public String currentGeneration() {
        try {
            String val = redisTemplate.opsForValue().get(GENERATION_KEY);
            return val != null ? val : "0";
        } catch (Exception e) {
            log.warn("Failed to read retrieval generation from Redis, using 0", e);
            return "0";
        }
    }

    /**
     * 原子自增 generation。仅在 casActiveVersion 返回 affected=1 后调用。
     * Redis 不可达时记录警告，不抛异常，不阻断文档更新流程。
     */
    public void incrementGeneration() {
        try {
            redisTemplate.opsForValue().increment(GENERATION_KEY);
        } catch (Exception e) {
            log.warn("Failed to increment retrieval generation in Redis", e);
        }
    }
}
