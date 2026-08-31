package com.kb.demo.judge;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DraftJudgeService {

    private static final Logger log = LoggerFactory.getLogger(DraftJudgeService.class);

    private static final String JUDGE_PROMPT_TEMPLATE =
            "你是一个分类器，判断下面的用户问题是否涉及企业内部事实（公司制度、流程、系统功能、业务数据、人事规定等）。\n\n" +
            "只输出以下三个词之一，不得输出任何其他内容：\n" +
            "  SAFE_GENERAL   — 通用问候、闲聊、或完全不需要企业知识库就能回答的问题\n" +
            "  REQUIRES_KB    — 明确涉及企业内部事实，必须查询知识库才能安全作答\n" +
            "  UNCERTAIN      — 无法确定，保守处理\n\n" +
            "用户问题：\n%s\n\n" +
            "分类结果：";

    /**
     * 调用 model 对 question 进行一次性判定。
     * 任何异常均向上抛出，由调用方实施 fail-safe。
     */
    public JudgeVerdict judge(ChatLanguageModel model, String question) {
        String prompt = String.format(JUDGE_PROMPT_TEMPLATE, question);
        String raw = model.generate(prompt).trim().toUpperCase();
        log.debug("Judge raw output for [{}]: {}", question, raw);

        if (raw.contains("SAFE_GENERAL")) return JudgeVerdict.SAFE_GENERAL;
        if (raw.contains("REQUIRES_KB"))  return JudgeVerdict.REQUIRES_KB;
        if (raw.contains("UNCERTAIN"))    return JudgeVerdict.UNCERTAIN;

        // 输出不合规 → 保守处理
        log.warn("Judge returned unrecognised token '{}', treating as UNCERTAIN", raw);
        return JudgeVerdict.UNCERTAIN;
    }
}
