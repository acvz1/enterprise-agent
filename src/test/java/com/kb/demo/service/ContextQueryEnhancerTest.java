package com.kb.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ContextQueryEnhancerTest {

    private ContextQueryEnhancer enhancer;

    @BeforeEach
    void setUp() {
        enhancer = new ContextQueryEnhancer();
        ReflectionTestUtils.setField(enhancer, "shortQueryMaxLength", 12);
    }

    @Test
    void shortPronounQueryWithContext_completesRetrievalQuery() {
        ContextQueryEnhancer.Enhancement result = enhancer.enhance("那试用期呢？", "正式员工年假是多少？");

        assertThat(result.clarify()).isFalse();
        assertThat(result.retrievalQuery()).contains("年假").contains("试用期");
    }

    @Test
    void shortPronounQueryWithoutContext_clarifies() {
        ContextQueryEnhancer.Enhancement result = enhancer.enhance("那这个呢？", null);

        assertThat(result.clarify()).isTrue();
        assertThat(result.retrievalQuery()).isNull();
    }

    @Test
    void fullQuery_doesNotRewrite() {
        ContextQueryEnhancer.Enhancement result =
                enhancer.enhance("正式员工年假是多少天？", null);

        assertThat(result.clarify()).isFalse();
        assertThat(result.retrievalQuery()).isNull();
    }

    @Test
    void irrelevantPreviousContext_preservesBothSidesWithoutHardSemanticMerge() {
        // 纯规则（不调 LLM）无法判断「打印机怎么安装」与「试用期」是否相关。
        // 增强策略保留最近检索原文 + 本轮核心词，语义相关性交由下游向量检索判定，
        // 而不是硬编码成一个错误的语义短语（如「打印机试用期」）。
        ContextQueryEnhancer.Enhancement result = enhancer.enhance("那试用期呢？", "打印机怎么安装？");

        assertThat(result.clarify()).isFalse();
        assertThat(result.retrievalQuery()).contains("打印机").contains("试用期");
        // 不产生硬拼接语义：核心词「试用期」作为独立 token 保留，未被改写进上轮主语
        assertThat(result.retrievalQuery()).doesNotContain("打印机试用期");
    }

    @Test
    void blankContext_clarifies() {
        ContextQueryEnhancer.Enhancement result = enhancer.enhance("那试用期呢？", "   ");

        assertThat(result.clarify()).isTrue();
        assertThat(result.retrievalQuery()).isNull();
    }
}
