package com.kb.demo.entity;

import java.io.Serializable;

/**
 * 会话消息实体
 * 用于存储对话历史中的每条消息
 * @author LiJingLin
 */
public class SessionMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String role;           // "user" 或 "assistant"
    private String content;        // 消息内容
    private long timestamp;        // 消息时间戳
    
    public SessionMessage() {
    }
    
    public SessionMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
    
    public SessionMessage(String role, String content, long timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }
    
    // 估算Token数量（简单启发式方法）
    public int estimateTokens() {
        if (content == null) {
            return 0;
        }
        int tokenCount = 0;
        for (char c : content.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {  // 汉字
                tokenCount++;  // 汉字算1个Token
            }
        }
        // 英文字符估算：约4个字符=1个Token
        int englishCount = content.length() - tokenCount;
        tokenCount += englishCount / 4;
        return Math.max(tokenCount, 10);  // 最少10个Token
    }
    
    // Getters and Setters
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "SessionMessage{" +
                "role='" + role + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
