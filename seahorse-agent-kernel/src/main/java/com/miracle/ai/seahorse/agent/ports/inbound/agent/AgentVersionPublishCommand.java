package com.miracle.ai.seahorse.agent.ports.inbound.agent;

public record AgentVersionPublishCommand(String instructions,
                                         String toolSetJson,
                                         String modelConfigJson,
                                         String memoryConfigJson,
                                         String guardrailConfigJson,
                                         String skillSetJson,
                                         String changeSummary) {

    public AgentVersionPublishCommand(String instructions,
                                      String toolSetJson,
                                      String modelConfigJson,
                                      String memoryConfigJson,
                                      String guardrailConfigJson,
                                      String changeSummary) {
        this(instructions, toolSetJson, modelConfigJson, memoryConfigJson, guardrailConfigJson, null, changeSummary);
    }
}
