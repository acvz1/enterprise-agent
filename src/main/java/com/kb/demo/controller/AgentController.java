package com.kb.demo.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kb.demo.config.ModelConfig;
import com.kb.demo.dto.AgentResponse;
import com.kb.demo.service.KnowledgeAgentService;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/agent")
public class AgentController {
    private final KnowledgeAgentService knowledgeAgentService;
    private final ModelConfig modelConfig;

    public AgentController(KnowledgeAgentService knowledgeAgentService, ModelConfig modelConfig) {
        this.knowledgeAgentService = knowledgeAgentService;
        this.modelConfig = modelConfig;
    }
    
    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('qa:ask')")
    public AgentResponse ask(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String modelName = request.get("model");

        if (modelName == null || modelName.isBlank()) {
            modelName = modelConfig.getDefaultModel();
        }

        return knowledgeAgentService.ask(question, modelName);
    }
}
