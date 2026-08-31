package com.kb.demo.service;

import com.kb.demo.agent.KnowledgeAgent;
import com.kb.demo.agent.KnowledgeBaseTool;
import com.kb.demo.dto.AgentResponse;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.judge.AgentPathType;
import com.kb.demo.judge.DraftJudgeService;
import com.kb.demo.judge.JudgeVerdict;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeAgentServiceTest {

    @Mock KnowledgeBaseTool knowledgeBaseTool;
    @Mock DraftJudgeService draftJudgeService;
    @Mock ChatLanguageModel model;
    @Mock KnowledgeAgent agent;
    @Mock ModelFactory modelFactory;
    @Mock Result<String> agentResult;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Subclass that bypasses AiServices.builder to return the mock agent. */
    private class TestableService extends KnowledgeAgentService {
        TestableService() {
            super(modelFactory, knowledgeBaseTool, draftJudgeService, objectMapper);
        }
        @Override
        protected KnowledgeAgent buildAgent(ChatLanguageModel m) {
            return agent;
        }
    }

    private TestableService service;

    @BeforeEach
    void setUp() {
        service = new TestableService();
        when(modelFactory.createModel(anyString())).thenReturn(model);
        when(agent.chat(anyString())).thenReturn(agentResult);
    }

    // -----------------------------------------------------------------------
    // Test 1: Agent 自主调用 searchKnowledgeBase，有 citations → AGENT_TOOL_USED
    // -----------------------------------------------------------------------
    @Test
    void agentToolUsed_withCitations_returnsAgentToolUsedPath() throws Exception {
        RetrievalHit hit = hit(1L, 0, "doc", "content");
        String hitsJson = objectMapper.writeValueAsString(List.of(hit));
        stubAgentResult("年假需提前3个工作日申请。",
                List.of(toolExec("searchKnowledgeBase", "q", hitsJson)));

        AgentResponse response = service.ask("年假要提前几天申请？", "qwen");

        assertThat(response.getPathType()).isEqualTo(AgentPathType.AGENT_TOOL_USED);
        assertThat(response.isToolUsed()).isTrue();
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getAnswer()).isEqualTo("年假需提前3个工作日申请。");
        verifyNoInteractions(draftJudgeService);
    }

    // -----------------------------------------------------------------------
    // Test 2: Agent 自主调用 searchKnowledgeBase，citations 为空 → NO_ACCESSIBLE_EVIDENCE
    // -----------------------------------------------------------------------
    @Test
    void agentToolUsed_emptyCitations_returnsNoAccessibleEvidence() {
        stubAgentResult("（模型裸回答）",
                List.of(toolExec("searchKnowledgeBase", "q", "[]")));

        AgentResponse response = service.ask("年假要提前几天申请？", "qwen");

        assertThat(response.getPathType()).isEqualTo(AgentPathType.AGENT_TOOL_USED);
        assertThat(response.getAnswer()).isEqualTo(KnowledgeAgentService.NO_ACCESSIBLE_EVIDENCE);
        verifyNoInteractions(draftJudgeService);
    }

    // -----------------------------------------------------------------------
    // Test 3: Agent 未调用 Tool，Judge → SAFE_GENERAL → 放行草稿
    // -----------------------------------------------------------------------
    @Test
    void noToolCalled_judgeSafeGeneral_returnsDraft() {
        stubAgentResult("你好！", List.of());
        when(draftJudgeService.judge(model, "你好")).thenReturn(JudgeVerdict.SAFE_GENERAL);

        AgentResponse response = service.ask("你好", "qwen");

        assertThat(response.getPathType()).isEqualTo(AgentPathType.JUDGE_SAFE_GENERAL);
        assertThat(response.getAnswer()).isEqualTo("你好！");
        assertThat(response.getCitations()).isEmpty();
        verifyNoInteractions(knowledgeBaseTool);
    }

    // -----------------------------------------------------------------------
    // Test 4: Agent 未调用 Tool，Judge → REQUIRES_KB，强制检索有结果 → grounded 答案
    // -----------------------------------------------------------------------
    @Test
    void noToolCalled_judgeRequiresKb_forcedRetrievalWithHits() {
        stubAgentResult("（裸回答）", List.of());
        when(draftJudgeService.judge(model, "年假几天")).thenReturn(JudgeVerdict.REQUIRES_KB);
        when(knowledgeBaseTool.searchKnowledgeBase("年假几天"))
                .thenReturn(List.of(hit(1L, 0, "年假政策", "年假5天")));
        when(model.generate(anyString())).thenReturn("年假5天。");

        AgentResponse response = service.ask("年假几天", "qwen");

        assertThat(response.getPathType()).isEqualTo(AgentPathType.JUDGE_FORCED_RETRIEVAL);
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getAnswer()).isEqualTo("年假5天。");
        assertThat(response.isToolUsed()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Test 5: Agent 未调用 Tool，Judge → REQUIRES_KB，强制检索无结果 → NO_ACCESSIBLE_EVIDENCE
    // -----------------------------------------------------------------------
    @Test
    void noToolCalled_judgeRequiresKb_forcedRetrievalEmpty_returnsNoEvidence() {
        stubAgentResult("（裸回答）", List.of());
        when(draftJudgeService.judge(model, "年假几天")).thenReturn(JudgeVerdict.REQUIRES_KB);
        when(knowledgeBaseTool.searchKnowledgeBase("年假几天")).thenReturn(List.of());

        AgentResponse response = service.ask("年假几天", "qwen");

        assertThat(response.getPathType()).isEqualTo(AgentPathType.JUDGE_FORCED_RETRIEVAL);
        assertThat(response.getAnswer()).isEqualTo(KnowledgeAgentService.NO_ACCESSIBLE_EVIDENCE);
        assertThat(response.getCitations()).isEmpty();
        verify(model, never()).generate(anyString());
    }

    // -----------------------------------------------------------------------
    // Test 6: Agent 未调用 Tool，Judge → UNCERTAIN → JUDGE_UNCERTAIN_FORCED_RETRIEVAL
    // -----------------------------------------------------------------------
    @Test
    void noToolCalled_judgeUncertain_forcedRetrievalPath() {
        stubAgentResult("（裸回答）", List.of());
        when(draftJudgeService.judge(model, "系统能做什么")).thenReturn(JudgeVerdict.UNCERTAIN);
        when(knowledgeBaseTool.searchKnowledgeBase("系统能做什么"))
                .thenReturn(List.of(hit(2L, 1, "系统介绍", "系统支持报销申请")));
        when(model.generate(anyString())).thenReturn("系统支持报销申请。");

        AgentResponse response = service.ask("系统能做什么", "qwen");

        assertThat(response.getPathType()).isEqualTo(AgentPathType.JUDGE_UNCERTAIN_FORCED_RETRIEVAL);
        assertThat(response.getCitations()).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Test 7: Judge 抛出异常 → fail-safe → JUDGE_FAILURE + 强制检索
    // -----------------------------------------------------------------------
    @Test
    void judgeThrows_failSafe_forcedRetrievalWithJudgeFailurePath() {
        stubAgentResult("（裸回答）", List.of());
        when(draftJudgeService.judge(any(), anyString()))
                .thenThrow(new RuntimeException("LLM timeout"));
        when(knowledgeBaseTool.searchKnowledgeBase(anyString()))
                .thenReturn(List.of(hit(3L, 0, "报销流程", "报销需要凭证")));
        when(model.generate(anyString())).thenReturn("报销需要凭证。");

        AgentResponse response = service.ask("报销流程是什么", "qwen");

        assertThat(response.getPathType()).isEqualTo(AgentPathType.JUDGE_FAILURE);
        assertThat(response.getCitations()).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Test 8: Judge 最多调用一次，不循环
    // -----------------------------------------------------------------------
    @Test
    void judgeIsCalledAtMostOnce() {
        stubAgentResult("（裸回答）", List.of());
        when(draftJudgeService.judge(model, "测试问题")).thenReturn(JudgeVerdict.REQUIRES_KB);
        when(knowledgeBaseTool.searchKnowledgeBase("测试问题")).thenReturn(List.of());

        service.ask("测试问题", "qwen");

        verify(draftJudgeService, times(1)).judge(any(), eq("测试问题"));
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private void stubAgentResult(String content, List<ToolExecution> executions) {
        when(agentResult.content()).thenReturn(content);
        when(agentResult.toolExecutions()).thenReturn(executions);
    }

    private ToolExecution toolExec(String name, String args, String toolResult) {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .name(name).arguments(args).build();
        return ToolExecution.builder().request(req).result(toolResult).build();
    }

    private RetrievalHit hit(Long docId, int chunkIndex, String title, String content) {
        return new RetrievalHit(docId, null, chunkIndex, title, content, 0.0, Set.of());
    }
}
