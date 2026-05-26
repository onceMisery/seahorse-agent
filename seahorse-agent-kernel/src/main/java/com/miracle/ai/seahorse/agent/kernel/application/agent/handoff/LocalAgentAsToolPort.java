/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LocalAgentAsToolPort implements DescribedToolPort {

    public static final String TOOL_ID = "local_agent_handoff";

    private static final String TOOL_NAME = "Local Agent Handoff";
    private static final String TOOL_DESCRIPTION = "Delegate a governed local child Agent run.";
    private static final String TOOL_SCHEMA = """
            {"type":"object","properties":{"tenantId":{"type":"string"},"parentRunId":{"type":"string"},"sourceAgentId":{"type":"string"},"targetAgentId":{"type":"string"},"targetVersionId":{"type":"string"},"handoffReason":{"type":"string"},"inputSummary":{"type":"string"},"contextSummaryJson":{"type":"string"},"depth":{"type":"integer"},"ancestorAgentIds":{"type":"array","items":{"type":"string"}}},"required":["parentRunId","sourceAgentId","targetAgentId","inputSummary"]}\
            """;

    private final KernelAgentHandoffService handoffService;

    public LocalAgentAsToolPort(KernelAgentHandoffService handoffService) {
        this.handoffService = Objects.requireNonNull(handoffService, "handoffService must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(TOOL_ID, TOOL_NAME, TOOL_DESCRIPTION, TOOL_SCHEMA);
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        if (!TOOL_ID.equals(toolId)) {
            return ToolInvocationResult.failed("LOCAL_AGENT_TOOL_ID_MISMATCH");
        }
        try {
            AgentHandoff handoff = handoffService.createLocalHandoff(new AgentHandoffCreateCommand(
                    text(arguments, "tenantId"),
                    text(arguments, "parentRunId"),
                    text(arguments, "sourceAgentId"),
                    text(arguments, "targetAgentId"),
                    text(arguments, "targetVersionId"),
                    text(arguments, "handoffReason"),
                    text(arguments, "inputSummary"),
                    text(arguments, "contextSummaryJson"),
                    integer(arguments, "depth"),
                    stringList(arguments, "ancestorAgentIds"),
                    text(arguments, "traceId")));
            return ToolInvocationResult.ok("{\"handoffId\":\"" + handoff.handoffId()
                    + "\",\"childRunId\":\"" + handoff.childRunId()
                    + "\",\"status\":\"" + handoff.status().name() + "\"}");
        } catch (RuntimeException ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        }
    }

    private static String text(Map<String, Object> arguments, String key) {
        Object value = safeArguments(arguments).get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static int integer(Map<String, Object> arguments, String key) {
        Object value = safeArguments(arguments).get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static List<String> stringList(Map<String, Object> arguments, String key) {
        Object value = safeArguments(arguments).get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return List.of();
    }

    private static Map<String, Object> safeArguments(Map<String, Object> arguments) {
        return arguments == null ? Map.of() : arguments;
    }
}
