package com.example.movierecommendation.service.ai;

import java.util.ArrayList;
import java.util.List;

public class ChatAgentPlan {
    private String intent = "SMALL_TALK";
    private double confidence;
    private String missingInfo = "";
    private String responseGuidance = "";
    private List<ToolCall> toolCalls = new ArrayList<>();

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getMissingInfo() { return missingInfo; }
    public void setMissingInfo(String missingInfo) { this.missingInfo = missingInfo; }
    public String getResponseGuidance() { return responseGuidance; }
    public void setResponseGuidance(String responseGuidance) { this.responseGuidance = responseGuidance; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls; }

    public static class ToolCall {
        private String name;
        private String arguments = "{}";

        public ToolCall() {}

        public ToolCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }
    }
}
