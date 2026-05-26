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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummaryPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummaryHistoryQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummarySaveCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAgentEvalControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveLatestAndHistoryWithoutRawEvaluationFields() throws Exception {
        AgentEvalInboundPort port = mock(AgentEvalInboundPort.class);
        AgentEvalSummary summary = summary("summary-1", AgentEvalStatus.PASS);
        when(port.saveSummary(org.mockito.ArgumentMatchers.any())).thenReturn(summary);
        when(port.latestSummary("tenant-a", "agent-1", "version-1", AgentEvalType.SAFETY))
                .thenReturn(Optional.of(summary));
        when(port.history(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AgentEvalSummaryPage(List.of(summary), 1L, 20L, 1L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentEvalController(
                        provider(AgentEvalInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests())).build();

        mvc.perform(post("/api/agents/agent-1/versions/version-1/eval-summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summaryId": "summary-1",
                                  "tenantId": "tenant-a",
                                  "evalType": "SAFETY",
                                  "status": "PASS",
                                  "score": 0.95,
                                  "passThreshold": 0.9,
                                  "warnThreshold": 0.7,
                                  "caseCount": 8,
                                  "datasetRef": "dataset:v1",
                                  "evalRunRef": "eval-run-1",
                                  "evidenceRefs": ["trace:1", "dashboard:1"],
                                  "createdBy": "admin-1",
                                  "createdAt": "2026-05-26T00:00:00Z",
                                  "rawCase": "secret-token",
                                  "rawPrompt": "secret-token",
                                  "rawToolOutput": "secret-token",
                                  "sampleInput": "secret-token",
                                  "sampleOutput": "secret-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.summaryId").value("summary-1"))
                .andExpect(jsonPath("$.data.agentId").value("agent-1"))
                .andExpect(jsonPath("$.data.versionId").value("version-1"))
                .andExpect(jsonPath("$.data.evalType").value("SAFETY"))
                .andExpect(content().string(not(containsString("secret-token"))))
                .andExpect(content().string(not(containsString("rawCase"))))
                .andExpect(content().string(not(containsString("rawPrompt"))))
                .andExpect(content().string(not(containsString("rawToolOutput"))))
                .andExpect(content().string(not(containsString("sampleInput"))))
                .andExpect(content().string(not(containsString("sampleOutput"))));

        ArgumentCaptor<AgentEvalSummarySaveCommand> commandCaptor =
                ArgumentCaptor.forClass(AgentEvalSummarySaveCommand.class);
        verify(port).saveSummary(commandCaptor.capture());
        AgentEvalSummarySaveCommand command = commandCaptor.getValue();
        assertThat(command.agentId()).isEqualTo("agent-1");
        assertThat(command.versionId()).isEqualTo("version-1");
        assertThat(command.evalType()).isEqualTo(AgentEvalType.SAFETY);

        mvc.perform(get("/api/agents/agent-1/versions/version-1/eval-summaries/latest")
                        .param("tenantId", "tenant-a")
                        .param("evalType", "SAFETY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summaryId").value("summary-1"));
        verify(port).latestSummary("tenant-a", "agent-1", "version-1", AgentEvalType.SAFETY);

        mvc.perform(get("/api/agents/agent-1/versions/version-1/eval-summaries")
                        .param("tenantId", "tenant-a")
                        .param("evalType", "SAFETY")
                        .param("current", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].summaryId").value("summary-1"))
                .andExpect(content().string(not(containsString("secret-token"))));
        ArgumentCaptor<AgentEvalSummaryHistoryQuery> queryCaptor =
                ArgumentCaptor.forClass(AgentEvalSummaryHistoryQuery.class);
        verify(port).history(queryCaptor.capture());
        assertThat(queryCaptor.getValue().tenantId()).isEqualTo("tenant-a");
    }

    @Test
    void consumerWebModeShouldRejectAgentEvaluationApis() throws Exception {
        AgentEvalInboundPort port = mock(AgentEvalInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseAgentEvalController(provider(AgentEvalInboundPort.class, port)))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(post("/api/agents/agent-1/versions/version-1/eval-summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summaryId": "summary-1",
                                  "tenantId": "tenant-a",
                                  "evalType": "SAFETY",
                                  "status": "PASS"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature AGENT_EVALUATION is disabled in CONSUMER_WEB mode"));

        verifyNoInteractions(port);
    }

    private static AgentEvalSummary summary(String summaryId, AgentEvalStatus status) {
        return new AgentEvalSummary(
                summaryId,
                "tenant-a",
                "agent-1",
                "version-1",
                AgentEvalType.SAFETY,
                status,
                0.95d,
                0.9d,
                0.7d,
                8,
                "dataset:v1",
                "eval-run-1",
                List.of("trace:1", "dashboard:1"),
                "admin-1",
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
