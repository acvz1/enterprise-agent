package com.kb.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 短 Query / 上下文依赖检测与检索增强（纯规则，不调用 LLM 做 rewrite）。
 *
 * 当查询很短且含指代（那/这个/呢/他们/它等）、单独语义信息不足时，
 * 尝试用「最近一次成功进行知识库检索的 query」补全检索 query；原始 query 仍保留给回答生成。
 *
 * 增强策略（不做语义替换，替换需要 LLM）：取最近一次检索 query，拼接本轮剥离
 * 指代/语气词后剩余的核心词。例如：
 *   最近检索「正式员工年假是多少」 + 本轮「那试用期呢」
 *   → retrieval query「正式员工年假是多少 试用期」
 * 同时含「年假」「试用期」两个语义，向量检索即可召回正确内容。
 */
@Service
public class ContextQueryEnhancer {

    /** 需要用户补充上下文时的统一回复文案。 */
    public static final String CLARIFY_MESSAGE = "我不确定你具体想问什么，请补充更多上下文或换一种更完整的说法。";

    @Value("${app.query-understanding.short-query-max-length:12}")
    private int shortQueryMaxLength;

    private static final String[] CONTEXT_MARKERS = {
            "那", "这个", "那个", "这些", "那些", "他们", "她们", "它们", "它", "他", "她", "呢", "吗", "啊", "呀", "吧"
    };

    public record Enhancement(String retrievalQuery, boolean clarify) {
        public static Enhancement none() {
            return new Enhancement(null, false);
        }

        public static Enhancement askClarify() {
            return new Enhancement(null, true);
        }

        public static Enhancement rewrite(String retrievalQuery) {
            return new Enhancement(retrievalQuery, false);
        }
    }

    /**
     * 返回用于检索的 query 增强结果。
     * - 正常完整 query：返回 none（不做改写）
     * - 短且含指代、且能找到最近一次成功检索 query：返回 rewrite（增强后的 retrieval query）
     * - 短且含指代、但找不到可靠上下文：返回 askClarify（要求用户澄清）
     *
     * @param lastRetrievalQuery 最近一次成功进行知识库检索的 query；可为 null
     */
    public Enhancement enhance(String query, String lastRetrievalQuery) {
        if (query == null) {
            return Enhancement.askClarify();
        }
        String trimmed = query.trim();
        if (!isContextDependent(trimmed)) {
            return Enhancement.none();
        }

        if (lastRetrievalQuery == null || lastRetrievalQuery.isBlank()) {
            return Enhancement.askClarify();
        }

        String retrievalQuery = merge(trimmed, lastRetrievalQuery);
        if (retrievalQuery == null || retrievalQuery.isBlank()) {
            return Enhancement.askClarify();
        }
        return Enhancement.rewrite(retrievalQuery);
    }

    private boolean isContextDependent(String query) {
        if (query.length() > shortQueryMaxLength) {
            return false;
        }
        for (String marker : CONTEXT_MARKERS) {
            if (query.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String merge(String query, String lastRetrievalQuery) {
        // 最近一次检索 query 去掉末尾标点
        String base = lastRetrievalQuery.replaceAll("[？?。.！!]+$", "").trim();

        // 本轮核心词：剥离指代/语气词后剩余部分
        String core = stripMarkers(query);
        if (core.isBlank()) {
            return null; // 全是语气词，无法增强
        }

        return base + " " + core;
    }

    private String stripMarkers(String query) {
        String result = query;
        for (String marker : CONTEXT_MARKERS) {
            result = result.replace(marker, "");
        }
        result = result.replaceAll("[？?。.！!\\s]+", "").trim();
        return result;
    }
}
