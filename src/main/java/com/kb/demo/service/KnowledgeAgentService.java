package com.kb.demo.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import com.kb.demo.agent.KnowledgeAgent;
import com.kb.demo.agent.KnowledgeBaseTool;
import com.kb.demo.dto.AgentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.dto.RetrievalHit;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.ArrayList;
import java.util.Arrays;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;

import java.util.List;
import java.util.ArrayList;

@Service
public class KnowledgeAgentService {
    private final ModelFactory modelFactory;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final ObjectMapper objectMapper;

    public KnowledgeAgentService(ModelFactory modelFactory, KnowledgeBaseTool knowledgeBaseTool,ObjectMapper objectMapper) {
        this.modelFactory = modelFactory;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.objectMapper=objectMapper;
    }
    
    private List<RetrievalHit> extractCitations(Result<String>result){
        List<RetrievalHit> citations=new ArrayList<>();
        for(ToolExecution execution:result.toolExecutions()){
            if(!"searchKnowledgeBase".equals(execution.request().name()))continue;
        try{
            RetrievalHit[] hits=objectMapper.readValue(execution.result(), RetrievalHit[].class);
            citations.addAll(Arrays.asList(hits));
        }catch(JsonProcessingException e){
            throw new IllegalStateException("解析知识库工具结果失败",e);
        }
    }
        return citations;
    }

    public AgentResponse ask(String question,String modelName){
        ChatLanguageModel model = modelFactory.createModel(modelName);

        KnowledgeAgent agent = AiServices.builder(KnowledgeAgent.class)
                .chatLanguageModel(model)
                .tools(knowledgeBaseTool)
                .build();

        Result<String> result=agent.chat(question);
        List<String> toolNames=result.toolExecutions().stream()
                                    .map(execution->execution.request().name())
                                    .toList();
        return new AgentResponse(result.content(), modelName, !toolNames.isEmpty(), toolNames, extractCitations(result));
    }
}
