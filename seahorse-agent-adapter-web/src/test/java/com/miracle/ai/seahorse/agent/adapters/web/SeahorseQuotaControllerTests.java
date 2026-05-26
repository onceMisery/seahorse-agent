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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaUsage;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaPolicyUpsertCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseQuotaControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldUpsertDisableAndEvaluateQuotaPolicies() throws Exception {
        QuotaManagementInboundPort port = mock(QuotaManagementInboundPort.class);
        when(port.upsertPolicy(any())).thenReturn(policy(QuotaPolicyStatus.ACTIVE));
        when(port.evaluate(any())).thenReturn(new QuotaDecisionResult(
                QuotaDecisionEffect.WARN,
                QuotaDecisionReasonCode.WARN_THRESHOLD_EXCEEDED,
                "policy-1",
                new QuotaUsage(900L, 9L, 2.5d),
                NOW));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseQuotaController(provider(QuotaManagementInboundPort.class, port))).build();

        mvc.perform(post("/api/quotas/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyId": "policy-1",
                                  "tenantId": "tenant-a",
                                  "scope": "AGENT",
                                  "subjectId": "agent-1",
                                  "status": "ACTIVE",
                                  "tokenLimit": 1000,
                                  "callLimit": 10,
                                  "costLimit": 3.5,
                                  "warnRatio": 0.8,
                                  "createdAt": "2026-05-26T00:00:00Z",
                                  "updatedAt": "2026-05-26T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.policyId").value("policy-1"))
                .andExpect(jsonPath("$.data.scope").value("AGENT"));

        ArgumentCaptor<QuotaPolicyUpsertCommand> policyCaptor =
                ArgumentCaptor.forClass(QuotaPolicyUpsertCommand.class);
        verify(port).upsertPolicy(policyCaptor.capture());
        assertThat(policyCaptor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(policyCaptor.getValue().scope()).isEqualTo(QuotaScope.AGENT);

        mvc.perform(post("/api/quotas/policies/policy-1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        verify(port).disablePolicy("policy-1");

        mvc.perform(post("/api/quotas/decisions:evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "agentId": "agent-1",
                                  "userId": "user-1",
                                  "toolId": "tool-1",
                                  "modelId": "model-1",
                                  "runId": "run-1",
                                  "riskLevel": "HIGH",
                                  "tokens": 900,
                                  "calls": 9,
                                  "cost": 2.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effect").value("WARN"))
                .andExpect(jsonPath("$.data.reasonCode").value("WARN_THRESHOLD_EXCEEDED"));

        ArgumentCaptor<QuotaDecisionCommand> decisionCaptor =
                ArgumentCaptor.forClass(QuotaDecisionCommand.class);
        verify(port).evaluate(decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().riskLevel()).isEqualTo(AgentRiskLevel.HIGH);
        assertThat(decisionCaptor.getValue().requestedUsage().tokens()).isEqualTo(900L);
    }

    private static QuotaPolicy policy(QuotaPolicyStatus status) {
        return new QuotaPolicy(
                "policy-1",
                "tenant-a",
                QuotaScope.AGENT,
                "agent-1",
                status,
                1000L,
                10L,
                3.5d,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
