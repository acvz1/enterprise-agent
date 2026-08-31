package com.kb.demo.judge;

public enum JudgeVerdict {
    /** 通用问候/闲聊/非企业事实，Agent 草稿可直接放行 */
    SAFE_GENERAL,
    /** 明确涉及企业制度、系统、业务数据，必须先检索知识库 */
    REQUIRES_KB,
    /** 无法判断，保守处理，强制检索 */
    UNCERTAIN
}
