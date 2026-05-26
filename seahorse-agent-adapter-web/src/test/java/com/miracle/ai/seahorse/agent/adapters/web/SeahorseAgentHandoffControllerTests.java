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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentHandoffInboundPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAgentHandoffControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldListAndCancelHandoffButRejectDirectCreate() throws Exception {
        AgentHandoffInboundPort port = mock(AgentHandoffInboundPort.class);
        AgentHandoff running = handoff(AgentHandoffStatus.RUNNING);
        AgentHandoff cancelled = running.cancel(NOW.plusSeconds(10));
        when(port.listByParentRunId("tenant-a", "parent-run-1")).thenReturn(List.of(running));
        when(port.findById("handoff-1")).thenReturn(running);
        when(port.cancel("handoff-1")).thenReturn(cancelled);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentHandoffController(provider(AgentHandoffInboundPort.class, port))).build();

        mvc.perform(get("/api/agent-runs/parent-run-1/handoffs").param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].handoffId").value("handoff-1"))
                .andExpect(jsonPath("$.data[0].parentRunId").value("parent-run-1"))
                .andExpect(jsonPath("$.data[0].childRunId").value("child-run-1"))
                .andExpect(jsonPath("$.data[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].inputSummaryJson").doesNotExist())
                .andExpect(jsonPath("$.data[0].contextSummaryJson").doesNotExist());

        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> parentCaptor = ArgumentCaptor.forClass(String.class);
        verify(port).listByParentRunId(tenantCaptor.capture(), parentCaptor.capture());
        assertThat(tenantCaptor.getValue()).isEqualTo("tenant-a");
        assertThat(parentCaptor.getValue()).isEqualTo("parent-run-1");

        mvc.perform(get("/api/agent-handoffs/handoff-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoffId").value("handoff-1"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
        verify(port).findById("handoff-1");

        mvc.perform(post("/api/agent-handoffs/handoff-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        verify(port).cancel("handoff-1");

        mvc.perform(post("/api/agent-handoffs")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private static AgentHandoff handoff(AgentHandoffStatus status) {
        return new AgentHandoff(
                "handoff-1",
                "tenant-a",
                "parent-run-1",
                "child-run-1",
                "source-agent",
                "target-agent",
                status,
                null,
                "delegate summary",
                "{\"inputSummary\":\"secret-token should not be returned\"}",
                "{\"summary\":\"raw context should not be returned\"}",
                NOW,
                NOW,
                null);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
