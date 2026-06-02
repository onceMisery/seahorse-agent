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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunCostSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.CostUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseCostUsageControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAppendAndAggregateCostUsageWithoutRawFields() throws Exception {
        CostUsageInboundPort port = mock(CostUsageInboundPort.class);
        when(port.append(any())).thenReturn(record());
        when(port.aggregate(any())).thenReturn(new CostUsageAggregate(
                "tenant-a",
                "agent-1",
                "run-1",
                150L,
                3L,
                0.35d,
                2L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseCostUsageController(provider(CostUsageInboundPort.class, port))).build();

        mvc.perform(post("/api/cost-usage-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usageId": "usage-1",
                                  "tenantId": "tenant-a",
                                  "agentId": "agent-1",
                                  "runId": "run-1",
                                  "userId": "user-1",
                                  "toolId": "tool-1",
                                  "modelId": "model-1",
                                  "source": "MODEL",
                                  "tokens": 150,
                                  "calls": 3,
                                  "cost": 0.35,
                                  "createdAt": "2026-05-26T00:00:00Z",
                                  "rawPrompt": "secret-token",
                                  "rawToolOutput": "secret-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.usageId").value("usage-1"))
                .andExpect(content().string(not(containsString("secret-token"))))
                .andExpect(content().string(not(containsString("rawPrompt"))))
                .andExpect(content().string(not(containsString("rawToolOutput"))));

        ArgumentCaptor<CostUsageRecord> recordCaptor = ArgumentCaptor.forClass(CostUsageRecord.class);
        verify(port).append(recordCaptor.capture());
        assertThat(recordCaptor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(recordCaptor.getValue().source()).isEqualTo(CostUsageSource.MODEL);

        mvc.perform(get("/api/cost-usage:aggregate")
                        .param("tenantId", "tenant-a")
                        .param("agentId", "agent-1")
                        .param("runId", "run-1")
                        .param("from", "2026-05-26T00:00:00Z")
                        .param("to", "2026-05-26T01:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTokens").value(150))
                .andExpect(jsonPath("$.data.totalCalls").value(3));

        ArgumentCaptor<CostUsageQuery> queryCaptor = ArgumentCaptor.forClass(CostUsageQuery.class);
        verify(port).aggregate(queryCaptor.capture());
        assertThat(queryCaptor.getValue().runId()).isEqualTo("run-1");
    }

    @Test
    void shouldReadAgentRunCostSummaryFromApiPath() throws Exception {
        AgentRunCostSummaryInboundPort port = mock(AgentRunCostSummaryInboundPort.class);
        when(port.getCostSummary("run-1")).thenReturn(new CostUsageAggregate(
                "tenant-a",
                "agent-1",
                "run-1",
                150L,
                3L,
                0.35d,
                2L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseAgentRunController(
                null,
                null,
                null,
                null,
                null,
                provider(AgentRunCostSummaryInboundPort.class, port),
                null,
                provider(AdvancedFeatureGate.class, AdvancedFeatureGate.consumerWebDefaults()))).build();

        mvc.perform(get("/api/agent-runs/run-1/cost-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.data.totalTokens").value(150))
                .andExpect(jsonPath("$.data.totalCalls").value(3));
    }

    private static CostUsageRecord record() {
        return new CostUsageRecord(
                "usage-1",
                "tenant-a",
                "agent-1",
                "run-1",
                "user-1",
                "tool-1",
                "model-1",
                CostUsageSource.MODEL,
                150L,
                3L,
                0.35d,
                null,
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
