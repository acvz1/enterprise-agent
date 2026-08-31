package com.kb.demo.judge;

public enum AgentPathType {
    /** Agent 自主调用了 searchKnowledgeBase */
    AGENT_TOOL_USED,
    /** Judge 判定为 SAFE_GENERAL，直接放行草稿 */
    JUDGE_SAFE_GENERAL,
    /** Judge 判定为 REQUIRES_KB，强制检索后生成答案 */
    JUDGE_FORCED_RETRIEVAL,
    /** Judge 判定为 UNCERTAIN，强制检索后生成答案 */
    JUDGE_UNCERTAIN_FORCED_RETRIEVAL,
    /** Judge 执行失败，fail-safe：强制检索后生成答案 */
    JUDGE_FAILURE
}
