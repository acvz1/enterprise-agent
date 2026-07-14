package com.kb.demo.service;

import com.kb.demo.entity.AnswerEvaluation;
import com.kb.demo.repository.AnswerEvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 数据分析服务
 * 提供Dashboard所需的统计数据
 */
@Service
public class AnalyticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    
    @Autowired
    private AnswerEvaluationRepository answerEvaluationRepository;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    // 缓存命中计数器键前缀
    private static final String CACHE_HIT_KEY = "analytics:cache:hit";
    private static final String CACHE_MISS_KEY = "analytics:cache:miss";
    
    /**
     * 获取Dashboard综合统计数据
     */
    public Map<String, Object> getDashboardStats() {
        logger.info("开始获取Dashboard统计数据");
        
        Map<String, Object> stats = new HashMap<>();
        
        // 今日起始时间
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        // 获取今日所有评估记录
        List<AnswerEvaluation> todayEvaluations = answerEvaluationRepository.findTodayEvaluations(startOfDay);
        
        // 获取所有历史评估记录（用于整体统计）
        List<AnswerEvaluation> allEvaluations = answerEvaluationRepository.findAll();
        
        // 1. 今日问答数量
        stats.put("todayQaCount", todayEvaluations.size());
        
        // 2. 整体平均评分（所有历史数据）
        double avgScore = allEvaluations.stream()
                .mapToDouble(AnswerEvaluation::getOverallScore)
                .average()
                .orElse(0.0);
        stats.put("todayAvgScore", Math.round(avgScore * 100.0) / 100.0);
        
        // 3. 评分分布统计（所有历史数据）
        Map<String, Long> scoreDistribution = allEvaluations.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getEvaluationLevel().getDescription(),
                        Collectors.counting()
                ));
        stats.put("scoreDistribution", scoreDistribution);
        
        // 4. 模型响应时间对比（所有历史数据）
        Map<String, Double> modelResponseTimes = allEvaluations.stream()
                .collect(Collectors.groupingBy(
                        AnswerEvaluation::getModel,
                        Collectors.averagingLong(AnswerEvaluation::getResponseTime)
                ));
        stats.put("modelResponseTimes", modelResponseTimes);
        
        // 5. 缓存命中率
        stats.put("cacheHitRate", calculateCacheHitRate());
        
        // 6. 最近问答记录（最新10条，所有历史数据）
        List<Map<String, Object>> recentQa = allEvaluations.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .map(this::convertToQaRecord)
                .collect(Collectors.toList());
        stats.put("recentQa", recentQa);
        
        // 7. 各模型平均评分（所有历史数据）
        Map<String, Double> modelAvgScores = allEvaluations.stream()
                .collect(Collectors.groupingBy(
                        AnswerEvaluation::getModel,
                        Collectors.averagingDouble(AnswerEvaluation::getOverallScore)
                ));
        stats.put("modelAvgScores", modelAvgScores);
        
        // 8. 评分指标平均值（所有历史数据）
        Map<String, Double> avgMetrics = new HashMap<>();
        avgMetrics.put("relevance", allEvaluations.stream()
                .mapToDouble(AnswerEvaluation::getRelevanceScore).average().orElse(0.0));
        avgMetrics.put("completeness", allEvaluations.stream()
                .mapToDouble(AnswerEvaluation::getCompletenessScore).average().orElse(0.0));
        avgMetrics.put("hallucination", allEvaluations.stream()
                .mapToDouble(AnswerEvaluation::getHallucination).average().orElse(0.0));
        stats.put("avgMetrics", avgMetrics);
        
        logger.info("Dashboard统计数据获取完成: 今日问答={}, 历史总数={}, 平均评分={}", 
                todayEvaluations.size(), allEvaluations.size(), avgScore);
        
        return stats;
    }
    
    /**
     * 计算缓存命中率
     */
    private Map<String, Object> calculateCacheHitRate() {
        String hitCount = redisTemplate.opsForValue().get(CACHE_HIT_KEY);
        String missCount = redisTemplate.opsForValue().get(CACHE_MISS_KEY);
        
        long hits = hitCount != null ? Long.parseLong(hitCount) : 0;
        long misses = missCount != null ? Long.parseLong(missCount) : 0;
        long total = hits + misses;
        
        double hitRate = total > 0 ? (double) hits / total * 100 : 0.0;
        
        Map<String, Object> cacheStats = new HashMap<>();
        cacheStats.put("hits", hits);
        cacheStats.put("misses", misses);
        cacheStats.put("total", total);
        cacheStats.put("hitRate", Math.round(hitRate * 100.0) / 100.0);
        
        return cacheStats;
    }
    
    /**
     * 转换评估记录为前端展示格式
     */
    private Map<String, Object> convertToQaRecord(AnswerEvaluation evaluation) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", evaluation.getId());
        record.put("question", evaluation.getQuestion());
        record.put("answer", truncateText(evaluation.getAnswer(), 100));
        record.put("score", evaluation.getOverallScore());
        record.put("level", evaluation.getEvaluationLevel().getDescription());
        record.put("model", evaluation.getModel());
        record.put("responseTime", evaluation.getResponseTime());
        record.put("createdAt", evaluation.getCreatedAt().toString());
        return record;
    }
    
    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        redisTemplate.opsForValue().increment(CACHE_HIT_KEY);
        redisTemplate.expire(CACHE_HIT_KEY, 24, TimeUnit.HOURS);
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        redisTemplate.opsForValue().increment(CACHE_MISS_KEY);
        redisTemplate.expire(CACHE_MISS_KEY, 24, TimeUnit.HOURS);
    }
    
    /**
     * 重置缓存统计
     */
    public void resetCacheStats() {
        redisTemplate.delete(CACHE_HIT_KEY);
        redisTemplate.delete(CACHE_MISS_KEY);
        logger.info("缓存统计已重置");
    }
}
