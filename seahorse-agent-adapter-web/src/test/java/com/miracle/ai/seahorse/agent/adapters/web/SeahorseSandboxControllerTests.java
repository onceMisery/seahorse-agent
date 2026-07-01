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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseSandboxControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeSandboxRuntimeApis() throws Exception {
        SandboxRuntimeInboundPort port = mock(SandboxRuntimeInboundPort.class);
        when(port.createSession(any())).thenReturn(session(SandboxExecutionStatus.CREATED));
        when(port.execute(any())).thenReturn(SandboxExecutionResult.failed(
                SandboxExecution.failed(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.CODE_INTERPRETER,
                        NOW.plusSeconds(1),
                        SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED),
                SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED));
        when(port.close("session-1")).thenReturn(session(SandboxExecutionStatus.CANCELLED));
        when(port.listExecutions("session-1")).thenReturn(List.of(SandboxExecution.failed(
                "exec-1",
                "session-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW.plusSeconds(1),
                SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED)));
        when(port.listArtifacts("session-1")).thenReturn(List.of(artifact()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSandboxController(
                        provider(SandboxRuntimeInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests())).build();

        mvc.perform(post("/api/sandbox/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-a",
                                "runId", "run-1",
                                "runtimeType", "CODE_INTERPRETER",
                                "networkRequested", false,
                                "requestedHosts", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        ArgumentCaptor<SandboxSessionCreateCommand> createCaptor =
                ArgumentCaptor.forClass(SandboxSessionCreateCommand.class);
        verify(port).createSession(createCaptor.capture());
        assertThat(createCaptor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(createCaptor.getValue().runtimeType()).isEqualTo(SandboxRuntimeType.CODE_INTERPRETER);

        mvc.perform(post("/api/sandbox/sessions/session-1/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "input", "print('hello')",
                                "networkRequested", false,
                                "requestedHosts", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.execution.status").value("FAILED"))
                .andExpect(jsonPath("$.data.reasonCode").value("RUNTIME_UNSUPPORTED"));

        ArgumentCaptor<SandboxExecutionCommand> executeCaptor =
                ArgumentCaptor.forClass(SandboxExecutionCommand.class);
        verify(port).execute(executeCaptor.capture());
        assertThat(executeCaptor.getValue().sessionId()).isEqualTo("session-1");
        assertThat(executeCaptor.getValue().input()).isEqualTo("print('hello')");

        mvc.perform(post("/api/sandbox/sessions/session-1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        verify(port).close("session-1");

        mvc.perform(get("/api/sandbox/sessions/session-1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].executionId").value("exec-1"))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].reasonCode").value("RUNTIME_UNSUPPORTED"));
        verify(port).listExecutions("session-1");

        mvc.perform(get("/api/sandbox/sessions/session-1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].artifactId").value("artifact-clean"))
                .andExpect(jsonPath("$.data[0].scanStatus").value("CLEAN"))
                .andExpect(jsonPath("$.data[0].sensitivity").value("INTERNAL"));
        verify(port).listArtifacts("session-1");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static SandboxSession session(SandboxExecutionStatus status) {
        return new SandboxSession(
                "session-1",
                "tenant-a",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                status,
                SandboxPolicyReasonCode.VALID_REQUEST,
                NOW,
                NOW);
    }

    private static SandboxArtifact artifact() {
        return new SandboxArtifact(
                "artifact-clean",
                "session-1",
                "exec-1",
                "s3://sandbox/artifact-clean",
                "text/plain",
                SandboxArtifactScanStatus.CLEAN,
                ContextSensitivity.INTERNAL,
                NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
