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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ProductionGateInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseProductionGateControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldExposeProductionGateApis() throws Exception {
        ProductionGateInboundPort port = mock(ProductionGateInboundPort.class);
        ProductionGateReport report = report();
        when(port.generate("agent-1")).thenReturn(report);
        when(port.latest("agent-1")).thenReturn(Optional.of(report));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseProductionGateController(
                        provider(ProductionGateInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests())).build();

        mvc.perform(post("/api/agents/agent-1/production-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reportId").value("gate-1"))
                .andExpect(jsonPath("$.data.status").value("WARN"))
                .andExpect(jsonPath("$.data.items[0].code").value("AUDIT_LEDGER_ENABLED"));
        verify(port).generate("agent-1");

        mvc.perform(get("/api/agents/agent-1/production-gate/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value("agent-1"))
                .andExpect(jsonPath("$.data.versionId").value("version-1"));
        verify(port).latest("agent-1");
    }

    @Test
    void consumerWebModeShouldRejectProductionGateApis() throws Exception {
        ProductionGateInboundPort port = mock(ProductionGateInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseProductionGateController(provider(ProductionGateInboundPort.class, port)))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(post("/api/agents/agent-1/production-gate"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature PRODUCTION_GATE is disabled in CONSUMER_WEB mode"));

        verifyNoInteractions(port);
    }

    private static ProductionGateReport report() {
        return new ProductionGateReport(
                "gate-1",
                "agent-1",
                "version-1",
                ProductionGateStatus.WARN,
                List.of(
                        ProductionGateCheckItem.pass(
                                ProductionGateCheckCode.AUDIT_LEDGER_ENABLED,
                                "Audit ledger repository is configured."),
                        ProductionGateCheckItem.warn(
                                ProductionGateCheckCode.EVAL_PASSING,
                                "Evaluation platform is not connected yet.")),
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
