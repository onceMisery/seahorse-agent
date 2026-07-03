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
import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.DefaultSandboxPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaPolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeProfilePolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
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

import static org.hamcrest.Matchers.containsString;
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
        when(port.inspectRuntimeHealth()).thenReturn(new SandboxRuntimeHealth(
                NOW,
                "container",
                "docker",
                SandboxRuntimeHealth.STATUS_HEALTHY,
                true,
                true,
                4096L,
                1024L,
                true,
                SandboxRuntimeHealth.DISK_AVAILABLE,
                1,
                3,
                2,
                true,
                SandboxRuntimeHealth.CAPACITY_AVAILABLE,
                1,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of()));
        when(port.inspectArtifactScannerPolicy()).thenReturn(defaultScannerPolicy());
        when(port.listRuntimeProfilePolicies("default")).thenReturn(List.of(
                runtimeProfilePolicy("default", SandboxRuntimeType.CODE_INTERPRETER, SandboxRuntimeProfilePolicyStatus.ACTIVE, 3600),
                runtimeProfilePolicy("default", SandboxRuntimeType.FILE_CONVERSION, SandboxRuntimeProfilePolicyStatus.ACTIVE, 3600),
                runtimeProfilePolicy("default", SandboxRuntimeType.BROWSER_AUTOMATION, SandboxRuntimeProfilePolicyStatus.ACTIVE, 3600),
                runtimeProfilePolicy("default", SandboxRuntimeType.SHELL, SandboxRuntimeProfilePolicyStatus.ACTIVE, 3600)));
        when(port.upsertRuntimeProfilePolicy(any())).thenReturn(runtimeProfilePolicy(
                "default",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxRuntimeProfilePolicyStatus.ACTIVE,
                120));
        when(port.reapOrphanedRuntimeContainers(false)).thenReturn(new SandboxRuntimeContainerReapResult(
                NOW,
                false,
                1,
                1,
                0,
                1,
                0,
                1,
                0,
                List.of(),
                List.of("seahorse-sandbox-orphan-live"),
                List.of("seahorse-sandbox-orphan-live"),
                List.of(),
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
        QuotaManagementInboundPort quotaPort = mock(QuotaManagementInboundPort.class);
        when(quotaPort.upsertPolicy(any())).thenReturn(new QuotaPolicy(
                "sandbox-tool-policy-1",
                "tenant-a",
                QuotaScope.TOOL,
                "sandbox_python",
                QuotaPolicyStatus.ACTIVE,
                null,
                3L,
                null,
                0.8,
                NOW,
                NOW));
        ObjectStoragePort storagePort = mock(ObjectStoragePort.class);
        when(storagePort.openStream("local://sandbox-artifacts/artifact-clean.txt"))
                .thenReturn(new ByteArrayInputStream("download body".getBytes(StandardCharsets.UTF_8)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSandboxController(
                        provider(SandboxRuntimeInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests(),
                        provider(ObjectStoragePort.class, storagePort),
                        provider(QuotaManagementInboundPort.class, quotaPort)))
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

        mvc.perform(get("/api/sandbox/runtime/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runtime").value("container"))
                .andExpect(jsonPath("$.data.engine").value("docker"))
                .andExpect(jsonPath("$.data.status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.engineAvailable").value(true))
                .andExpect(jsonPath("$.data.workspaceAvailable").value(true))
                .andExpect(jsonPath("$.data.workspaceFreeBytes").value(4096))
                .andExpect(jsonPath("$.data.workspaceMinFreeBytes").value(1024))
                .andExpect(jsonPath("$.data.workspaceDiskAvailable").value(true))
                .andExpect(jsonPath("$.data.workspaceDiskStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.activeSessionCount").value(1))
                .andExpect(jsonPath("$.data.activeSessionLimit").value(3))
                .andExpect(jsonPath("$.data.activeSessionRemaining").value(2))
                .andExpect(jsonPath("$.data.activeSessionCapacityAvailable").value(true))
                .andExpect(jsonPath("$.data.capacityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.inspectedContainerCount").value(1));
        verify(port).inspectRuntimeHealth();

        mvc.perform(get("/api/sandbox/runtime/artifact-scanner-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scannerId").value("default-local-bounded"))
                .andExpect(jsonPath("$.data.scannerMode").value("LOCAL_METADATA_AND_BOUNDED_CONTENT"))
                .andExpect(jsonPath("$.data.failClosed").value(true))
                .andExpect(jsonPath("$.data.rawFindingValuesPersisted").value(false))
                .andExpect(jsonPath("$.data.maxContentScanBytes").value(262144))
                .andExpect(jsonPath("$.data.maxArchiveScanEntries").value(128))
                .andExpect(jsonPath("$.data.downloadOnlyMediaTypes[0]").value("application/zip"))
                .andExpect(jsonPath("$.data.blockedCategories[0]").value("OFFICE_MACRO"))
                .andExpect(jsonPath("$.data.unsupportedCapabilities[0]").value("external virus scanning"));
        verify(port).inspectArtifactScannerPolicy();

        mvc.perform(get("/api/sandbox/runtime/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultNetworkPolicy").value("DENY_ALL"))
                .andExpect(jsonPath("$.data.allowlistedHosts.length()").value(0))
                .andExpect(jsonPath("$.data.defaultTtlSeconds").value(3600))
                .andExpect(jsonPath("$.data.profiles[0].runtimeType").value("CODE_INTERPRETER"))
                .andExpect(jsonPath("$.data.profiles[0].profileId").value("python-small"))
                .andExpect(jsonPath("$.data.profiles[0].supportedByContainerRuntime").value(true))
                .andExpect(jsonPath("$.data.profiles[0].networkAllowed").value(false))
                .andExpect(jsonPath("$.data.profiles[0].status").value("SUPPORTED"))
                .andExpect(jsonPath("$.data.profiles[0].policyId").value("sandbox-runtime-profile-default-code_interpreter"))
                .andExpect(jsonPath("$.data.profiles[0].policyStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.profiles[0].sessionTtlSeconds").value(3600))
                .andExpect(jsonPath("$.data.profiles[1].runtimeType").value("FILE_CONVERSION"))
                .andExpect(jsonPath("$.data.profiles[1].profileId").value("file-conversion"))
                .andExpect(jsonPath("$.data.profiles[1].supportedByContainerRuntime").value(true))
                .andExpect(jsonPath("$.data.profiles[1].networkAllowed").value(false))
                .andExpect(jsonPath("$.data.profiles[1].status").value("SUPPORTED"))
                .andExpect(jsonPath("$.data.profiles[2].runtimeType").value("BROWSER_AUTOMATION"))
                .andExpect(jsonPath("$.data.profiles[2].profileId").value("browser-readonly"))
                .andExpect(jsonPath("$.data.profiles[2].supportedByContainerRuntime").value(true))
                .andExpect(jsonPath("$.data.profiles[2].networkAllowed").value(false))
                .andExpect(jsonPath("$.data.profiles[2].status").value("SUPPORTED"))
                .andExpect(jsonPath("$.data.profiles[3].runtimeType").value("SHELL"))
                .andExpect(jsonPath("$.data.profiles[3].profileId").value("shell-restricted"))
                .andExpect(jsonPath("$.data.profiles[3].supportedByContainerRuntime").value(false))
                .andExpect(jsonPath("$.data.profiles[3].networkAllowed").value(false))
                .andExpect(jsonPath("$.data.profiles[3].status").value("PLANNED"));
        verify(port).listRuntimeProfilePolicies("default");

        mvc.perform(post("/api/sandbox/runtime/profile-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "default",
                                "runtimeType", "CODE_INTERPRETER",
                                "profileId", "python-small",
                                "status", "ACTIVE",
                                "sessionTtlSeconds", 120,
                                "networkAllowed", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyId").value("sandbox-runtime-profile-default-code_interpreter"))
                .andExpect(jsonPath("$.data.tenantId").value("default"))
                .andExpect(jsonPath("$.data.runtimeType").value("CODE_INTERPRETER"))
                .andExpect(jsonPath("$.data.profileId").value("python-small"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sessionTtlSeconds").value(120))
                .andExpect(jsonPath("$.data.networkAllowed").value(false));
        ArgumentCaptor<SandboxRuntimeProfilePolicyUpsertCommand> runtimePolicyCaptor =
                ArgumentCaptor.forClass(SandboxRuntimeProfilePolicyUpsertCommand.class);
        verify(port).upsertRuntimeProfilePolicy(runtimePolicyCaptor.capture());
        assertThat(runtimePolicyCaptor.getValue().tenantId()).isEqualTo("default");
        assertThat(runtimePolicyCaptor.getValue().runtimeType()).isEqualTo(SandboxRuntimeType.CODE_INTERPRETER);
        assertThat(runtimePolicyCaptor.getValue().sessionTtlSeconds()).isEqualTo(120L);

        mvc.perform(post("/api/sandbox/runtime/tool-quota-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "policyId", "sandbox-tool-policy-1",
                                "tenantId", "tenant-a",
                                "toolId", "sandbox_python",
                                "callLimit", 3,
                                "warnRatio", 0.8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyId").value("sandbox-tool-policy-1"))
                .andExpect(jsonPath("$.data.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.data.scope").value("TOOL"))
                .andExpect(jsonPath("$.data.subjectId").value("sandbox_python"))
                .andExpect(jsonPath("$.data.callLimit").value(3));
        ArgumentCaptor<QuotaPolicyUpsertCommand> quotaCaptor =
                ArgumentCaptor.forClass(QuotaPolicyUpsertCommand.class);
        verify(quotaPort).upsertPolicy(quotaCaptor.capture());
        assertThat(quotaCaptor.getValue().scope()).isEqualTo(QuotaScope.TOOL);
        assertThat(quotaCaptor.getValue().subjectId()).isEqualTo("sandbox_python");
        assertThat(quotaCaptor.getValue().callLimit()).isEqualTo(3L);

        mvc.perform(post("/api/sandbox/runtime/orphan-containers:reap")
                        .param("dryRun", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.activeSessionCount").value(1))
                .andExpect(jsonPath("$.data.orphanContainerCount").value(1))
                .andExpect(jsonPath("$.data.reapedContainerCount").value(1))
                .andExpect(jsonPath("$.data.failedContainerCount").value(0))
                .andExpect(jsonPath("$.data.reapedContainerNames[0]").value("seahorse-sandbox-orphan-live"));
        verify(port).reapOrphanedRuntimeContainers(false);

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
                .andExpect(jsonPath("$.data[0].scanSummary").value("metadata scan passed"))
                .andExpect(jsonPath("$.data[0].redactionSummaryJson").value(containsString("\"decision\":\"CLEAN\"")))
                .andExpect(jsonPath("$.data[0].promptVisible").value(true));
        verify(port).listArtifacts("session-1");

        mvc.perform(get("/api/sandbox/artifacts/artifact-clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.artifactId").value("artifact-clean"))
                .andExpect(jsonPath("$.data.contentType").value("text/plain"))
                .andExpect(jsonPath("$.data.filename").value("artifact-clean.txt"))
                .andExpect(jsonPath("$.data.scanSummary").value("metadata scan passed"))
                .andExpect(jsonPath("$.data.redactionSummaryJson").value(containsString("\"decision\":\"CLEAN\"")))
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

    @Test
    void shouldExposeConfiguredSandboxNetworkPolicyInRuntimeProfiles() throws Exception {
        SandboxRuntimeInboundPort port = mock(SandboxRuntimeInboundPort.class);
        when(port.listRuntimeProfilePolicies("default")).thenReturn(List.of());
        ObjectStoragePort storagePort = mock(ObjectStoragePort.class);
        QuotaManagementInboundPort quotaPort = mock(QuotaManagementInboundPort.class);
        SandboxPolicyPort policyPort = new DefaultSandboxPolicyPort(
                SandboxNetworkPolicy.ALLOWLISTED,
                List.of("host.docker.internal"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSandboxController(
                        provider(SandboxRuntimeInboundPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests(),
                        provider(ObjectStoragePort.class, storagePort),
                        provider(QuotaManagementInboundPort.class, quotaPort),
                        provider(SandboxPolicyPort.class, policyPort)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        mvc.perform(get("/api/sandbox/runtime/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultNetworkPolicy").value("ALLOWLISTED"))
                .andExpect(jsonPath("$.data.allowlistedHosts[0]").value("host.docker.internal"));
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
                "metadata scan passed",
                NOW);
    }

    private static SandboxRuntimeProfilePolicy runtimeProfilePolicy(String tenantId,
                                                                    SandboxRuntimeType runtimeType,
                                                                    SandboxRuntimeProfilePolicyStatus status,
                                                                    long ttlSeconds) {
        return new SandboxRuntimeProfilePolicy(
                null,
                tenantId,
                runtimeType,
                null,
                status,
                ttlSeconds,
                false,
                NOW,
                NOW);
    }

    private static SandboxArtifactScannerPolicy defaultScannerPolicy() {
        return new SandboxArtifactScannerPolicy(
                "default-local-bounded",
                "LOCAL_METADATA_AND_BOUNDED_CONTENT",
                true,
                false,
                262144,
                262144,
                128,
                262144,
                List.of("application/json", "text/*"),
                List.of("application/zip", "video/webm"),
                List.of("application/json", "text/*"),
                List.of("application/pdf", "video/webm"),
                List.of("application/zip"),
                List.of("OFFICE_MACRO", "PDF_ACTIVE_CONTENT"),
                List.of("CONFIDENTIAL_METADATA"),
                List.of("external virus scanning"));
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
