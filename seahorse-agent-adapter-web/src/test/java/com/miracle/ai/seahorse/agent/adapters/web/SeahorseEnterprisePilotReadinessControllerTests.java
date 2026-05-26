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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessGenerateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessInboundPort;
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

class SeahorseEnterprisePilotReadinessControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldGenerateAndQueryReadinessReportWithoutRawEvidence() throws Exception {
        EnterprisePilotReadinessInboundPort port = mock(EnterprisePilotReadinessInboundPort.class);
        EnterprisePilotReadinessReport report = report();
        when(port.generate(any())).thenReturn(report);
        when(port.latest("tenant-a", "agent-1", "version-1")).thenReturn(Optional.of(report));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseEnterprisePilotReadinessController(
                        provider(EnterprisePilotReadinessInboundPort.class, port))).build();

        mvc.perform(post("/api/agents/agent-1/versions/version-1/pilot-readiness/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "operator": "operator-1",
                                  "rawPrompt": "secret-token",
                                  "rawToolOutput": "secret-token",
                                  "stackTrace": "secret-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reportId").value("report-1"))
                .andExpect(jsonPath("$.data.status").value("FAIL"))
                .andExpect(jsonPath("$.data.checkResults.length()").value(9))
                .andExpect(jsonPath("$.data.checkResults[0].code").value("OWNER"))
                .andExpect(content().string(not(containsString("secret-token"))))
                .andExpect(content().string(not(containsString("rawPrompt"))))
                .andExpect(content().string(not(containsString("rawToolOutput"))))
                .andExpect(content().string(not(containsString("stackTrace"))));

        ArgumentCaptor<EnterprisePilotReadinessGenerateCommand> commandCaptor =
                ArgumentCaptor.forClass(EnterprisePilotReadinessGenerateCommand.class);
        verify(port).generate(commandCaptor.capture());
        assertThat(commandCaptor.getValue().agentId()).isEqualTo("agent-1");
        assertThat(commandCaptor.getValue().versionId()).isEqualTo("version-1");

        mvc.perform(get("/api/agents/agent-1/versions/version-1/pilot-readiness/latest")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value("report-1"))
                .andExpect(jsonPath("$.data.checkResults[5].code").value("QUOTA"));
        verify(port).latest("tenant-a", "agent-1", "version-1");
    }

    private static EnterprisePilotReadinessReport report() {
        List<EnterprisePilotReadinessCheckResult> results = EnterprisePilotReadinessCheckCode.all().stream()
                .map(SeahorseEnterprisePilotReadinessControllerTests::result)
                .toList();
        return new EnterprisePilotReadinessReport(
                "report-1",
                "tenant-a",
                "agent-1",
                "version-1",
                null,
                results,
                NOW);
    }

    private static EnterprisePilotReadinessCheckResult result(EnterprisePilotReadinessCheckCode code) {
        EnterprisePilotReadinessStatus status = code == EnterprisePilotReadinessCheckCode.EVAL
                ? EnterprisePilotReadinessStatus.FAIL
                : EnterprisePilotReadinessStatus.PASS;
        EnterprisePilotReadinessReasonCode reasonCode = status == EnterprisePilotReadinessStatus.PASS
                ? EnterprisePilotReadinessReasonCode.READY
                : EnterprisePilotReadinessReasonCode.EVAL_FAILED;
        return new EnterprisePilotReadinessCheckResult(
                code,
                status,
                reasonCode,
                "evidence:" + code.name(),
                code.name() + " check",
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
