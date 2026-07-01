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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SandboxArtifactToAgentArtifactPolicyTests {

    @Test
    void sandboxArtifactMetadataShouldNotExposeObjectStorageUri() throws Exception {
        SandboxRuntimeInboundPort port = mock(SandboxRuntimeInboundPort.class);
        when(port.listArtifacts("session-1")).thenReturn(List.of(artifact()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSandboxController(
                        provider(SandboxRuntimeInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests())).build();

        mvc.perform(get("/api/sandbox/sessions/session-1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].artifactId").value("artifact-clean"))
                .andExpect(jsonPath("$.data[0].scanStatus").value("CLEAN"))
                .andExpect(jsonPath("$.data[0].sensitivity").value("INTERNAL"))
                .andExpect(jsonPath("$.data[0].promptVisible").value(true))
                .andExpect(jsonPath("$.data[0].objectUri").doesNotExist());
    }

    @Test
    void sandboxExecutionResultShouldNotExposeArtifactObjectStorageUri() throws Exception {
        SandboxRuntimeInboundPort port = mock(SandboxRuntimeInboundPort.class);
        when(port.execute(any())).thenReturn(SandboxExecutionResult.succeeded(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.CODE_INTERPRETER,
                        SandboxExecutionStatus.SUCCEEDED,
                        "ok",
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        Instant.parse("2026-05-26T00:00:00Z"),
                        Instant.parse("2026-05-26T00:00:00Z")),
                List.of(artifact())));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSandboxController(
                        provider(SandboxRuntimeInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests())).build();

        mvc.perform(post("/api/sandbox/sessions/session-1/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "input", "print('hello')",
                                "networkRequested", false,
                                "requestedHosts", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.execution.executionId").value("exec-1"))
                .andExpect(jsonPath("$.data.artifacts[0].artifactId").value("artifact-clean"))
                .andExpect(jsonPath("$.data.artifacts[0].promptVisible").value(true))
                .andExpect(jsonPath("$.data.artifacts[0].objectUri").doesNotExist());
    }

    private static SandboxArtifact artifact() {
        return new SandboxArtifact(
                "artifact-clean",
                "session-1",
                "exec-1",
                "s3://sandbox/internal-object",
                "text/plain",
                SandboxArtifactScanStatus.CLEAN,
                ContextSensitivity.INTERNAL,
                Instant.parse("2026-05-26T00:00:00Z"));
    }

    private static String json(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
