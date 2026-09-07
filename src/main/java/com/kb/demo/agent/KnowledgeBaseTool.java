package com.kb.demo.agent;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;

import com.kb.demo.service.HybridRetrievalService;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import com.kb.demo.dto.RetrievalHit;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class KnowledgeBaseTool {
    private final HybridRetrievalService hybridRetrievalService;

    public KnowledgeBaseTool(HybridRetrievalService hybridRetrievalService) {
        this.hybridRetrievalService = hybridRetrievalService;
    }

    @Tool("搜索企业知识库。当用户询问公司制度、系统功能、业务资料或文档内容时调用")
    public List<RetrievalHit> searchKnowledgeBase(@P("需要在企业知识库中检索的完整问题") String query){
        if (!canReadKnowledgeBase()) {
            return List.of();
        }

        try{
            return hybridRetrievalService.searchHits(query, 10, 5);
        }catch(IOException e){
            throw new IllegalStateException("知识库检索失败",e);
        }
    }

    /** 澄清选择后仅在已展示的候选文档范围内取证，不暴露为 LLM Tool。 */
    public List<RetrievalHit> searchKnowledgeBaseInDocuments(String query, Set<Long> documentIds) {
        if (!canReadKnowledgeBase()) {
            return List.of();
        }
        try {
            return hybridRetrievalService.searchHitsInDocuments(query, 10, 5, documentIds);
        } catch (IOException e) {
            throw new IllegalStateException("知识库检索失败", e);
        }
    }

    private boolean canReadKnowledgeBase() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "document:read".equals(authority.getAuthority()));
    }
    
}
