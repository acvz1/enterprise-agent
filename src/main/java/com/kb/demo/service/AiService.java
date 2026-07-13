package com.kb.demo.service;

import com.kb.demo.config.ModelConfig;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.SessionMessage;
import com.kb.demo.repository.DocumentRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * AI服务类
 * 提供AI问答功能，支持多种模型和流式响应
 * @author LiJingLin
 */
@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    
    @Autowired
    private ModelFactory modelFactory;
    
    @Autowired
    private ModelConfig modelConfig;
    
    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private VectorSearchService vectorSearchService;
    
    @Autowired
    private ChatMemoryStore chatMemoryStore;
    
    @Autowired
    private ResponseEvaluationService responseEvaluationService;
    
    @Autowired
    private AnalyticsService analyticsService;

    public Map<String, Object> askQuestion(String question, String sessionId) {
        return askQuestion(question, sessionId, modelConfig.getDefaultModel());
    }
    
    public Map<String, Object> askQuestion(String question, String sessionId, String modelName) {
        // 生成缓存键
        String cacheKey = "ai:answer:" + sessionId + ":" + question.hashCode() + ":" + modelName;
        
        // 尝试从缓存获取答案
        String cachedAnswer = redisTemplate.opsForValue().get(cacheKey);
        if (cachedAnswer != null) {
            return Map.of("answer", cachedAnswer, "fromCache", true, "model", modelName);
        }
        
        // 使用向量检索获取相关文档
        List<Document> relevantDocuments = vectorSearchService.searchDocuments(question);
        
        // 构建上下文
        String context = relevantDocuments.stream()
                .map(doc -> "标题: " + doc.getTitle() + "\n内容: " + doc.getContent())
                .collect(Collectors.joining("\n\n"));
        
        // 构建提示词
        String prompt = "请仅根据以下知识库内容回答问题。若证据不足，请明确说明知识库中没有足够信息，不要使用模型自身知识补充事实。\n\n" +
                "知识库内容:\n" + context + "\n\n" +
                "问题: " + question;
        
        // 获取指定模型
        ChatLanguageModel model = modelFactory.createModel(modelName);
        
        // 调用模型生成答案
        String answer = model.generate(prompt);
        
        // 将答案存入缓存，有效期5分钟
        redisTemplate.opsForValue().set(cacheKey, answer, 5, TimeUnit.MINUTES);
        
        return Map.of("answer", answer, "fromCache", false, "model", modelName);
    }

    public void askQuestionStream(String question, String sessionId, SseEmitter emitter) throws IOException {
        askQuestionStream(question, sessionId, emitter, modelConfig.getDefaultModel());
    }
    
    public void askQuestionStream(String question, String sessionId, SseEmitter emitter, String modelName) throws IOException {
        logger.info("开始处理流式请求 - 问题: {}, 会话ID: {}, 模型: {}", question, sessionId, modelName);
        
        // 生成缓存键
        String cacheKey = "ai:answer:" + sessionId + ":" + question.hashCode() + ":" + modelName;
        logger.debug("缓存键: {}", cacheKey);
        
        // 尝试从缓存获取答案
        String cachedAnswer = redisTemplate.opsForValue().get(cacheKey);
        if (cachedAnswer != null) {
            logger.info("从缓存获取答案");
            // 记录缓存命中
            analyticsService.recordCacheHit();
            // 如果有缓存，逐个字符发送
            for (int i = 0; i < cachedAnswer.length(); i++) {
                String charToSend = String.valueOf(cachedAnswer.charAt(i));
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(charToSend));
                try {
                    Thread.sleep(30); // 模拟打字效果
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            emitter.send(SseEmitter.event()
                    .name("metadata")
                    .data(Map.of("fromCache", true, "model", modelName)));
            emitter.complete();
            return;
        }
        
        // 记录缓存未命中
        analyticsService.recordCacheMiss();
        
        // 获取知识库中的所有文档
        List<Document> relevantDocuments = vectorSearchService.searchDocuments(question);
        logger.info("从向量检索获取到 {} 个相关文档", relevantDocuments.size());
        
        // 改进逆辑：只有当有质量良好的检索结果时，才使用它们
        if (relevantDocuments.isEmpty()) {
            // 如果没有检索结果，也不使用所有文档，这样模型会更严格的恰应约束
            logger.info("向量检索没有找到相关文档，使用空知识库教模式回答");
        }
        
        // 获取会话的最近对话历史（最近3轮）
        List<SessionMessage> recentHistory = chatMemoryStore.getRecentContext(sessionId);
        logger.info("从会话记忆获取到 {} 条消息", recentHistory.size());
        
        // 构建增强的提示词（包含对话历史）
        String prompt = buildEnhancedPrompt(question, recentHistory, relevantDocuments);
        logger.debug("构建的提示词: {}", prompt);
        
        // 获取指定流式模型
        logger.info("开始创建流式模型: {}", modelName);
        StreamingChatLanguageModel model = modelFactory.createStreamingModel(modelName);
        logger.info("流式模型创建成功: {}", modelName);
        
        // 使用流式API生成答案
        logger.info("开始调用流式模型生成答案");
        final long startTime = System.currentTimeMillis();  // 记录开始时间
        StringBuilder fullAnswer = new StringBuilder();
        StringBuilder tokenBuffer = new StringBuilder();  // 缓冲器不是每个 token 都发送
        final int BUFFER_SIZE = 20;  // 每收集 20 个 token 发送一次
        
        // 使用流式API生成答案
        model.generate(prompt, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                try {
                    fullAnswer.append(token);
                    tokenBuffer.append(token);
                    
                    // 当缓冲区达到一定大小时，发送数据
                    if (tokenBuffer.length() >= BUFFER_SIZE) {
                        String bufferedData = tokenBuffer.toString();
                        tokenBuffer.setLength(0);  // 清空下一次的缓冲
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(bufferedData));
                    }
                } catch (IOException e) {
                    logger.error("发送流式数据失败", e);
                }
            }
            
            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    String answer = fullAnswer.toString();
                    long endTime = System.currentTimeMillis();
                    long responseTime = endTime - startTime;  // 计算响应时间（毫秒）
                    
                    // 发送缓冲区中的剩余数据
                    if (tokenBuffer.length() > 0) {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(tokenBuffer.toString()));
                        tokenBuffer.setLength(0);
                    }
                    
                    // 将完整答案存入缓存，有效期5分钟
                    redisTemplate.opsForValue().set(cacheKey, answer, 5, TimeUnit.MINUTES);
                    logger.debug("答案已存入缓存");
                    
                    // 保存用户问题和AI回答到会话记忆
                    chatMemoryStore.addMessage(sessionId, new SessionMessage("user", question));
                    chatMemoryStore.addMessage(sessionId, new SessionMessage("assistant", answer));
                    logger.info("会话记忆已保存: sessionId={}", sessionId);
                    
                    // 评估问答质量（异步，不阻塞响应）
                    try {
                        com.kb.demo.entity.AnswerEvaluation evaluation = responseEvaluationService.evaluateAnswer(
                            sessionId,
                            question,
                            answer,
                            modelName,
                            responseTime,  // 传入计算后的响应时间
                            relevantDocuments.size()   // 检索到的文档数
                        );
                        
                        // 将评分信息发送给前端
                        Map<String, Object> evaluationData = new HashMap<>();
                        evaluationData.put("evaluationId", evaluation.getId());
                        evaluationData.put("relevanceScore", evaluation.getRelevanceScore());
                        evaluationData.put("completenessScore", evaluation.getCompletenessScore());
                        evaluationData.put("hallucinationScore", evaluation.getHallucination());
                        evaluationData.put("overallScore", evaluation.getOverallScore());
                        evaluationData.put("level", evaluation.getEvaluationLevel().getDescription());
                        
                        emitter.send(SseEmitter.event()
                                .name("evaluation")
                                .data(evaluationData));
                        
                        logger.info("评估信息已发送: 综合评分={}, 响应时间={}ms", 
                                evaluation.getOverallScore(), responseTime);
                    } catch (Exception e) {
                        logger.error("评估问答质量失败", e);
                        // 评估失败不影响主流程
                    }
                    
                    emitter.send(SseEmitter.event()
                            .name("metadata")
                            .data(Map.of("fromCache", false, "model", modelName)));
                    emitter.complete();
                    logger.info("流式请求处理完成");
                } catch (IOException e) {
                    logger.error("完成流式请求失败", e);
                    emitter.completeWithError(e);
                }
            }
            
            @Override
            public void onError(Throwable error) {
                logger.error("流式模型生成答案出错", error);
                emitter.completeWithError(error);
            }
        });
    }

    public void clearSessionCache(String sessionId) {
        // 清除会话相关的所有缓存
        String pattern = "ai:answer:" + sessionId + ":*";
        redisTemplate.delete(redisTemplate.keys(pattern));
        
        // 清除会话记忆
        chatMemoryStore.clear(sessionId);
        logger.info("会话缓存和记忆已清除: sessionId={}", sessionId);
    }
    
    public void clearAllCache() {
        // 清除所有AI相关的缓存
        String pattern = "ai:answer:*";
        redisTemplate.delete(redisTemplate.keys(pattern));
    }
    
    /**
     * 构建增强的提示词（包含对话历史）- 改进版本，更强的约束
     */
    private String buildEnhancedPrompt(
        String question,
        List<SessionMessage> recentHistory,
        List<Document> documents
    ) {
        StringBuilder sb = new StringBuilder();
        
        // 系统提示 - 更强的角色定义和约束
        sb.append("你是一个专业的知识库问答助手。你的职责是根据提供的知识库内容来回答用户的问题。\n");
        sb.append("重要约束:\n");
        sb.append("1. 优先使用知识库中的内容回答问题\n");
        sb.append("2. 如果知识库中包含相关信息，请基于这些内容给出详细的回答\n");
        sb.append("3. 只有当知识库中完全没有相关信息时，才告诉用户'知识库中没有相关信息'\n");
        sb.append("4. 如果用户问关于你自身的问题（如'你是谁'、'你能做什么'），你应该回答'我是一个知识库问答助手，我可以根据我的知识库为您解答相关问题'\n");
        sb.append("\n");
        
        // 添加对话历史（如果存在）
        if (!recentHistory.isEmpty()) {
            sb.append("【对话历史】\n");
            for (SessionMessage msg : recentHistory) {
                if ("user".equalsIgnoreCase(msg.getRole())) {
                    sb.append("用户：").append(msg.getContent()).append("\n\n");
                } else {
                    sb.append("助手：").append(msg.getContent()).append("\n\n");
                }
            }
        }
        
        // 添加知识库内容 - 仅包含相关性高的内容
        if (!documents.isEmpty()) {
            sb.append("【相关知识库信息】\n");
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                sb.append("[").append(i + 1).append("] 标题：").append(doc.getTitle()).append("\n")
                  .append("内容：").append(truncateContent(doc.getContent(), 1500)).append("\n\n");  // 从 200 增加到 1500
            }
        } else {
            sb.append("【相关知识库信息】\n");
            sb.append("暂无相关知识库内容\n\n");
        }
        
        // 当前问题
        sb.append("【当前问题】\n")
          .append(question).append("\n\n");
        
        // 指引 - 更灵活的指示
        sb.append("请根据上述知识库内容回答问题。");
        sb.append("如果知识库中包含相关信息，请给出详细的回答。");
        
        return sb.toString();
    }
    
    /**
     * 截断内容，保留前N个字符
     */
    private String truncateContent(String content, int maxLength) {
        if (content.length() > maxLength) {
            return content.substring(0, maxLength) + "...";
        }
        return content;
    }
    
    // 获取可用的模型列表
    public Map<String, Object> getAvailableModels() {
        return Map.of(
            "models", List.of("qwen", "deepseek", "kimi", "ollama"),
            "default", modelConfig.getDefaultModel()
        );
    }
    
    /**
     * 获取会话信息
     */
    public Map<String, Object> getSessionInfo(String sessionId) {
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("sessionId", sessionId);
        sessionInfo.put("exists", chatMemoryStore.sessionExists(sessionId));
        
        if (chatMemoryStore.sessionExists(sessionId)) {
            List<SessionMessage> messages = chatMemoryStore.getMessages(sessionId);
            Map<String, Object> metadata = chatMemoryStore.getMetadata(sessionId);
            
            sessionInfo.put("messageCount", messages.size());
            sessionInfo.put("turns", messages.size() / 2);
            sessionInfo.put("metadata", metadata);
            sessionInfo.put("messages", messages);
        }
        
        return sessionInfo;
    }
}
