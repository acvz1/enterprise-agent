package com.kb.demo.agent;
import dev.langchain4j.service.SystemMessage;

import dev.langchain4j.service.Result;

public interface KnowledgeAgent{

        @SystemMessage("""
        你是企业知识库问答 Agent。
        当问题涉及企业文档、公司制度、业务资料或系统信息时，
        必须先调用 searchKnowledgeBase 工具，再根据工具结果回答。
        对于问候、自我介绍等不需要知识库的问题，不调用工具，直接回答。
        不得编造工具没有返回的信息。
        """)
    Result<String> chat(String question);
}