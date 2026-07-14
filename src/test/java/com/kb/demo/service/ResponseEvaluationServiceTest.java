package com.kb.demo.service;

import com.kb.demo.entity.AnswerEvaluation;
import com.kb.demo.repository.AnswerEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ResponseEvaluationService 单元测试
 * 测试问答评分功能
 */
@ExtendWith(MockitoExtension.class)
class ResponseEvaluationServiceTest {

    @Mock
    private AnswerEvaluationRepository answerEvaluationRepository;

    @InjectMocks
    private ResponseEvaluationService responseEvaluationService;

    @BeforeEach
    void setUp() {
        // 模拟保存操作
        when(answerEvaluationRepository.save(any(AnswerEvaluation.class)))
                .thenAnswer(invocation -> {
                    AnswerEvaluation evaluation = invocation.getArgument(0);
                    evaluation.setId(1L);
                    return evaluation;
                });
    }

    @Test
    void testEvaluateAnswer_WithGoodAnswer() {
        // Given: 准备优质问答
        String sessionId = "test-session";
        String question = "什么是 Spring Boot？";
        String answer = "Spring Boot 是一个基于 Spring 框架的开源 Java 框架，它简化了 Spring 应用的初始搭建和开发过程。" +
                "Spring Boot 提供了自动配置、嵌入式服务器、生产就绪特性等功能，使开发者能够快速构建独立的、生产级的应用程序。";
        String modelName = "qwen";
        long responseTime = 1500L;
        int retrievedDocs = 3;

        // When: 评估答案
        AnswerEvaluation evaluation = responseEvaluationService.evaluateAnswer(
                sessionId, question, answer, modelName, responseTime, retrievedDocs
        );

        // Then: 验证评分结果
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getId()).isNotNull();
        assertThat(evaluation.getSessionId()).isEqualTo(sessionId);
        assertThat(evaluation.getQuestion()).isEqualTo(question);
        assertThat(evaluation.getAnswer()).isEqualTo(answer);
        assertThat(evaluation.getModel()).isEqualTo(modelName);
        assertThat(evaluation.getResponseTime()).isEqualTo(responseTime);
        assertThat(evaluation.getRetrievedDocCount()).isEqualTo(retrievedDocs);

        // 验证评分字段
        assertThat(evaluation.getRelevanceScore()).isGreaterThan(0);
        assertThat(evaluation.getCompletenessScore()).isGreaterThan(0);
        assertThat(evaluation.getHallucination()).isGreaterThanOrEqualTo(0);
        assertThat(evaluation.getOverallScore()).isGreaterThan(0);
        assertThat(evaluation.getEvaluationLevel()).isNotNull();

