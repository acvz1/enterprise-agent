package com.kb.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 问答评估DTO
 */
public class AnswerEvaluationDTO {
    
    @JsonProperty("evaluationId")
    private Long evaluationId;
    
    @JsonProperty("relevanceScore")
    private Double relevanceScore;
    
    @JsonProperty("completenessScore")
    private Double completenessScore;
    
    @JsonProperty("hallucinationScore")
    private Double hallucinationScore;
    
    @JsonProperty("overallScore")
    private Double overallScore;
    
    @JsonProperty("evaluationLevel")
    private String evaluationLevel;  // EXCELLENT/GOOD/FAIR/POOR
    
    @JsonProperty("userFeedback")
    private Integer userFeedback;  // -1/0/1
    
    // Getters and Setters
    public Long getEvaluationId() {
        return evaluationId;
    }
    
    public void setEvaluationId(Long evaluationId) {
        this.evaluationId = evaluationId;
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
    
    public Double getHallucinationScore() {
        return hallucinationScore;
    }
    
    public void setHallucinationScore(Double hallucinationScore) {
        this.hallucinationScore = hallucinationScore;
    }
    
    public Double getOverallScore() {
        return overallScore;
    }
    
    public void setOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }
    
    public String getEvaluationLevel() {
        return evaluationLevel;
    }
    
    public void setEvaluationLevel(String evaluationLevel) {
        this.evaluationLevel = evaluationLevel;
    }
    
    public Integer getUserFeedback() {
        return userFeedback;
    }
    
    public void setUserFeedback(Integer userFeedback) {
        this.userFeedback = userFeedback;
    }
}
