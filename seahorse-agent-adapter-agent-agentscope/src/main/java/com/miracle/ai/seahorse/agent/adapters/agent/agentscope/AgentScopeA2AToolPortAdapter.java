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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AgentScopeA2AToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "invoke_remote_a2a_agent";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Invoke Remote A2A Agent",
            "Invoke a remote agent discovered through AgentScope A2A/Nacos within the current tenant.",
            """
                    {"type":"object","required":["agentName","prompt"],"properties":{"agentName":{"type":"string","description":"Remote agent name."},"prompt":{"type":"string","description":"Task or question to send to the remote agent."},"metadata":{"type":"object","description":"Optional string metadata for the remote A2A request."}}}
                    """);

    private final A2AAgentConnectorPort connector;

    public AgentScopeA2AToolPortAdapter(A2AAgentConnectorPort connector) {
        this.connector = Objects.requireNonNull(connector, "connector must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
            String agentName = requiredText(safeArguments, "agentName");
            String prompt = requiredText(safeArguments, "prompt");
            A2AAgentResult result = connector.invoke(new A2AAgentRequest(
                    TenantContext.get(),
                    agentName,
                    prompt,
                    metadata(safeArguments.get("metadata"))));
            return ToolInvocationResult.ok(Objects.requireNonNullElse(result.content(), ""));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("invoke_remote_a2a_agent failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private String requiredText(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text.trim();
    }

    private Map<String, String> metadata(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, metadataValue) -> {
            if (key instanceof String textKey && !textKey.isBlank() && metadataValue != null) {
                result.put(textKey.trim(), String.valueOf(metadataValue));
            }
        });
        return Map.copyOf(result);
    }
}
