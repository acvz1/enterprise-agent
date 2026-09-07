package com.kb.demo.service;

import com.kb.demo.dto.ClarificationCandidate;
import com.kb.demo.dto.PendingClarification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** 只解析澄清状态中的选择语，不做普通 Query 的意图识别或改写。 */
@Service
public class ClarificationFollowUpResolver {

    @Value("${app.query-understanding.clarification-follow-up-max-length:12}")
    private int followUpMaxLength;

    public Resolution resolve(String input, PendingClarification pending) {
        String normalized = normalize(input);
        if (normalized.equals("都问") || normalized.equals("都要") || normalized.equals("全都要")) {
            return Resolution.all(pending.candidates());
        }
        if (isFirst(normalized)) {
            return candidateAt(pending.candidates(), 0);
        }
        if (isSecond(normalized)) {
            return candidateAt(pending.candidates(), 1);
        }
        for (ClarificationCandidate candidate : pending.candidates()) {
            String label = normalize(candidate.label());
            if (label.length() >= 2 && normalized.contains(label)) {
                return Resolution.single(candidate);
            }
        }
        return Resolution.unresolved();
    }

    /** 无法解析时，带问句形态的完整新问题可以覆盖旧澄清状态。 */
    public boolean looksLikeNewQuestion(String input) {
        String value = input == null ? "" : input.trim();
        if (value.length() > followUpMaxLength || value.endsWith("?") || value.endsWith("？")) {
            return true;
        }
        return value.contains("什么") || value.contains("多少") || value.contains("多久")
                || value.contains("怎么") || value.contains("如何") || value.contains("哪些")
                || value.contains("是否") || value.contains("能否") || value.contains("谁")
                || value.contains("几");
    }

    public String retryPrompt(PendingClarification pending) {
        String labels = pending.candidates().stream().map(ClarificationCandidate::label)
                .reduce((left, right) -> left + "、" + right).orElse("候选主题");
        return "请从 " + labels + " 中选择：第一个、第二个，或都问。";
    }

    private Resolution candidateAt(List<ClarificationCandidate> candidates, int index) {
        return candidates.size() > index ? Resolution.single(candidates.get(index)) : Resolution.unresolved();
    }

    private boolean isFirst(String value) {
        return value.equals("1") || value.equals("一") || value.equals("第一个") || value.equals("第一");
    }

    private boolean isSecond(String value) {
        return value.equals("2") || value.equals("二") || value.equals("第二个") || value.equals("第二");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s，,。！？!?]", "").trim();
    }

    public record Resolution(Type type, List<ClarificationCandidate> candidates) {
        public static Resolution single(ClarificationCandidate candidate) {
            return new Resolution(Type.SINGLE, List.of(candidate));
        }
        public static Resolution all(List<ClarificationCandidate> candidates) {
            return new Resolution(Type.ALL, List.copyOf(candidates));
        }
        public static Resolution unresolved() {
            return new Resolution(Type.UNRESOLVED, List.of());
        }
        public boolean resolved() {
            return type == Type.SINGLE || type == Type.ALL;
        }
    }

    public enum Type { SINGLE, ALL, UNRESOLVED }
}
