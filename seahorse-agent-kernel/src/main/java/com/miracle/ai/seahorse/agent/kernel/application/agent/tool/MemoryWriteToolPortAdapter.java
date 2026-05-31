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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class MemoryWriteToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "memory_write";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Write Memory",
            "Write a high-value short-term memory for the current user. User scope is injected by the server.",
            """
                    {"type":"object","required":["content","reason"],"properties":{"content":{"type":"string"},"reason":{"type":"string"},"sourceSnapshotId":{"type":"string"}}}
                    """);

    private final MemoryEnginePort memoryEnginePort;
    private final MemoryIngestionWorkflowPort memoryIngestionWorkflowPort;
    private final MemoryGovernanceInboundPort memoryGovernancePort;
    private final AgentToolJsonSupport jsonSupport;

    public MemoryWriteToolPortAdapter(MemoryEnginePort memoryEnginePort, AgentToolJsonSupport jsonSupport) {
        this(memoryEnginePort, null, null, jsonSupport);
    }

    public MemoryWriteToolPortAdapter(MemoryEnginePort memoryEnginePort,
                                      MemoryGovernanceInboundPort memoryGovernancePort,
                                      AgentToolJsonSupport jsonSupport) {
        this(memoryEnginePort, null, memoryGovernancePort, jsonSupport);
    }

    public MemoryWriteToolPortAdapter(MemoryEnginePort memoryEnginePort,
                                      MemoryIngestionWorkflowPort memoryIngestionWorkflowPort,
                                      MemoryGovernanceInboundPort memoryGovernancePort,
                                      AgentToolJsonSupport jsonSupport) {
        this.memoryEnginePort = Objects.requireNonNull(memoryEnginePort, "memoryEnginePort must not be null");
        this.memoryIngestionWorkflowPort = memoryIngestionWorkflowPort;
        this.memoryGovernancePort = memoryGovernancePort;
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String userId = jsonSupport.string(arguments, "_seahorseUserId");
            if (userId.isBlank()) {
                return ToolInvocationResult.failed("server user scope is missing");
            }
            String content = jsonSupport.string(arguments, "content");
            if (content.isBlank()) {
                return ToolInvocationResult.failed("content is required");
            }
            MemoryWriteRequest writeRequest = MemoryWriteRequest.builder()
                    .userId(userId)
                    .conversationId(jsonSupport.string(arguments, "_seahorseConversationId"))
                    .messageId("agent-tool-" + SnowflakeIds.nextIdString())
                    .message(ChatMessage.user(content))
                    .build();
            MemoryIngestionResult ingestionResult;
            if (memoryIngestionWorkflowPort != null) {
                ingestionResult = memoryIngestionWorkflowPort.ingest(
                        MemoryIngestionCommand.toolWrite(toolCallId, writeRequest));
            } else {
                memoryEnginePort.writeMemory(writeRequest);
                ingestionResult = MemoryIngestionResult.ignored("delegated_to_memory_engine");
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("policyDecision", "ALLOW_IF_CAPTURE_POLICY_ACCEPTS");
            response.put("ingestionStatus", ingestionResult.status().name());
            response.put("ingestionAction", ingestionResult.action().name());
            response.put("memoryAction", ingestionResult.action().name());
            response.put("memoryReason", ingestionResult.reason());
            response.put("memoryOperations", ingestionResult.operations());
            response.put("layer", "SHORT_TERM");
            installGovernanceResult(userId, response);
            return ToolInvocationResult.ok(jsonSupport.write(response));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("memory_write failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private void installGovernanceResult(String userId, Map<String, Object> response) {
        if (memoryGovernancePort == null) {
            response.put("governanceStatus", "SKIPPED");
            return;
        }
        try {
            MemoryGovernanceRunResult result = memoryGovernancePort.runGovernance(
                    userId, "agent-memory-write", false);
            response.put("governanceStatus", result.errors().isEmpty() ? "OK" : "PARTIAL");
            response.put("promotedCount", result.promotedCount());
            response.put("semanticUpsertCount", result.semanticUpsertCount());
            response.put("inferredCount", result.inferredCount());
            response.put("governanceErrors", result.errors());
        } catch (RuntimeException ex) {
            response.put("governanceStatus", "FAILED");
            response.put("governanceError", Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }
}
