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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretMetadata;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCheckpointQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingReplaceCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalModifyCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AccessDecisionQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolInvocationAuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAgentControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-23T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeAgentDefinitionManagementApi() throws Exception {
        AgentDefinitionInboundPort port = mock(AgentDefinitionInboundPort.class);
        AgentDefinition definition = definition(AgentStatus.DRAFT);
        when(port.createDraft(any())).thenReturn("agent-1");
        when(port.page("tenant-a", 1L, 10L, "agent"))
                .thenReturn(new AgentDefinitionPage(List.of(definition), 1L, 10L, 1L, 1L));
        when(port.findById("agent-1")).thenReturn(Optional.of(definition));
        when(port.updateDraft(eq("agent-1"), any())).thenReturn(definition);
        when(port.publish(eq("agent-1"), any())).thenReturn(version());
        when(port.disable("agent-1")).thenReturn(definition(AgentStatus.DISABLED));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentDefinitionController(provider(AgentDefinitionInboundPort.class, port))).build();

        mvc.perform(post("/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "agentId", "agent-1",
                                "tenantId", "tenant-a",
                                "name", "Agent One",
                                "description", "desc",
                                "ownerUserId", "owner-1",
                                "ownerTeam", "platform",
                                "agentType", "WORKFLOW",
                                "riskLevel", "HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("agent-1"));

        ArgumentCaptor<AgentDefinitionCreateCommand> createCaptor =
                ArgumentCaptor.forClass(AgentDefinitionCreateCommand.class);
        verify(port).createDraft(createCaptor.capture());
        assertThat(createCaptor.getValue().agentType()).isEqualTo(AgentType.WORKFLOW);
        assertThat(createCaptor.getValue().riskLevel()).isEqualTo(AgentRiskLevel.HIGH);

        mvc.perform(get("/agents")
                        .param("tenantId", "tenant-a")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].agentId").value("agent-1"));

        mvc.perform(get("/agents/agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value("agent-1"));

        mvc.perform(put("/agents/agent-1/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Agent One",
                                "description", "desc",
                                "ownerTeam", "platform",
                                "agentType", "DOMAIN",
                                "riskLevel", "MEDIUM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        verify(port).updateDraft(eq("agent-1"), any(AgentDefinitionUpdateDraftCommand.class));

        mvc.perform(post("/agents/agent-1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "instructions", "Do the work",
                                "toolSetJson", "{}",
                                "modelConfigJson", "{}",
                                "memoryConfigJson", "{}",
                                "guardrailConfigJson", "{}",
                                "changeSummary", "initial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value("agent-1-v1"));
        verify(port).publish(eq("agent-1"), any(AgentVersionPublishCommand.class));

        mvc.perform(post("/agents/agent-1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void shouldExposeAgentRunApi() throws Exception {
        AgentRunInboundPort port = mock(AgentRunInboundPort.class);
        AgentRunResumeInboundPort resumePort = mock(AgentRunResumeInboundPort.class);
        AgentCheckpointQueryInboundPort checkpointPort = mock(AgentCheckpointQueryInboundPort.class);
        AgentRun run = run(AgentRunStatus.RUNNING);
        when(port.startRun(any())).thenReturn(run);
        when(port.findRunById("run-1")).thenReturn(Optional.of(run));
        when(port.listSteps("run-1")).thenReturn(List.of(step()));
        when(port.cancel("run-1")).thenReturn(run(AgentRunStatus.CANCELLED));
        when(resumePort.resume("run-1")).thenReturn(run(AgentRunStatus.SUCCEEDED));
        when(checkpointPort.listByRunId("run-1")).thenReturn(List.of(checkpoint()));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentRunController(
                        provider(AgentRunInboundPort.class, port),
                        provider(AgentRunResumeInboundPort.class, resumePort),
                        provider(AgentCheckpointQueryInboundPort.class, checkpointPort))).build();

        mvc.perform(post("/agents/agent-1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "versionId", "agent-1-v1",
                                "tenantId", "tenant-a",
                                "conversationId", "conversation-1",
                                "triggerType", "CHAT",
                                "inputSummary", "summary",
                                "traceId", "trace-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"));

        ArgumentCaptor<AgentRunStartCommand> startCaptor = ArgumentCaptor.forClass(AgentRunStartCommand.class);
        verify(port).startRun(startCaptor.capture());
        assertThat(startCaptor.getValue().agentId()).isEqualTo("agent-1");
        assertThat(startCaptor.getValue().triggerType()).isEqualTo(AgentRunTriggerType.CHAT);

        mvc.perform(get("/agent-runs/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mvc.perform(get("/agent-runs/run-1/steps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stepId").value("step-1"));

        mvc.perform(post("/agent-runs/run-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mvc.perform(post("/api/agent-runs/run-1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        verify(resumePort).resume("run-1");

        mvc.perform(get("/api/agent-runs/run-1/checkpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].checkpointId").value("checkpoint-1"))
                .andExpect(jsonPath("$.data[0].checkpointType").value("WAITING_APPROVAL"));
        verify(checkpointPort).listByRunId("run-1");
    }

    @Test
    void shouldExposeToolCatalogManagementApi() throws Exception {
        ToolCatalogManagementInboundPort port = mock(ToolCatalogManagementInboundPort.class);
        when(port.page("MCP", "weather", 2L, 20L, true))
                .thenReturn(new ToolCatalogPage(List.of(tool(true)), 1L, 20L, 2L, 1L));
        when(port.findById("weather_query")).thenReturn(Optional.of(tool(true)));
        when(port.disable("weather_query")).thenReturn(tool(false));
        when(port.enable("weather_query")).thenReturn(tool(true));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseToolCatalogController(provider(ToolCatalogManagementInboundPort.class, port))).build();

        mvc.perform(get("/api/tools")
                        .param("resourceType", "MCP")
                        .param("keyword", "weather")
                        .param("current", "2")
                        .param("size", "20")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].toolId").value("weather_query"));

        mvc.perform(get("/api/tools/weather_query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("MCP"))
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"));

        mvc.perform(post("/api/tools/weather_query/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mvc.perform(post("/api/tools/weather_query/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        verify(port).page("MCP", "weather", 2L, 20L, true);
        verify(port).findById("weather_query");
        verify(port).disable("weather_query");
        verify(port).enable("weather_query");
    }

    @Test
    void shouldExposeAgentToolBindingManagementApi() throws Exception {
        AgentToolBindingManagementInboundPort port = mock(AgentToolBindingManagementInboundPort.class);
        when(port.replaceBindings(eq("agent-1"), eq("agent-1-v1"), any()))
                .thenReturn(List.of(binding("weather_query", 3)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentToolBindingController(provider(AgentToolBindingManagementInboundPort.class, port)))
                .build();

        mvc.perform(put("/api/agents/agent-1/versions/agent-1-v1/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tools", List.of(Map.of(
                                "toolId", "weather_query",
                                "maxCallsPerRun", 3,
                                "argumentPolicyJson", "{\"required\":[\"query\"],\"allowed\":[\"query\"]}"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].toolId").value("weather_query"))
                .andExpect(jsonPath("$.data[0].maxCallsPerRun").value(3));

        ArgumentCaptor<AgentToolBindingReplaceCommand> captor =
                ArgumentCaptor.forClass(AgentToolBindingReplaceCommand.class);
        verify(port).replaceBindings(eq("agent-1"), eq("agent-1-v1"), captor.capture());
        assertThat(captor.getValue().tools()).hasSize(1);
        assertThat(captor.getValue().tools().get(0).toolId()).isEqualTo("weather_query");
        assertThat(captor.getValue().tools().get(0).maxCallsPerRun()).isEqualTo(3);
    }

    @Test
    void shouldExposeToolInvocationAuditQueryApi() throws Exception {
        ToolInvocationAuditQueryInboundPort port = mock(ToolInvocationAuditQueryInboundPort.class);
        when(port.page(
                        "tenant-a",
                        "agent-1",
                        "agent-1-v1",
                        "run-1",
                        "weather_query",
                        ToolInvocationStatus.SUCCEEDED,
                        2L,
                        20L))
                .thenReturn(new ToolInvocationAuditPage(List.of(invocation()), 1L, 20L, 2L, 1L));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseToolInvocationAuditController(provider(ToolInvocationAuditQueryInboundPort.class, port)))
                .build();

        mvc.perform(get("/api/tool-invocations")
                        .param("tenantId", "tenant-a")
                        .param("agentId", "agent-1")
                        .param("versionId", "agent-1-v1")
                        .param("runId", "run-1")
                        .param("toolId", "weather_query")
                        .param("status", "SUCCEEDED")
                        .param("current", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].invocationId").value("invocation-1"))
                .andExpect(jsonPath("$.data.records[0].status").value("SUCCEEDED"));

        verify(port).page(
                "tenant-a",
                "agent-1",
                "agent-1-v1",
                "run-1",
                "weather_query",
                ToolInvocationStatus.SUCCEEDED,
                2L,
                20L);
    }

    @Test
    void shouldExposeAccessDecisionQueryApi() throws Exception {
        AccessDecisionQueryInboundPort port = mock(AccessDecisionQueryInboundPort.class);
        when(port.page(
                        "tenant-a",
                        AccessSubjectType.USER_DELEGATED_AGENT,
                        "user-1",
                        ResourceAction.READ,
                        ContextResourceType.MEMORY.value(),
                        "memory-1",
                        AccessDecisionEffect.ALLOW,
                        ResourceAccessReasonCodes.OWNER_MATCH,
                        2L,
                        20L))
                .thenReturn(new AccessDecisionPage(List.of(accessDecision()), 1L, 20L, 2L, 1L));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAccessDecisionController(provider(AccessDecisionQueryInboundPort.class, port))).build();

        mvc.perform(get("/api/access-decisions")
                        .param("tenantId", "tenant-a")
                        .param("subjectType", "USER_DELEGATED_AGENT")
                        .param("subjectId", "user-1")
                        .param("action", "READ")
                        .param("resourceType", "MEMORY")
                        .param("resourceId", "memory-1")
                        .param("effect", "ALLOW")
                        .param("reasonCode", ResourceAccessReasonCodes.OWNER_MATCH)
                        .param("current", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].decisionId").value("decision-1"))
                .andExpect(jsonPath("$.data.records[0].effect").value("ALLOW"));

        verify(port).page(
                "tenant-a",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                ContextResourceType.MEMORY.value(),
                "memory-1",
                AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.OWNER_MATCH,
                2L,
                20L);
    }

    @Test
    void shouldExposeApprovalManagementApi() throws Exception {
        ApprovalManagementInboundPort port = mock(ApprovalManagementInboundPort.class);
        when(port.page("tenant-a", ApprovalRequestStatus.PENDING, 2L, 20L))
                .thenReturn(new ApprovalRequestPage(List.of(approval(ApprovalRequestStatus.PENDING)), 1L, 20L, 2L,
                        1L));
        when(port.findById("approval-1")).thenReturn(Optional.of(approval(ApprovalRequestStatus.PENDING)));
        when(port.approve(eq("approval-1"), any())).thenReturn(approval(ApprovalRequestStatus.APPROVED));
        when(port.reject(eq("approval-1"), any())).thenReturn(approval(ApprovalRequestStatus.REJECTED));
        when(port.modify(eq("approval-1"), any())).thenReturn(approval(ApprovalRequestStatus.MODIFIED));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseApprovalController(provider(ApprovalManagementInboundPort.class, port))).build();

        mvc.perform(get("/api/approvals")
                        .param("tenantId", "tenant-a")
                        .param("status", "PENDING")
                        .param("current", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].approvalId").value("approval-1"))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));

        mvc.perform(get("/api/approvals/approval-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalId").value("approval-1"))
                .andExpect(jsonPath("$.data.toolId").value("memory-forget"));

        mvc.perform(post("/api/approvals/approval-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decisionComment", "Looks safe"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        ArgumentCaptor<ApprovalDecisionCommand> approveCaptor =
                ArgumentCaptor.forClass(ApprovalDecisionCommand.class);
        verify(port).approve(eq("approval-1"), approveCaptor.capture());
        assertThat(approveCaptor.getValue().decisionComment()).isEqualTo("Looks safe");

        mvc.perform(post("/api/approvals/approval-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decisionComment", "Risk too high"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        ArgumentCaptor<ApprovalDecisionCommand> rejectCaptor =
                ArgumentCaptor.forClass(ApprovalDecisionCommand.class);
        verify(port).reject(eq("approval-1"), rejectCaptor.capture());
        assertThat(rejectCaptor.getValue().decisionComment()).isEqualTo("Risk too high");

        mvc.perform(post("/api/approvals/approval-1/modify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "argumentsPreviewJson", "{\"argumentKeys\":[\"input\"],\"modified\":true}",
                                "decisionComment", "Reduced scope"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MODIFIED"));

        ArgumentCaptor<ApprovalModifyCommand> modifyCaptor = ArgumentCaptor.forClass(ApprovalModifyCommand.class);
        verify(port).modify(eq("approval-1"), modifyCaptor.capture());
        assertThat(modifyCaptor.getValue().argumentsPreviewJson())
                .isEqualTo("{\"argumentKeys\":[\"input\"],\"modified\":true}");
        assertThat(modifyCaptor.getValue().decisionComment()).isEqualTo("Reduced scope");
    }

    @Test
    void shouldExposeContextPackQueryApi() throws Exception {
        ContextPackQueryInboundPort port = mock(ContextPackQueryInboundPort.class);
        when(port.findById("context-pack-1")).thenReturn(Optional.of(contextPack()));
        when(port.listItems("context-pack-1")).thenReturn(List.of(contextItem()));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseContextPackController(provider(ContextPackQueryInboundPort.class, port))).build();

        mvc.perform(get("/api/context-packs/context-pack-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.contextPackId").value("context-pack-1"))
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.items[0].aclDecisionId").value("decision-doc-1"));

        mvc.perform(get("/api/context-packs/context-pack-1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemId").value("item-1"))
                .andExpect(jsonPath("$.data[0].sourceType").value("RAG_CHUNK"))
                .andExpect(jsonPath("$.data[0].sensitivity").value("INTERNAL"));

        verify(port).findById("context-pack-1");
        verify(port).listItems("context-pack-1");
    }

    @Test
    void shouldExposeSecretCreationApiWithoutEchoingPlaintext() throws Exception {
        SecretManagementInboundPort port = mock(SecretManagementInboundPort.class);
        when(port.create(any())).thenReturn(new SecretMetadata(
                "secret_1",
                "tenant-a",
                "{\"purpose\":\"mcp\"}",
                NOW,
                null));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSecretController(provider(SecretManagementInboundPort.class, port))).build();

        mvc.perform(post("/api/secrets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-a",
                                "secretValue", "super-secret-token",
                                "metadataJson", "{\"purpose\":\"mcp\"}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.secretRef").value("secret_1"))
                .andExpect(jsonPath("$.data.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.data.secretValue").doesNotExist());

        ArgumentCaptor<SecretCreateCommand> captor = ArgumentCaptor.forClass(SecretCreateCommand.class);
        verify(port).create(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().secretValue().reveal()).isEqualTo("super-secret-token");
        assertThat(captor.getValue().metadataJson()).isEqualTo("{\"purpose\":\"mcp\"}");
    }

    @Test
    void shouldRejectSecretCreationMetadataContainingPlaintext() throws Exception {
        SecretManagementInboundPort port = mock(SecretManagementInboundPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseSecretController(provider(SecretManagementInboundPort.class, port)))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(post("/api/secrets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-a",
                                "secretValue", "super-secret-token",
                                "metadataJson", "{\"note\":\"super-secret-token\"}"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("metadataJson must not contain secret plaintext"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(port);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static AgentDefinition definition(AgentStatus status) {
        return new AgentDefinition("agent-1", "tenant-a", "Agent One", "desc", "owner-1", "platform",
                AgentType.WORKFLOW, null, status, AgentRiskLevel.HIGH, "agent-1-v1", NOW, NOW);
    }

    private static AgentVersion version() {
        return new AgentVersion("agent-1-v1", "agent-1", 1L, "Do the work", "{}", "{}", "{}",
                "{}", "admin-1", NOW, "initial");
    }

    private static AgentRun run(AgentRunStatus status) {
        return new AgentRun("run-1", "agent-1", "agent-1-v1", "tenant-a", "user-1",
                "conversation-1", AgentRunTriggerType.CHAT, "summary", status, "trace-1",
                0L, 0L, BigDecimal.ZERO, null, null, NOW, status == AgentRunStatus.RUNNING ? null : NOW);
    }

    private static AgentStep step() {
        return new AgentStep("step-1", "run-1", 1, AgentStepType.MODEL_TURN, AgentStepStatus.SUCCEEDED,
                "{\"prompt\":\"hi\"}", "{\"answer\":\"hello\"}", null, null, NOW, NOW);
    }

    private static AgentCheckpoint checkpoint() {
        return new AgentCheckpoint(
                "checkpoint-1",
                "run-1",
                "step-1",
                1L,
                AgentCheckpointType.WAITING_APPROVAL,
                "{\"exitReason\":\"WAITING_APPROVAL\"}",
                "[]",
                null,
                "{\"toolId\":\"memory-forget\"}",
                NOW);
    }

    private static ToolCatalogEntry tool(boolean enabled) {
        return new ToolCatalogEntry(
                "weather_query",
                ToolProvider.MCP,
                "Weather Query",
                "查询天气",
                "{\"type\":\"object\"}",
                null,
                ToolRiskLevel.MEDIUM,
                ToolActionType.EXECUTE,
                "MCP",
                "platform",
                enabled,
                false,
                NOW,
                NOW);
    }

    private static AgentToolBinding binding(String toolId, int maxCallsPerRun) {
        return new AgentToolBinding(
                "atb-1",
                "agent-1",
                "agent-1-v1",
                toolId,
                maxCallsPerRun,
                "{\"required\":[\"query\"],\"allowed\":[\"query\"]}",
                "admin-1",
                NOW);
    }

    private static ToolInvocationAuditEntry invocation() {
        return new ToolInvocationAuditEntry(
                "invocation-1",
                "run-1",
                "step-1",
                "agent-1",
                "agent-1-v1",
                "tenant-a",
                "user-1",
                "weather_query",
                "run-1:call-1",
                ToolInvocationStatus.SUCCEEDED,
                "decision-1",
                "keys=[query], size=1",
                "length=16",
                null,
                NOW,
                NOW.plusSeconds(1));
    }

    private static AccessDecision accessDecision() {
        return new AccessDecision(
                "decision-1",
                "tenant-a",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                ContextResourceType.MEMORY.value(),
                "memory-1",
                AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.OWNER_MATCH,
                NOW);
    }

    private static ApprovalRequest approval(ApprovalRequestStatus status) {
        return new ApprovalRequest(
                "approval-1",
                "run-1",
                "step-1",
                "invocation-1",
                "tenant-a",
                "user-1",
                "agent-1",
                "memory-forget",
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool memory-forget requires approval",
                "{\"argumentKeys\":[\"input\"]}",
                status,
                NOW,
                null,
                status == ApprovalRequestStatus.PENDING ? null : "admin-1",
                status == ApprovalRequestStatus.PENDING ? null : NOW.plusSeconds(60),
                status == ApprovalRequestStatus.PENDING ? null : "decided");
    }

    private static ContextPack contextPack() {
        return new ContextPack(
                "context-pack-1",
                "run-1",
                "agent-1",
                "version-1",
                "tenant-a",
                "user-1",
                "answer question",
                300,
                List.of(contextItem()),
                NOW);
    }

    private static ContextItem contextItem() {
        return new ContextItem(
                "item-1",
                "context-pack-1",
                ContextItemSourceType.RAG_CHUNK,
                "doc-1",
                "content for doc-1",
                "summary for doc-1",
                0.91,
                0.88,
                ContextSensitivity.INTERNAL,
                "decision-doc-1",
                "{\"sourceId\":\"doc-1\"}",
                64,
                null,
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getName(), bean);
        return factory.getBeanProvider(type);
    }
}
