package com.kb.demo.repository;

import com.kb.demo.entity.AnswerEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答评估Repository
 */
@Repository
public interface AnswerEvaluationRepository extends JpaRepository<AnswerEvaluation, Long> {
    
    /**
     * 查询会话的所有评估
     */
    List<AnswerEvaluation> findBySessionId(String sessionId);
    
    /**
     * 查询指定评估级别的记录
     */
    List<AnswerEvaluation> findByEvaluationLevel(AnswerEvaluation.EvaluationLevel level);
    
    /**
     * 查询今日的所有评估（用于Dashboard）
     */
    @Query("SELECT a FROM AnswerEvaluation a WHERE a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    List<AnswerEvaluation> findTodayEvaluations(LocalDateTime startTime);
    
    /**
     * 查询特定模型的平均评分
     */
    @Query("SELECT AVG(a.overallScore) FROM AnswerEvaluation a WHERE a.model = :model")
    Double getAverageScoreByModel(String model);
    
    /**
     * 查询用户点赞最多的回答
     */
    @Query("SELECT a FROM AnswerEvaluation a WHERE a.userFeedback = 1 ORDER BY a.createdAt DESC LIMIT 10")
    List<AnswerEvaluation> findTopAnswers();
}
