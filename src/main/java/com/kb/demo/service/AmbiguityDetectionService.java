package com.kb.demo.service;

import com.kb.demo.dto.RetrievalHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 检索结果歧义检测（纯规则，不调用 LLM）。
 *
 * 判断 TopK 命中是否来自多个不同文档/主题且分数接近（无明显唯一主导）。
 * 满足时返回一个基于实际命中标题生成的澄清问题，而不是让 LLM 猜测答案。
 */
@Service
public class AmbiguityDetectionService {

    /** 文档最高分之间允许的最小分差；低于该值视为「无明显主导」。 */
    @Value("${app.query-understanding.ambiguity-score-gap:0.05}")
    private double ambiguityScoreGap;

    /** 至少命中多少个不同文档才进入歧义判断；同一文档多 chunk 不构成歧义。 */
    @Value("${app.query-understanding.ambiguity-min-documents:2}")
    private int minDocuments;

    /**
     * 若 TopK 命中存在明显主题竞争，返回澄清问题；否则返回 empty。
     */
    public Optional<String> detectClarification(List<RetrievalHit> hits) {
        if (hits == null || hits.size() < 2) {
            return Optional.empty();
        }

        // 每个文档取最高分 chunk 作为该文档的代表分（避免同一文档多 chunk 误判）
        Map<Long, Double> topScoreByDocument = new LinkedHashMap<>();
        for (RetrievalHit hit : hits) {
            if (hit.getDocumentId() == null) {
                continue;
            }
            topScoreByDocument.merge(hit.getDocumentId(), hit.getFusionScore(), Math::max);
        }

        if (topScoreByDocument.size() < minDocuments) {
            return Optional.empty();
        }

        List<Map.Entry<Long, Double>> sortedDocs = topScoreByDocument.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .toList();

        double top1 = sortedDocs.get(0).getValue();
        double top2 = sortedDocs.get(1).getValue();

        // 第一名明显主导 → 不歧义
        if (top1 - top2 >= ambiguityScoreGap) {
            return Optional.empty();
        }

        // 基于实际命中生成候选主题（去重，最多 3 个）
        List<String> titles = hits.stream()
                .filter(h -> h.getDocumentId() != null)
                .sorted(Comparator.comparingDouble(RetrievalHit::getFusionScore).reversed())
                .map(RetrievalHit::getDocumentTitle)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .limit(3)
                .toList();

        if (titles.size() < 2) {
            return Optional.empty();
        }

        return Optional.of("你想问 " + String.join("、", titles) + " 哪一个？");
    }
}
