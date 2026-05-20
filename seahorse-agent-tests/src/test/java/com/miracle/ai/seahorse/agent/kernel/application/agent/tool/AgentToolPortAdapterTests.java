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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolPortAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(objectMapper);

    @Test
    void searchKnowledgeBaseReturnsStructuredChunksAndClampsTopK() throws Exception {
        KernelRetrievalEngine retrievalEngine = mock(KernelRetrievalEngine.class);
        when(retrievalEngine.retrieveKnowledgeChannels(any(), anyInt()))
                .thenReturn(List.of(RetrievedChunk.builder()
                        .id("chunk-1")
                        .kbId("kb-1")
                        .docId("doc-1")
                        .text("hit")
                        .score(0.91F)
                        .metadata(Map.of("source", "unit"))
                        .build()));

        ToolInvocationResult result = new SearchKnowledgeBaseToolPortAdapter(retrievalEngine, jsonSupport)
                .invoke("call-1", SearchKnowledgeBaseToolPortAdapter.TOOL_ID,
                        Map.of("query", "student policy", "topK", 99));

        assertThat(result.success()).isTrue();
        JsonNode body = objectMapper.readTree(result.content());
        assertThat(body.path("topK").asInt()).isEqualTo(20);
        assertThat(body.path("resultCount").asInt()).isEqualTo(1);
        assertThat(body.path("chunks").get(0).path("chunkId").asText()).isEqualTo("chunk-1");
    }

    @Test
    void searchKnowledgeBaseRejectsBlankQuery() {
        KernelRetrievalEngine retrievalEngine = mock(KernelRetrievalEngine.class);

        ToolInvocationResult result = new SearchKnowledgeBaseToolPortAdapter(retrievalEngine, jsonSupport)
                .invoke("call-1", SearchKnowledgeBaseToolPortAdapter.TOOL_ID, Map.of("query", " "));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("query is required");
        verify(retrievalEngine, never()).retrieveKnowledgeChannels(any(), anyInt());
    }

    @Test
    void memoryReadUsesServerInjectedUserScope() throws Exception {
        MemoryEnginePort memoryEnginePort = mock(MemoryEnginePort.class);
        when(memoryEnginePort.retrieveMemories(any(MemoryLoadRequest.class)))
                .thenReturn(List.of(MemoryItem.builder()
                        .id("mem-1")
                        .layer(MemoryLayer.SHORT_TERM)
                        .type("profile")
                        .content("用户是学生")
                        .importanceScore(0.8D)
                        .confidenceLevel(0.9D)
                        .build()));

        ToolInvocationResult result = new MemoryReadToolPortAdapter(memoryEnginePort, jsonSupport)
                .invoke("call-1", MemoryReadToolPortAdapter.TOOL_ID, Map.of(
                        "_seahorseUserId", "admin-user",
                        "_seahorseConversationId", "conversation-a",
                        "query", "职业",
                        "limit", 5));

        assertThat(result.success()).isTrue();
        JsonNode body = objectMapper.readTree(result.content());
        assertThat(body.path("memories").get(0).path("content").asText()).isEqualTo("用户是学生");

        ArgumentCaptor<MemoryLoadRequest> captor = ArgumentCaptor.forClass(MemoryLoadRequest.class);
        verify(memoryEnginePort).retrieveMemories(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("admin-user");
        assertThat(captor.getValue().conversationId()).isEqualTo("conversation-a");
        assertThat(captor.getValue().currentQuestion()).isEqualTo("职业");
    }

    @Test
    void memoryReadFailsWhenServerUserScopeIsMissing() {
        ToolInvocationResult result = new MemoryReadToolPortAdapter(mock(MemoryEnginePort.class), jsonSupport)
                .invoke("call-1", MemoryReadToolPortAdapter.TOOL_ID, Map.of("query", "职业"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("server user scope is missing");
    }

    @Test
    void memoryWriteDelegatesToCapturePolicyWithServerScope() {
        MemoryEnginePort memoryEnginePort = mock(MemoryEnginePort.class);

        ToolInvocationResult result = new MemoryWriteToolPortAdapter(memoryEnginePort, jsonSupport)
                .invoke("call-1", MemoryWriteToolPortAdapter.TOOL_ID, Map.of(
                        "_seahorseUserId", "admin-user",
                        "_seahorseConversationId", "conversation-a",
                        "content", "用户是学生",
                        "reason", "profile fact"));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<MemoryWriteRequest> captor = ArgumentCaptor.forClass(MemoryWriteRequest.class);
        verify(memoryEnginePort).writeMemory(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("admin-user");
        assertThat(captor.getValue().conversationId()).isEqualTo("conversation-a");
        assertThat(captor.getValue().message().getContent()).isEqualTo("用户是学生");
    }

    @Test
    void memoryWriteSubmitsIngestionCommandWhenWorkflowExists() throws Exception {
        MemoryEnginePort memoryEnginePort = mock(MemoryEnginePort.class);
        MemoryIngestionWorkflowPort workflowPort = mock(MemoryIngestionWorkflowPort.class);
        when(workflowPort.ingest(any(MemoryIngestionCommand.class)))
                .thenReturn(MemoryIngestionResult.accepted(
                        MemoryIngestionAction.ADD,
                        List.of("SHORT_TERM_SAVE"),
                        Map.of("memoryType", "PROFILE")));

        ToolInvocationResult result = new MemoryWriteToolPortAdapter(memoryEnginePort, workflowPort, null, jsonSupport)
                .invoke("call-9", MemoryWriteToolPortAdapter.TOOL_ID, Map.of(
                        "_seahorseUserId", "admin-user",
                        "_seahorseConversationId", "conversation-a",
                        "content", "用户是学生",
                        "reason", "profile fact"));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<MemoryIngestionCommand> captor = ArgumentCaptor.forClass(MemoryIngestionCommand.class);
        verify(workflowPort).ingest(captor.capture());
        verify(memoryEnginePort, never()).writeMemory(any(MemoryWriteRequest.class));
        assertThat(captor.getValue().operationId()).isEqualTo("tool-memory-write-call-9");
        assertThat(captor.getValue().source()).isEqualTo("agent-memory-write");
        assertThat(captor.getValue().writeRequest().userId()).isEqualTo("admin-user");
        JsonNode body = objectMapper.readTree(result.content());
        assertThat(body.path("policyDecision").asText()).isEqualTo("ALLOW_IF_CAPTURE_POLICY_ACCEPTS");
        assertThat(body.path("ingestionStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(body.path("ingestionAction").asText()).isEqualTo("ADD");
        assertThat(body.path("memoryAction").asText()).isEqualTo("ADD");
    }

    @Test
    void memoryWriteRunsGovernanceWhenAvailable() throws Exception {
        MemoryEnginePort memoryEnginePort = mock(MemoryEnginePort.class);
        MemoryGovernanceInboundPort governancePort = mock(MemoryGovernanceInboundPort.class);
        when(governancePort.runGovernance("admin-user", "agent-memory-write", false))
                .thenReturn(new MemoryGovernanceRunResult(
                        "admin-user", "agent-memory-write", 1, 1, 0, false, false, List.of(), Instant.now()));

        ToolInvocationResult result = new MemoryWriteToolPortAdapter(memoryEnginePort, governancePort, jsonSupport)
                .invoke("call-1", MemoryWriteToolPortAdapter.TOOL_ID, Map.of(
                        "_seahorseUserId", "admin-user",
                        "_seahorseConversationId", "conversation-a",
                        "content", "用户是学生",
                        "reason", "profile fact"));

        assertThat(result.success()).isTrue();
        JsonNode body = objectMapper.readTree(result.content());
        assertThat(body.path("governanceStatus").asText()).isEqualTo("OK");
        assertThat(body.path("promotedCount").asInt()).isEqualTo(1);
        assertThat(body.path("semanticUpsertCount").asInt()).isEqualTo(1);
        verify(governancePort).runGovernance("admin-user", "agent-memory-write", false);
    }

    @Test
    void memoryWriteRejectsBlankContent() {
        MemoryEnginePort memoryEnginePort = mock(MemoryEnginePort.class);

        ToolInvocationResult result = new MemoryWriteToolPortAdapter(memoryEnginePort, jsonSupport)
                .invoke("call-1", MemoryWriteToolPortAdapter.TOOL_ID, Map.of(
                        "_seahorseUserId", "admin-user",
                        "content", " "));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("content is required");
        verify(memoryEnginePort, never()).writeMemory(any(MemoryWriteRequest.class));
    }

    @Test
    void memoryForgetDeniesCrossUserDeletionWhenOwnerMetadataExists() {
        MemoryManagementInboundPort memoryManagementPort = mock(MemoryManagementInboundPort.class);
        when(memoryManagementPort.findMemory("short_term", "mem-1"))
                .thenReturn(Optional.of(new MemoryRecord(
                        "mem-1",
                        "short_term",
                        "profile",
                        "用户是学生",
                        Map.of("userId", "other-user"),
                        Instant.now())));

        ToolInvocationResult result = new MemoryForgetToolPortAdapter(memoryManagementPort, jsonSupport)
                .invoke("call-1", MemoryForgetToolPortAdapter.TOOL_ID, Map.of(
                        "_seahorseUserId", "admin-user",
                        "layer", "short_term",
                        "memoryId", "mem-1",
                        "reason", "cleanup"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("memory does not belong to current user scope");
        verify(memoryManagementPort, never()).deleteMemory("short_term", "mem-1");
    }

    @Test
    void memoryForgetDeletesOwnedMemory() {
        MemoryManagementInboundPort memoryManagementPort = mock(MemoryManagementInboundPort.class);
        when(memoryManagementPort.findMemory("short_term", "mem-1"))
                .thenReturn(Optional.of(new MemoryRecord(
                        "mem-1",
                        "short_term",
                        "profile",
                        "用户是学生",
                        Map.of("userId", "admin-user"),
                        Instant.now())));
        when(memoryManagementPort.deleteMemory("short_term", "mem-1")).thenReturn(true);

        ToolInvocationResult result = new MemoryForgetToolPortAdapter(memoryManagementPort, jsonSupport)
                .invoke("call-1", MemoryForgetToolPortAdapter.TOOL_ID, Map.of(
                        "_seahorseUserId", "admin-user",
                        "layer", "short_term",
                        "memoryId", "mem-1",
                        "reason", "cleanup"));

        assertThat(result.success()).isTrue();
        verify(memoryManagementPort).deleteMemory("short_term", "mem-1");
    }
}
