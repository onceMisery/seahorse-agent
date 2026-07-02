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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseSandboxControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        when(port.listSessions("tenant-a", 20)).thenReturn(List.of(session(SandboxExecutionStatus.CREATED)));
        when(port.sweepExpiredSessions("tenant-a", 20)).thenReturn(new SandboxSessionSweepResult(
                "tenant-a",
                NOW,
                1,
                1,
                0,
                List.of(session(SandboxExecutionStatus.TIMED_OUT))));
        when(port.sweepOrphanedRuntimeResources()).thenReturn(new SandboxRuntimeCleanupResult(
                NOW,
                1,
                2,
                1,
                0,
                1,
                0,
                List.of("sandbox_container_orphan"),
                List.of()));
        when(port.listExecutions("session-1")).thenReturn(List.of(SandboxExecution.failed(
                "exec-1",
                "session-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW.plusSeconds(1),
                SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED)));
        SandboxArtifact artifact = artifact();
        when(port.listArtifacts("session-1")).thenReturn(List.of(artifact));
        when(port.describeArtifact("artifact-clean")).thenReturn(new SandboxArtifactDetailDecision(
                artifact,
                "text/plain",
                "artifact-clean.txt",
                true,
                null));
        when(port.downloadArtifact("artifact-clean")).thenReturn(new SandboxArtifactDownloadDecision(
                artifact,
                "text/plain",
                "artifact-clean.txt",
                "local://sandbox-artifacts/artifact-clean.txt"));
        ObjectStoragePort storagePort = mock(ObjectStoragePort.class);
        when(storagePort.openStream("local://sandbox-artifacts/artifact-clean.txt"))
                .thenReturn(new ByteArrayInputStream("download body".getBytes(StandardCharsets.UTF_8)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSandboxController(
                        provider(SandboxRuntimeInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests(),
                        provider(ObjectStoragePort.class, storagePort)))
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new ResourceHttpMessageConverter())
                .build();

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
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.profileId").value("python-small"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-05-26T01:00:00Z"));

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

        mvc.perform(get("/api/sandbox/sessions")
                        .param("tenantId", "tenant-a")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$.data[0].tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.data[0].runtimeType").value("CODE_INTERPRETER"))
                .andExpect(jsonPath("$.data[0].profileId").value("python-small"))
                .andExpect(jsonPath("$.data[0].expiresAt").value("2026-05-26T01:00:00Z"));
        verify(port).listSessions("tenant-a", 20);

        mvc.perform(post("/api/sandbox/sessions/expired:sweep")
                        .param("tenantId", "tenant-a")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.data.matchedCount").value(1))
                .andExpect(jsonPath("$.data.closedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.closedSessions[0].status").value("TIMED_OUT"));
        verify(port).sweepExpiredSessions("tenant-a", 20);

        mvc.perform(post("/api/sandbox/runtime/orphans:sweep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeSessionCount").value(1))
                .andExpect(jsonPath("$.data.inspectedWorkspaceCount").value(2))
                .andExpect(jsonPath("$.data.skippedActiveWorkspaceCount").value(1))
                .andExpect(jsonPath("$.data.removedWorkspaceCount").value(1))
                .andExpect(jsonPath("$.data.failedWorkspaceCount").value(0))
                .andExpect(jsonPath("$.data.removedWorkspaceNames[0]").value("sandbox_container_orphan"));
        verify(port).sweepOrphanedRuntimeResources();

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
                .andExpect(jsonPath("$.data[0].sensitivity").value("INTERNAL"))
                .andExpect(jsonPath("$.data[0].promptVisible").value(true));
        verify(port).listArtifacts("session-1");

        mvc.perform(get("/api/sandbox/artifacts/artifact-clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.artifactId").value("artifact-clean"))
                .andExpect(jsonPath("$.data.contentType").value("text/plain"))
                .andExpect(jsonPath("$.data.filename").value("artifact-clean.txt"))
                .andExpect(jsonPath("$.data.downloadable").value(true))
                .andExpect(jsonPath("$.data.objectUri").doesNotExist())
                .andExpect(jsonPath("$.data.storageRef").doesNotExist());
        verify(port).describeArtifact("artifact-clean");

        mvc.perform(get("/api/sandbox/artifacts/artifact-clean/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"artifact-clean.txt\""))
                .andExpect(content().string("download body"));
        verify(port).downloadArtifact("artifact-clean");
        verify(storagePort).openStream("local://sandbox-artifacts/artifact-clean.txt");
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
