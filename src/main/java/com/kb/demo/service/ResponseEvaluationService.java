package com.kb.demo.service;

import com.kb.demo.entity.AnswerEvaluation;
import com.kb.demo.repository.AnswerEvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 问答结果评估服务
 * 用于评估回答的相关性、完整性和幻觉程度
 * @author LiJingLin
 */
@Service
public class ResponseEvaluationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseEvaluationService.class);
    
    @Autowired
    private AnswerEvaluationRepository answerEvaluationRepository;
    
    /**
     * 评估问答回答
     * @param sessionId 会话ID
     * @param question 用户问题
     * @param answer AI回答
     * @param model 使用的模型
     * @param responseTime 响应时间
     * @param retrievedDocCount 检索到的文档数
     * @return 评估结果
     */
    @Transactional
    public AnswerEvaluation evaluateAnswer(
            String sessionId,
            String question,
            String answer,
            String model,
            Long responseTime,
            Integer retrievedDocCount) {
        
        // 计算相关性评分（0-1）
        // 如果没有检索到相关文档，降低相关性评分
        double relevanceScore = calculateRelevanceScore(question, answer, retrievedDocCount);
        
        // 计算完整性评分（0-1）
        // 根据回答长度和信息密度
        double completenessScore = calculateCompletenessScore(answer);
        
        // 计算幻觉程度（0-1）
        // 0表示完全基于知识库，1表示严重幻觉
        double hallucinationScore = calculateHallucinationScore(answer, retrievedDocCount);
        
        // 计算综合评分
        // 公式: (相关性*0.4 + 完整性*0.4 + (1-幻觉)*0.2)
        double overallScore = (relevanceScore * 0.4) 
                            + (completenessScore * 0.4) 
                            + ((1 - hallucinationScore) * 0.2);
        
        // 根据评分判断评估级别
        AnswerEvaluation.EvaluationLevel level = AnswerEvaluation.EvaluationLevel.fromScore(overallScore);
        
        // 创建评估记录
        AnswerEvaluation evaluation = new AnswerEvaluation();
        evaluation.setSessionId(sessionId);
        evaluation.setQuestion(question);
        evaluation.setAnswer(answer);
        evaluation.setRelevanceScore(relevanceScore);
        evaluation.setCompletenessScore(completenessScore);
        evaluation.setHallucination(hallucinationScore);
        evaluation.setOverallScore(overallScore);
        evaluation.setEvaluationLevel(level);
        evaluation.setModel(model);
        evaluation.setResponseTime(responseTime);
        evaluation.setRetrievedDocCount(retrievedDocCount);
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setUpdatedAt(LocalDateTime.now());
        
        answerEvaluationRepository.save(evaluation);
        
        logger.info("问答评估完成: sessionId={}, 相关性={:.2f}, 完整性={:.2f}, 幻觉={:.2f}, 综合={:.2f}, 级别={}",
                sessionId, relevanceScore, completenessScore, hallucinationScore, overallScore, level.getDescription());
        
        return evaluation;
    }
    
    /**
     * 计算相关性评分
     * 如果有检索结果，评分较高；如果没有检索结果或很少，评分较低
     */
    private double calculateRelevanceScore(String question, String answer, Integer retrievedDocCount) {
        // 如果检索到了文档，基础分数较高
        double baseScore = retrievedDocCount > 0 ? 0.7 : 0.3;
        
        // 检查回答中是否包含"知识库"、"没有"等否定词
        // 这些词通常表示没有找到相关信息
        if (answer.contains("知识库") && (answer.contains("没有") || answer.contains("不存在"))) {
            return 0.4;
        }
        
        // 如果回答很短，可能不够相关
        if (answer.length() < 10) {
            return Math.max(0.2, baseScore - 0.3);
        }
        
        // 如果检索到多个文档，提高评分
        if (retrievedDocCount > 3) {
            baseScore += 0.1;
        }
        
        return Math.min(1.0, baseScore);
    }
    
    /**
     * 计算完整性评分
     * 根据回答长度、句子数和逻辑结构
     */
    private double calculateCompletenessScore(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return 0.0;
        }
        
        // 计数句子数（以。！？作为分隔符）
        String cleanAnswer = answer.trim();
        int sentenceCount = cleanAnswer.split("[。！？\\.!?]").length - 1;
        
        // 计数段落数
        int paragraphCount = cleanAnswer.split("\n").length;
        
        // 计数逻辑连接词（表示有层次的回答）
        int logicalConnectors = 0;
        String[] connectors = {"首先", "其次", "最后", "另外", "此外", "因此", "所以", "总结"};
        for (String connector : connectors) {
            if (answer.contains(connector)) {
                logicalConnectors++;
            }
        }
        
        // 评分逻辑
        double score = 0.0;
        
        // 基于长度评分（200字以上为优秀）
        int charCount = answer.length();
        if (charCount < 50) {
            score += 0.1;
        } else if (charCount < 100) {
            score += 0.3;
        } else if (charCount < 200) {
            score += 0.5;
        } else if (charCount < 500) {
            score += 0.7;
        } else {
            score += 0.9;
        }
        
        // 基于句子数评分（3-5句为理想）
        if (sentenceCount >= 3 && sentenceCount <= 5) {
            score += 0.2;
        } else if (sentenceCount > 5) {
            score += 0.1;
        }
        
        // 基于逻辑连接词评分（有逻辑结构加分）
        if (logicalConnectors > 0) {
            score += Math.min(0.1, logicalConnectors * 0.05);
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * 计算幻觉程度（0-1，0表示无幻觉，1表示严重幻觉）
     * 幻觉特征：
     * - 包含知识库中明确说没有的信息
     * - 自信地陈述虚假信息
     */
    private double calculateHallucinationScore(String answer, Integer retrievedDocCount) {
        // 如果没有检索到任何文档，风险很高
        if (retrievedDocCount == 0) {
            // 检查是否是"不知道"回答
            if (answer.contains("知识库") && answer.contains("没有")) {
                return 0.2;  // 正确的拒绝回答，幻觉低
            }
            return 0.8;  // 没有检索结果却给出回答，风险高
        }
        
        // 检查明确的幻觉标志
        String[] hallucinationMarkers = {
            "我确定", "我肯定", "绝对", "100%",  // 过度自信
            "根据我的记忆", "我记得", "我知道"    // 虚假来源
        };
        
        for (String marker : hallucinationMarkers) {
            if (answer.contains(marker) && retrievedDocCount < 2) {
                return 0.6;
            }
        }
        
        // 检查常见的幻觉模式（回答与"知识库"这个短语不一致）
        int confidenceLevel = 0;
        if (answer.contains("根据") || answer.contains("根据提供的")) {
            confidenceLevel--;
        }
        
        // 基础幻觉评分（检索到文档时，幻觉程度降低）
        double hallucinationScore = 0.3 - (retrievedDocCount * 0.05);
        
        return Math.max(0.0, Math.min(1.0, hallucinationScore));
    }
    
    /**
     * 记录用户反馈（点赞/点踩）
     * @param evaluationId 评估ID
     * @param feedback 反馈: -1(差), 0(中立), 1(好)
     */
    @Transactional
    public void recordUserFeedback(Long evaluationId, Integer feedback) {
        AnswerEvaluation evaluation = answerEvaluationRepository.findById(evaluationId)
                .orElse(null);
        
        if (evaluation != null) {
            evaluation.setUserFeedback(feedback);
            evaluation.setUpdatedAt(LocalDateTime.now());
            answerEvaluationRepository.save(evaluation);
            
            logger.info("用户反馈已记录: evaluationId={}, feedback={}", evaluationId, feedback);
        }
    }
    
    /**
     * 获取评估统计信息
     */
    public Map<String, Object> getEvaluationStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 获取今日所有评估
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        // TODO: 添加查询逻辑，计算：
        // - 今日问答总数
        // - 平均评分
        // - 各级别分布
        // - 用户满意度
        
        return stats;
    }
}
