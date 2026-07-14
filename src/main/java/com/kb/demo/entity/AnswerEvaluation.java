package com.kb.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 问答评估实体
 * 用于评估和记录每条回答的质量
 * @author LiJingLin
 */
@Entity
@Table(name = "answer_evaluations")
public class AnswerEvaluation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String sessionId;  // 会话ID
    
    @Column(nullable = false)
    private String question;  // 用户问题
    
    @Column(columnDefinition = "LONGTEXT")
    private String answer;  // AI回答
    
    @Column(nullable = false)
    private Double relevanceScore = 0.0;  // 相关性评分(0-1)，是否基于检索结果
    
    @Column(nullable = false)
    private Double completenessScore = 0.0;  // 完整性评分(0-1)，是否完整回答
    
    @Column(nullable = false)
    private Double hallucination = 0.0;  // 幻觉程度(0-1)，0表示无幻觉，1表示严重幻觉
    
    @Column(nullable = false)
    private Double overallScore = 0.0;  // 综合评分 = (相关性*0.4 + 完整性*0.4 + (1-幻觉)*0.2)
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EvaluationLevel evaluationLevel;  // 评分级别：优秀/良好/一般/较差
    
    @Column(nullable = false)
    private Integer userFeedback = 0;  // 用户反馈: -1(差), 0(中立), 1(好)
    
    @Column(nullable = false)
    private String model;  // 使用的模型名称
    
    @Column(nullable = false)
    private Long responseTime;  // 响应时间（毫秒）
    
    @Column(nullable = false)
    private Integer retrievedDocCount = 0;  // 检索到的文档数
    
    @Column(columnDefinition = "TEXT")
    private String evaluationNotes;  // 评估备注
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public Double getRelevanceScore() {
        return relevanceScore;
    }
    
    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
    
    public Double getCompletenessScore() {
        return completenessScore;
    }
    
    public void setCompletenessScore(Double completenessScore) {
        this.completenessScore = completenessScore;
    }
    
    public Double getHallucination() {
        return hallucination;
    }
    
    public void setHallucination(Double hallucination) {
        this.hallucination = hallucination;
    }
    
    public Double getOverallScore() {
        return overallScore;
    }
    
    public void setOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }
    
    public EvaluationLevel getEvaluationLevel() {
        return evaluationLevel;
    }
    
    public void setEvaluationLevel(EvaluationLevel evaluationLevel) {
        this.evaluationLevel = evaluationLevel;
    }
    
    public Integer getUserFeedback() {
        return userFeedback;
    }
    
    public void setUserFeedback(Integer userFeedback) {
        this.userFeedback = userFeedback;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public Long getResponseTime() {
        return responseTime;
    }
    
    public void setResponseTime(Long responseTime) {
        this.responseTime = responseTime;
    }
    
    public Integer getRetrievedDocCount() {
        return retrievedDocCount;
    }
    
    public void setRetrievedDocCount(Integer retrievedDocCount) {
        this.retrievedDocCount = retrievedDocCount;
    }
    
    public String getEvaluationNotes() {
        return evaluationNotes;
    }
    
    public void setEvaluationNotes(String evaluationNotes) {
        this.evaluationNotes = evaluationNotes;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * 评分级别枚举
     */
    public enum EvaluationLevel {
        EXCELLENT("优秀", 0.8, 1.0),
        GOOD("良好", 0.6, 0.8),
        FAIR("一般", 0.4, 0.6),
        POOR("较差", 0.0, 0.4);
        
        private final String description;
        private final Double minScore;
        private final Double maxScore;
        
        EvaluationLevel(String description, Double minScore, Double maxScore) {
            this.description = description;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static EvaluationLevel fromScore(Double score) {
            if (score >= 0.8) return EXCELLENT;
            if (score >= 0.6) return GOOD;
            if (score >= 0.4) return FAIR;
            return POOR;
        }
    }
}
