package com.kb.demo.agent;

import org.springframework.stereotype.Component;

import com.kb.demo.service.HybridRetrievalService;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import com.kb.demo.dto.RetrievalHit;

import java.io.IOException;
import java.util.List;

@Component
public class KnowledgeBaseTool {
    private final HybridRetrievalService hybridRetrievalService;

    public KnowledgeBaseTool(HybridRetrievalService hybridRetrievalService) {
        this.hybridRetrievalService = hybridRetrievalService;
    }

    @Tool("搜索企业知识库。当用户询问公司制度、系统功能、业务资料或文档内容时调用")
    public List<RetrievalHit> searchKnowledgeBase(@P("需要在企业知识库中检索的完整问题") String query){
        try{
        return hybridRetrievalService.searchHits(query, 10, 0.5, 5);
        }catch(IOException e){
            throw new IllegalStateException("知识库检索失败",e);
        }
    }
    
}
