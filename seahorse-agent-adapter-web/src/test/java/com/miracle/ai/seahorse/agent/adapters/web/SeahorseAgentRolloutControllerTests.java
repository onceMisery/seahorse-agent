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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutCostSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutActionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutCostSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutRollbackCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAgentRolloutControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldCreateCanaryAndQueryLatestRollout() throws Exception {
        AgentRolloutInboundPort port = mock(AgentRolloutInboundPort.class);
        AgentVersionRollout running = rollout("rollout-1", AgentRolloutStatus.RUNNING, null, null);
        when(port.createCanary(any())).thenReturn(running);
        when(port.latest("tenant-a", "agent-1", "version-1")).thenReturn(Optional.of(running));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentRolloutController(provider(AgentRolloutInboundPort.class, port))).build();

        mvc.perform(post("/api/agents/agent-1/versions/version-1/rollouts/canary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "canaryPercent": 10,
                                  "operator": "operator-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.rolloutId").value("rollout-1"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.canaryPercent").value(10));

        ArgumentCaptor<AgentRolloutCreateCommand> createCaptor =
                ArgumentCaptor.forClass(AgentRolloutCreateCommand.class);
        verify(port).createCanary(createCaptor.capture());
        assertThat(createCaptor.getValue().agentId()).isEqualTo("agent-1");
        assertThat(createCaptor.getValue().versionId()).isEqualTo("version-1");

        mvc.perform(get("/api/agents/agent-1/versions/version-1/rollouts/latest")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rolloutId").value("rollout-1"));
        verify(port).latest("tenant-a", "agent-1", "version-1");
    }

    @Test
    void shouldPausePromoteAndRollbackThroughInboundPort() throws Exception {
        AgentRolloutInboundPort port = mock(AgentRolloutInboundPort.class);
        when(port.pause(any())).thenReturn(rollout("rollout-1", AgentRolloutStatus.PAUSED, null, null));
        when(port.promote(any())).thenReturn(rollout("rollout-1", AgentRolloutStatus.PROMOTED, null, "gate-1"));
        when(port.rollback(any()))
                .thenReturn(rollout("rollout-1", AgentRolloutStatus.FAILED, AgentRolloutFailureCode.ROLLBACK_FAILED, null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentRolloutController(provider(AgentRolloutInboundPort.class, port))).build();

        mvc.perform(post("/api/agents/agent-1/rollouts/rollout-1/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "operator": "operator-1",
                                  "comment": "pause canary"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
        ArgumentCaptor<AgentRolloutActionCommand> pauseCaptor =
                ArgumentCaptor.forClass(AgentRolloutActionCommand.class);
        verify(port).pause(pauseCaptor.capture());
        assertThat(pauseCaptor.getValue().rolloutId()).isEqualTo("rollout-1");

        mvc.perform(post("/api/agents/agent-1/rollouts/rollout-1/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "operator": "operator-1",
                                  "comment": "promote canary"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROMOTED"))
                .andExpect(jsonPath("$.data.gateReportId").value("gate-1"));
        verify(port).promote(any());

        mvc.perform(post("/api/agents/agent-1/rollouts/rollout-1/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "targetVersionId": "version-0",
                                  "operator": "operator-1",
                                  "comment": "rollback canary"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureCode").value("ROLLBACK_FAILED"));
        ArgumentCaptor<AgentRolloutRollbackCommand> rollbackCaptor =
                ArgumentCaptor.forClass(AgentRolloutRollbackCommand.class);
        verify(port).rollback(rollbackCaptor.capture());
        assertThat(rollbackCaptor.getValue().targetVersionId()).isEqualTo("version-0");
    }

    @Test
    void shouldExposeRolloutCostSummary() throws Exception {
        AgentRolloutInboundPort rolloutPort = mock(AgentRolloutInboundPort.class);
        AgentRolloutCostSummaryInboundPort costSummaryPort = mock(AgentRolloutCostSummaryInboundPort.class);
        when(costSummaryPort.getCostSummary("tenant-a", "agent-1", "rollout-1"))
                .thenReturn(new AgentRolloutCostSummary(
                        "rollout-1",
                        "tenant-a",
                        "agent-1",
                        "version-1",
                        NOW,
                        NOW.plusSeconds(60),
                        "AGENT_ROLLOUT_ID",
                        1200,
                        9,
                        0.42,
                        3,
                        Map.of(
                                "SUCCEEDED", 7L,
                                "FAILED", 2L,
                                "WAITING_APPROVAL", 1L),
                        10L,
                        7L,
                        2L,
                        1L,
                        0.2,
                        4L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentRolloutController(
                        provider(AgentRolloutInboundPort.class, rolloutPort),
                        provider(AgentRolloutCostSummaryInboundPort.class, costSummaryPort))).build();

        mvc.perform(get("/api/agents/agent-1/rollouts/rollout-1/cost-summary")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.rolloutId").value("rollout-1"))
                .andExpect(jsonPath("$.data.aggregationScope").value("AGENT_ROLLOUT_ID"))
                .andExpect(jsonPath("$.data.totalTokens").value(1200))
                .andExpect(jsonPath("$.data.totalCalls").value(9))
                .andExpect(jsonPath("$.data.recordCount").value(3))
                .andExpect(jsonPath("$.data.totalRuns").value(10))
                .andExpect(jsonPath("$.data.succeededRuns").value(7))
                .andExpect(jsonPath("$.data.failedRuns").value(2))
                .andExpect(jsonPath("$.data.waitingApprovalRuns").value(1))
                .andExpect(jsonPath("$.data.errorRate").value(0.2))
                .andExpect(jsonPath("$.data.pendingApprovalCount").value(4))
                .andExpect(jsonPath("$.data.runStatusCounts.SUCCEEDED").value(7));

        verify(costSummaryPort).getCostSummary("tenant-a", "agent-1", "rollout-1");
    }

    private static AgentVersionRollout rollout(String rolloutId,
                                               AgentRolloutStatus status,
                                               AgentRolloutFailureCode failureCode,
                                               String gateReportId) {
        Instant finishedAt = status.terminal() ? NOW.plusSeconds(60) : null;
        return new AgentVersionRollout(
                rolloutId,
                "tenant-a",
                "agent-1",
                "version-1",
                AgentRolloutLimits.DEFAULT_CANARY_PERCENT,
                status,
                failureCode,
                gateReportId,
                "operator-1",
                NOW,
                NOW.plusSeconds(60),
                finishedAt);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