        // 验证保存操作
        verify(answerEvaluationRepository, times(1)).save(any(AnswerEvaluation.class));
    }

    @Test
    void testEvaluateAnswer_WithShortAnswer() {
        // Given: 准备简短答案
        String sessionId = "test-session";
        String question = "什么是 Java？";
        String answer = "Java 是一种编程语言。";
        String modelName = "deepseek";
        long responseTime = 500L;
        int retrievedDocs = 1;

        // When: 评估答案
        AnswerEvaluation evaluation = responseEvaluationService.evaluateAnswer(
                sessionId, question, answer, modelName, responseTime, retrievedDocs
        );

        // Then: 验证简短答案的评分较低
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getCompletenessScore()).isLessThan(80);
        assertThat(evaluation.getEvaluationLevel()).isNotNull();
    }

    @Test
    void testEvaluateAnswer_WithNoRetrievedDocs() {
        // Given: 没有检索到文档
        String sessionId = "test-session";
        String question = "测试问题";
        String answer = "这是一个测试答案，内容相对完整，包含了一些详细信息。";
        String modelName = "kimi";
        long responseTime = 2000L;
        int retrievedDocs = 0;

        // When: 评估答案
        AnswerEvaluation evaluation = responseEvaluationService.evaluateAnswer(
                sessionId, question, answer, modelName, responseTime, retrievedDocs
        );

        // Then: 验证相关性评分较低（因为没有检索到文档）
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getRetrievedDocCount()).isEqualTo(0);
        assertThat(evaluation.getRelevanceScore()).isLessThanOrEqualTo(50);
    }

    @Test
    void testEvaluateAnswer_WithSlowResponse() {
        // Given: 响应时间较长
        String sessionId = "test-session";
        String question = "测试问题";
        String answer = "这是一个详细的答案，包含了多个方面的内容。";
        String modelName = "ollama";
        long responseTime = 10000L; // 10秒
        int retrievedDocs = 5;

        // When: 评估答案
        AnswerEvaluation evaluation = responseEvaluationService.evaluateAnswer(
                sessionId, question, answer, modelName, responseTime, retrievedDocs
        );

        // Then: 验证响应时间被正确记录
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getResponseTime()).isEqualTo(10000L);
    }

    @Test
    void testEvaluateAnswer_WithEmptyAnswer() {
        // Given: 空答案
        String sessionId = "test-session";
        String question = "测试问题";
        String answer = "";
        String modelName = "qwen";
        long responseTime = 100L;
        int retrievedDocs = 0;

        // When: 评估答案
        AnswerEvaluation evaluation = responseEvaluationService.evaluateAnswer(
                sessionId, question, answer, modelName, responseTime, retrievedDocs
        );

        // Then: 验证评分很低
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getCompletenessScore()).isEqualTo(0);
        assertThat(evaluation.getOverallScore()).isLessThan(30);
    }

    @Test
    void testEvaluateAnswer_CalculatesOverallScore() {
        // Given: 准备测试数据
        String sessionId = "test-session";
        String question = "Spring Boot 的主要特性是什么？";
        String answer = "Spring Boot 的主要特性包括：1. 自动配置，2. 嵌入式服务器，3. 生产就绪特性，4. 简化依赖管理。";
        String modelName = "qwen";
        long responseTime = 1000L;
        int retrievedDocs = 3;

        // When: 评估答案
        AnswerEvaluation evaluation = responseEvaluationService.evaluateAnswer(
                sessionId, question, answer, modelName, responseTime, retrievedDocs
        );

        // Then: 验证综合评分是各项评分的合理组合
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getOverallScore()).isGreaterThan(0);
        assertThat(evaluation.getOverallScore()).isLessThanOrEqualTo(1.0);
        
        // 综合评分应该接近各项评分的加权平均（考虑权重）
        double expectedScore = (evaluation.getRelevanceScore() * 0.4 +
                                evaluation.getCompletenessScore() * 0.4 +
                                (1 - evaluation.getHallucination()) * 0.2);
        assertThat(evaluation.getOverallScore()).isCloseTo(expectedScore, offset(0.01));
    }

    @Test
    void testEvaluateAnswer_AssignsCorrectLevel() {
        // Given: 准备不同质量的答案
        String sessionId = "test-session";
        String question = "测试问题";
        String modelName = "qwen";
        long responseTime = 1000L;

        // When & Then: 测试优秀答案
        String excellentAnswer = "这是一个非常详细、完整、准确的答案，包含了问题的所有方面，" +
                "并提供了深入的解释和例子，帮助读者全面理解问题的本质和解决方案。";
        AnswerEvaluation excellent = responseEvaluationService.evaluateAnswer(
                sessionId, question, excellentAnswer, modelName, responseTime, 5
        );
        assertThat(excellent.getEvaluationLevel()).isIn(
                AnswerEvaluation.EvaluationLevel.EXCELLENT,
                AnswerEvaluation.EvaluationLevel.GOOD
        );

        // When & Then: 测试较差答案
        String poorAnswer = "不知道";
        AnswerEvaluation poor = responseEvaluationService.evaluateAnswer(
                sessionId, question, poorAnswer, modelName, responseTime, 0
        );
        assertThat(poor.getEvaluationLevel()).isIn(
                AnswerEvaluation.EvaluationLevel.POOR,
                AnswerEvaluation.EvaluationLevel.FAIR
        );
    }
}
