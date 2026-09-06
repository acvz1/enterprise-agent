package com.kb.demo.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

        return knowledgeAgentService.ask(question, modelName, scopedSessionId(request.get("sessionId")));
    }

    /**
     * 将客户端会话 ID 限定在当前登录用户命名空间内，避免不同账号共享检索上下文。
     */
    private String scopedSessionId(String clientSessionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("无法确定当前登录用户");
        }

        String normalizedSessionId = clientSessionId == null || clientSessionId.isBlank()
                ? "default"
                : clientSessionId;
        return authentication.getName() + ":" + normalizedSessionId;
    }
}
