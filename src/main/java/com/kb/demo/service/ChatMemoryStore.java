package com.kb.demo.service;

import com.kb.demo.entity.SessionMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 会话记忆存储服务
 * 使用Redis存储会话的对话历史
 * @author LiJingLin
 */
@Service
public class ChatMemoryStore {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatMemoryStore.class);
    
    private static final String KEY_PREFIX = "conversation:";
    private static final String HISTORY_SUFFIX = ":history";
    private static final String METADATA_SUFFIX = ":metadata";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplateObject;
    
    @Value("${langchain4j.conversation.max-turns:5}")
    private int maxTurns;
    
    @Value("${langchain4j.conversation.max-context-tokens:2000}")
    private int maxContextTokens;
    
    @Value("${langchain4j.conversation.session-ttl-minutes:15}")
    private int sessionTTLMinutes;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取对话历史键
     */
    private String getHistoryKey(String sessionId) {
        return KEY_PREFIX + sessionId + HISTORY_SUFFIX;
    }
    
    /**
     * 获取元数据键
     */
    private String getMetadataKey(String sessionId) {
        return KEY_PREFIX + sessionId + METADATA_SUFFIX;
    }
    
    /**
     * 添加消息到会话历史
     */
    public void addMessage(String sessionId, SessionMessage message) {
        try {
            String historyKey = getHistoryKey(sessionId);
            String metadataKey = getMetadataKey(sessionId);
            
            // 获取当前消息列表
            List<SessionMessage> messages = getMessages(sessionId);
            
            // 添加新消息
            messages.add(message);
            logger.debug("添加消息到会话: {}, 角色: {}, 消息长度: {}", 
                sessionId, message.getRole(), message.getContent().length());
            
            // 根据Token限制和轮数限制修剪消息
            messages = trimMessages(messages);
            
            // 序列化消息并保存到Redis
            List<String> serializedMessages = new ArrayList<>();
            int totalTokens = 0;
            for (SessionMessage msg : messages) {
                serializedMessages.add(objectMapper.writeValueAsString(msg));
                totalTokens += msg.estimateTokens();
            }
            
            // 删除旧数据
            redisTemplateObject.delete(historyKey);
            
            // 保存消息列表
            if (!serializedMessages.isEmpty()) {
                redisTemplateObject.opsForList().rightPushAll(historyKey, serializedMessages.toArray());
                redisTemplateObject.expire(historyKey, sessionTTLMinutes, TimeUnit.MINUTES);
            }
            
            // 更新元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("lastUpdatedAt", String.valueOf(System.currentTimeMillis()));
            metadata.put("totalMessages", String.valueOf(messages.size()));
            metadata.put("totalTurns", String.valueOf(messages.size() / 2));  // 每轮对话 = 用户问 + AI答
            metadata.put("currentTokenCount", String.valueOf(totalTokens));
            
            redisTemplateObject.opsForHash().putAll(metadataKey, metadata);
            redisTemplateObject.expire(metadataKey, sessionTTLMinutes, TimeUnit.MINUTES);
            
            logger.info("会话记忆已更新: sessionId={}, 消息数={}, Token数={}, 轮数={}", 
                sessionId, messages.size(), totalTokens, messages.size() / 2);
        } catch (Exception e) {
            logger.error("添加会话消息失败: sessionId={}", sessionId, e);
        }
    }
    
    /**
     * 获取会话的所有消息
     */
    public List<SessionMessage> getMessages(String sessionId) {
        try {
            String historyKey = getHistoryKey(sessionId);
            List<Object> serializedMessages = redisTemplateObject.opsForList().range(historyKey, 0, -1);
            
            List<SessionMessage> messages = new ArrayList<>();
            if (serializedMessages != null) {
                for (Object obj : serializedMessages) {
                    if (obj != null) {
                        SessionMessage msg = objectMapper.readValue(obj.toString(), SessionMessage.class);
                        messages.add(msg);
                    }
                }
            }
            
            return messages;
        } catch (Exception e) {
            logger.error("获取会话消息失败: sessionId={}", sessionId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取最近N轮的对话（1轮 = 用户问 + AI答 = 2条消息）
     */
    public List<SessionMessage> getLastNTurns(String sessionId, int n) {
        List<SessionMessage> allMessages = getMessages(sessionId);
        
        // 计算需要的消息数：n轮对话 = n*2条消息，且至少保留用户问题
        int needMessageCount = Math.min(n * 2, allMessages.size());
        
        if (needMessageCount == 0) {
            return new ArrayList<>();
        }
        
        return new ArrayList<>(allMessages.subList(
            allMessages.size() - needMessageCount,
            allMessages.size()
        ));
    }
    
    /**
     * 获取会话的最近3轮对话（用于提示词上下文）
     */
    public List<SessionMessage> getRecentContext(String sessionId) {
        return getLastNTurns(sessionId, 3);
    }
    
    /**
     * 根据Token和轮数限制修剪消息
     */
    private List<SessionMessage> trimMessages(List<SessionMessage> messages) {
        // 先检查轮数限制（5轮 = 10条消息）
        int maxMessages = maxTurns * 2;
        if (messages.size() > maxMessages) {
            messages = new ArrayList<>(messages.subList(
                messages.size() - maxMessages,
                messages.size()
            ));
            logger.debug("超过最大轮数({})，已修剪消息，保留{}条", maxTurns, messages.size());
        }
        
        // 再检查Token限制
        int currentTokens = 0;
        List<SessionMessage> trimmedMessages = new ArrayList<>();
        
        // 从后往前遍历，保留最新的消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            SessionMessage msg = messages.get(i);
            int messageTokens = msg.estimateTokens();
            
            if (currentTokens + messageTokens <= maxContextTokens) {
                trimmedMessages.add(0, msg);
                currentTokens += messageTokens;
            }
        }
        
        if (trimmedMessages.size() < messages.size()) {
            logger.debug("超过最大Token数({})，已修剪消息，保留{}条", maxContextTokens, trimmedMessages.size());
        }
        
        return trimmedMessages;
    }
    
    /**
     * 清除会话
     */
    public void clear(String sessionId) {
        try {
            redisTemplateObject.delete(getHistoryKey(sessionId));
            redisTemplateObject.delete(getMetadataKey(sessionId));
            logger.info("会话已清除: sessionId={}", sessionId);
        } catch (Exception e) {
            logger.error("清除会话失败: sessionId={}", sessionId, e);
        }
    }
    
    /**
     * 获取会话元数据
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadata(String sessionId) {
        try {
            Map<Object, Object> metadata = redisTemplateObject.opsForHash().entries(getMetadataKey(sessionId));
            if (metadata == null || metadata.isEmpty()) {
                return new HashMap<>();
            }
            return new HashMap<>((Map<String, Object>) (Map) metadata);
        } catch (Exception e) {
            logger.error("获取会话元数据失败: sessionId={}", sessionId, e);
            return new HashMap<>();
        }
    }
    
    /**
     * 检查会话是否存在
     */
    public boolean sessionExists(String sessionId) {
        try {
            return Boolean.TRUE.equals(redisTemplateObject.hasKey(getHistoryKey(sessionId)));
        } catch (Exception e) {
            logger.error("检查会话失败: sessionId={}", sessionId, e);
            return false;
        }
    }
}
