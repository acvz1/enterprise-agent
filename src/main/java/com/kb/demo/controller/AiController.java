package com.kb.demo.controller;

import com.kb.demo.service.AiService;
import com.kb.demo.service.MetricsService;
import com.kb.demo.service.ResponseEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI控制器
 * 提供AI问答功能，支持普通请求和流式响应
 * @author LiJingLin
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger logger = LoggerFactory.getLogger(AiController.class);
    
    @Autowired
    private AiService aiService;
    
    @Autowired
    private ResponseEvaluationService responseEvaluationService;
    
    @Autowired
    private MetricsService metricsService;

    private final ExecutorService executor = new DelegatingSecurityContextExecutorService(
        Executors.newCachedThreadPool()
    );

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String sessionId = request.get("sessionId");
        String model = request.get("model"); // 可选参数，不指定则使用默认模型
        
        // 记录问答请求
        metricsService.recordQARequest();
        var timer = metricsService.startQATimer();
        
        Map<String, Object> result;
        if (model != null && !model.isEmpty()) {
            metricsService.recordModelUsage(model);
            result = aiService.askQuestion(question, sessionId, model);
        } else {
            result = aiService.askQuestion(question, sessionId);
        }
        
        // 记录响应时间
        metricsService.recordQAResponseTime(timer);
        
        return result;
    }

    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin(originPatterns = {"*"}, allowCredentials = "true")  // 使用 originPatterns
    public SseEmitter askStream(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String sessionId = request.get("sessionId");
        String model = request.get("model"); // 可选参数，不指定则使用默认模型
        
        logger.info("收到流式请求 - 问题: {}, 会话ID: {}, 模型: {}", question, sessionId, model);
        
        // 记录问答请求和模型使用
        metricsService.recordQARequest();
        if (model != null && !model.isEmpty()) {
            metricsService.recordModelUsage(model);
        }
        
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        // DelegatingSecurityContextExecutorService 会自动传递 SecurityContext
        executor.execute(() -> {
            try {
                logger.info("开始处理流式请求 - 使用模型: {}", model != null && !model.isEmpty() ? model : "默认模型");
                if (model != null && !model.isEmpty()) {
                    aiService.askQuestionStream(question, sessionId, emitter, model);
                } else {
                    aiService.askQuestionStream(question, sessionId, emitter);
                }
                logger.info("流式请求处理完成");
            } catch (Exception e) {
                logger.error("处理流式请求时发生错误: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    @PostMapping("/clear-cache")
    public Map<String, String> clearCache() {
        // 清除所有缓存，不限制于特定会话
        aiService.clearAllCache();
        
        return Map.of("message", "缓存已清除");
    }
    
    @GetMapping("/models")
    public Map<String, Object> getAvailableModels() {
        // 获取可用的模型列表
        return aiService.getAvailableModels();
    }
    
    @GetMapping("/session/{sessionId}")
    public Map<String, Object> getSessionInfo(@PathVariable String sessionId) {
        // 获取会话信息
        return aiService.getSessionInfo(sessionId);
    }
    
    /**
     * 评估问答结果
     * 在前端展示完整回答后调用此接口
     */
    @PostMapping("/evaluate")
    public Map<String, Object> evaluateAnswer(@RequestBody Map<String, Object> request) {
        try {
            String sessionId = (String) request.get("sessionId");
            String question = (String) request.get("question");
            String answer = (String) request.get("answer");
            String model = (String) request.get("model");
            Long responseTime = ((Number) request.getOrDefault("responseTime", 0)).longValue();
            Integer retrievedDocCount = ((Number) request.getOrDefault("retrievedDocCount", 0)).intValue();
            
            var evaluation = responseEvaluationService.evaluateAnswer(
                    sessionId, question, answer, model, responseTime, retrievedDocCount);
            
            // 记录评分分布
            metricsService.recordRating(evaluation.getOverallScore().intValue());
            
            logger.info("问答评估保存: evaluationId={}, 综合评分={}", evaluation.getId(), evaluation.getOverallScore());
            
            return Map.of(
                    "success", true,
                    "evaluationId", evaluation.getId(),
                    "relevanceScore", evaluation.getRelevanceScore(),
                    "completenessScore", evaluation.getCompletenessScore(),
                    "hallucinationScore", evaluation.getHallucination(),
                    "overallScore", evaluation.getOverallScore(),
                    "evaluationLevel", evaluation.getEvaluationLevel().getDescription()
            );
            
        } catch (Exception e) {
            logger.error("问答评估失败", e);
            return Map.of(
                    "success", false,
                    "message", "评估失败: " + e.getMessage()
            );
        }
    }
    
    /**
     * 记录用户反馈
     * @param evaluationId 评估ID
     * @param feedback 反馈: -1(差), 0(中立), 1(好)
     */
    @PostMapping("/feedback/{evaluationId}")
    public Map<String, Object> recordFeedback(
            @PathVariable Long evaluationId,
            @RequestParam Integer feedback) {
        try {
            responseEvaluationService.recordUserFeedback(evaluationId, feedback);
            return Map.of("success", true, "message", "反馈已保存");
        } catch (Exception e) {
            logger.error("保存反馈失败", e);
            return Map.of("success", false, "message", "保存反馈失败");
        }
    }
}