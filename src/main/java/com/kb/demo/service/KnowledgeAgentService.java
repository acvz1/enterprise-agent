package com.kb.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.agent.KnowledgeAgent;
import com.kb.demo.agent.KnowledgeBaseTool;
import com.kb.demo.dto.AgentResponse;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.judge.AgentPathType;
import com.kb.demo.judge.DraftJudgeService;
import com.kb.demo.judge.JudgeVerdict;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeAgentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAgentService.class);

    static final String NO_ACCESSIBLE_EVIDENCE =
            "未找到当前账号可访问的知识库内容，无法基于证据回答该问题。";

    private static final String GROUNDED_PROMPT_TEMPLATE =
            "请仅根据以下知识库内容回答问题。若证据不足，请明确说明知识库中没有足够信息，不要使用模型自身知识补充事实。\n\n" +
            "知识库内容:\n%s\n\n" +
            "问题: %s";

    private final ModelFactory modelFactory;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final DraftJudgeService draftJudgeService;
    private final ObjectMapper objectMapper;

    public KnowledgeAgentService(ModelFactory modelFactory,
                                 KnowledgeBaseTool knowledgeBaseTool,
                                 DraftJudgeService draftJudgeService,
                                 ObjectMapper objectMapper) {
        this.modelFactory = modelFactory;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.draftJudgeService = draftJudgeService;
        this.objectMapper = objectMapper;
    }

    protected KnowledgeAgent buildAgent(ChatLanguageModel model) {
        return AiServices.builder(KnowledgeAgent.class)
                .chatLanguageModel(model)
                .tools(knowledgeBaseTool)
                .build();
    }

    public AgentResponse ask(String question, String modelName) {
        ChatLanguageModel model = modelFactory.createModel(modelName);

        KnowledgeAgent agent = buildAgent(model);

        Result<String> result = agent.chat(question);
        List<String> toolNames = result.toolExecutions().stream()
                .map(e -> e.request().name())
                .collect(Collectors.toList());
        List<RetrievalHit> citations = extractCitations(result);
        boolean searchedKnowledgeBase = toolNames.contains("searchKnowledgeBase");

        if (searchedKnowledgeBase) {
            String answer = citations.isEmpty() ? NO_ACCESSIBLE_EVIDENCE : result.content();
            return new AgentResponse(answer, modelName, true, toolNames, citations,
                    AgentPathType.AGENT_TOOL_USED);
        }

        return applyJudge(question, modelName, model, result.content(), toolNames);
    }

    // -----------------------------------------------------------------

    private AgentResponse applyJudge(String question, String modelName,
                                     ChatLanguageModel model,
                                     String draft, List<String> toolNames) {
        JudgeVerdict verdict;
        try {
            verdict = draftJudgeService.judge(model, question);
            log.info("Judge verdict for [{}]: {}", question, verdict);
        } catch (Exception e) {
            log.warn("Judge failed for [{}], applying fail-safe forced retrieval", question, e);
            return forcedRetrievalResponse(question, modelName, model, toolNames,
                    AgentPathType.JUDGE_FAILURE);
        }

        switch (verdict) {
            case SAFE_GENERAL:
                return new AgentResponse(draft, modelName, !toolNames.isEmpty(), toolNames,
                        List.of(), AgentPathType.JUDGE_SAFE_GENERAL);
            case REQUIRES_KB:
                return forcedRetrievalResponse(question, modelName, model, toolNames,
                        AgentPathType.JUDGE_FORCED_RETRIEVAL);
            case UNCERTAIN:
            default:
                return forcedRetrievalResponse(question, modelName, model, toolNames,
                        AgentPathType.JUDGE_UNCERTAIN_FORCED_RETRIEVAL);
        }
    }

    private AgentResponse forcedRetrievalResponse(String question, String modelName,
                                                   ChatLanguageModel model,
                                                   List<String> originalToolNames,
                                                   AgentPathType pathType) {
        List<RetrievalHit> hits = knowledgeBaseTool.searchKnowledgeBase(question);
        if (hits.isEmpty()) {
            return new AgentResponse(NO_ACCESSIBLE_EVIDENCE, modelName, false,
                    originalToolNames, List.of(), pathType);
        }
        String context = hits.stream()
                .map(h -> "标题: " + h.getDocumentTitle()
                        + "\n分块: " + h.getChunkIndex()
                        + "\n内容: " + h.getContent())
                .collect(Collectors.joining("\n\n"));
        String prompt = String.format(GROUNDED_PROMPT_TEMPLATE, context, question);
        String answer = model.generate(prompt);
        return new AgentResponse(answer, modelName, true, originalToolNames, hits, pathType);
    }

    private List<RetrievalHit> extractCitations(Result<String> result) {
        List<RetrievalHit> citations = new ArrayList<>();
        for (ToolExecution execution : result.toolExecutions()) {
            if (!"searchKnowledgeBase".equals(execution.request().name())) continue;
            try {
                RetrievalHit[] hits = objectMapper.readValue(execution.result(), RetrievalHit[].class);
                citations.addAll(Arrays.asList(hits));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("解析知识库工具结果失败", e);
            }
        }
        return citations;
    }
}
