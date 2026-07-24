package com.kb.demo.dto;

import com.kb.demo.dto.RetrievalHit;
import java.util.List;

public class AgentResponse {
    private String answer;
    private String model;
    private boolean toolUsed;
    private List<String> toolNames;
    private List<RetrievalHit> citations;
    
    public AgentResponse(String answer, String model, boolean toolUsed, List<String> toolNames,
            List<RetrievalHit> citations) {
        this.answer = answer;
        this.model = model;
        this.toolUsed = toolUsed;
        this.toolNames = toolNames;
        this.citations = citations;
    }

    public String getAnswer() {
        return answer;
    }

    public String getModel() {
        return model;
    }

    public boolean isToolUsed() {
        return toolUsed;
    }

    public List<String> getToolNames() {
        return toolNames;
    }

    public List<RetrievalHit> getCitations() {
        return citations;
    }
    
    
}
