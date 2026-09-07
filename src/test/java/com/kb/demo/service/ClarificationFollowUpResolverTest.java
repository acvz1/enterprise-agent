package com.kb.demo.service;

import com.kb.demo.dto.ClarificationCandidate;
import com.kb.demo.dto.PendingClarification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationFollowUpResolverTest {

    private ClarificationFollowUpResolver resolver;
    private PendingClarification pending;

    @BeforeEach
    void setUp() {
        resolver = new ClarificationFollowUpResolver();
        ReflectionTestUtils.setField(resolver, "followUpMaxLength", 12);
        pending = new PendingClarification("公司年假有多长", List.of(
                new ClarificationCandidate(1L, "员工手册"),
                new ClarificationCandidate(2L, "差旅报销制度")), "2026-09-07T00:00:00Z");
    }

    @Test
    void resolvesOrdinalAndExplicitCandidateLabel() {
        assertThat(resolver.resolve("第一个", pending).candidates())
                .extracting(candidate -> candidate.documentId()).containsExactly(1L);
        assertThat(resolver.resolve("2", pending).candidates())
                .extracting(candidate -> candidate.documentId()).containsExactly(2L);
        assertThat(resolver.resolve("员工手册那个", pending).candidates())
                .extracting(candidate -> candidate.documentId()).containsExactly(1L);
    }

    @Test
    void resolvesAllWithoutUsingThePhraseAsAQuery() {
        assertThat(resolver.resolve("都问", pending).type())
                .isEqualTo(ClarificationFollowUpResolver.Type.ALL);
        assertThat(resolver.resolve("都要", pending).candidates())
                .extracting(candidate -> candidate.documentId()).containsExactly(1L, 2L);
    }

    @Test
    void unresolvedFollowUpKeepsPendingWhileCompleteQuestionCanReplaceIt() {
        assertThat(resolver.resolve("第三个", pending).resolved()).isFalse();
        assertThat(resolver.looksLikeNewQuestion("第三个")).isFalse();
        assertThat(resolver.looksLikeNewQuestion("新的报销标准是多少？")).isTrue();
    }
}
