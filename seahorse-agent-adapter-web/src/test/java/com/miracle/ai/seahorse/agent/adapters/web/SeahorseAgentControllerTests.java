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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
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
        AgentRun run = run(AgentRunStatus.RUNNING);
        when(port.startRun(any())).thenReturn(run);
        when(port.findRunById("run-1")).thenReturn(Optional.of(run));
        when(port.listSteps("run-1")).thenReturn(List.of(step()));
        when(port.cancel("run-1")).thenReturn(run(AgentRunStatus.CANCELLED));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentRunController(provider(AgentRunInboundPort.class, port))).build();

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

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getName(), bean);
        return factory.getBeanProvider(type);
    }
}
