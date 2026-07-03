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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxFileConvertToolPortAdapterTests {

    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(objectMapper);

    @Test
    void shouldExecuteCsvToJsonThroughFileConversionRuntime() throws Exception {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.succeeded(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.FILE_CONVERSION,
                        SandboxExecutionStatus.SUCCEEDED,
                        "exitCode=0; stdout=converted 2 rows from csv to json",
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        NOW,
                        NOW),
                List.of(new SandboxArtifact(
                        "artifact-1",
                        "session-1",
                        "exec-1",
                        "local://sandbox-artifacts/converted.json",
                        "application/json",
                        SandboxArtifactScanStatus.CLEAN,
                        ContextSensitivity.INTERNAL,
                        "metadata scan passed",
                        NOW))));
        SandboxFileConvertToolPortAdapter adapter = new SandboxFileConvertToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "sourceFormat", "csv",
                "targetFormat", "json",
                "content", "name,score\nAda,42\nGrace,99\n")));

        assertTrue(result.success());
        assertEquals("tenant-1", runtime.createCommand.tenantId());
        assertEquals("run-1", runtime.createCommand.runId());
        assertEquals(SandboxRuntimeType.FILE_CONVERSION, runtime.createCommand.runtimeType());
        assertFalse(runtime.createCommand.networkRequested());
        assertEquals(List.of(), runtime.createCommand.requestedHosts());
        assertEquals("session-1", runtime.executeCommand.sessionId());
        assertFalse(runtime.executeCommand.networkRequested());
        assertEquals(List.of(), runtime.executeCommand.requestedHosts());
        assertEquals("session-1", runtime.closedSessionId);

        JsonNode conversionInput = objectMapper.readTree(runtime.executeCommand.input());
        assertEquals("csv", conversionInput.path("sourceFormat").asText());
        assertEquals("json", conversionInput.path("targetFormat").asText());
        assertEquals("name,score\nAda,42\nGrace,99\n", conversionInput.path("content").asText());

        JsonNode root = objectMapper.readTree(result.content());
        assertEquals(SandboxFileConvertToolPortAdapter.TOOL_ID, root.path("toolId").asText());
        assertEquals("FILE_CONVERSION", root.path("runtimeType").asText());
        assertEquals("SUCCEEDED", root.path("executionStatus").asText());
        assertEquals("csv", root.path("conversion").path("sourceFormat").asText());
        assertEquals("json", root.path("conversion").path("targetFormat").asText());
        assertEquals("application/json", root.path("artifacts").get(0).path("mediaType").asText());
        assertEquals("metadata scan passed", root.path("artifacts").get(0).path("scanSummary").asText());
        assertTrue(root.path("artifacts").get(0).path("promptVisible").asBoolean());
    }

    @Test
    void shouldRejectUnsupportedConversionBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxFileConvertToolPortAdapter adapter = new SandboxFileConvertToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "sourceFormat", "xml",
                "targetFormat", "json",
                "content", "<root/>")));

        assertFalse(result.success());
        assertTrue(result.error().contains("supported conversion"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectBlankContentBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxFileConvertToolPortAdapter adapter = new SandboxFileConvertToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "sourceFormat", "csv",
                "targetFormat", "json",
                "content", " ")));

        assertFalse(result.success());
        assertTrue(result.error().contains("content is required"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldCloseSessionWhenFileConversionExecutionFails() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.failed(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.FILE_CONVERSION,
                        SandboxExecutionStatus.FAILED,
                        "exitCode=1; stderr=bad csv",
                        SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                        NOW,
                        NOW),
                SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED));
        SandboxFileConvertToolPortAdapter adapter = new SandboxFileConvertToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "sourceFormat", "csv",
                "targetFormat", "json",
                "content", "name\nAda\n")));

        assertFalse(result.success());
        assertTrue(result.error().contains("RUNTIME_EXECUTION_FAILED"));
        assertTrue(result.error().contains("bad csv"));
        assertEquals("session-1", runtime.closedSessionId);
    }

    private ToolInvocationRequest request(Map<String, Object> arguments) {
        return new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "rollout-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                SandboxFileConvertToolPortAdapter.TOOL_ID,
                arguments,
                Map.of(),
                "run-1:call-1",
                List.of(SandboxFileConvertToolPortAdapter.TOOL_ID));
    }

    private static final class RecordingSandboxRuntime implements SandboxRuntimeInboundPort {

        private final SandboxExecutionResult executionResult;
        private SandboxSession session;
        private SandboxSessionCreateCommand createCommand;
        private SandboxExecutionCommand executeCommand;
        private String closedSessionId;
        private int createCalls;

        private RecordingSandboxRuntime(SandboxExecutionResult executionResult) {
            this.executionResult = executionResult;
        }

        @Override
        public SandboxSession createSession(SandboxSessionCreateCommand command) {
            createCalls++;
            createCommand = command;
            session = SandboxSession.created(
                    "session-1",
                    command.tenantId(),
                    command.runId(),
                    command.runtimeType(),
                    NOW);
            return session;
        }

        @Override
        public SandboxExecutionResult execute(SandboxExecutionCommand command) {
            executeCommand = command;
            return executionResult;
        }

        @Override
        public SandboxSession close(String sessionId) {
            closedSessionId = sessionId;
            return session.closed(NOW);
        }

        @Override
        public List<SandboxSession> listSessions(String tenantId, int limit) {
            return List.of();
        }

        @Override
        public SandboxSessionSweepResult sweepExpiredSessions(String tenantId, int limit) {
            return new SandboxSessionSweepResult(tenantId, NOW, 0, 0, 0, List.of());
        }

        @Override
        public SandboxRuntimeCleanupResult sweepOrphanedRuntimeResources() {
            return SandboxRuntimeCleanupResult.empty(NOW, 0);
        }

        @Override
        public SandboxRuntimeHealth inspectRuntimeHealth() {
            return SandboxRuntimeHealth.unsupported(NOW, 0);
        }

        @Override
        public SandboxRuntimeContainerReapResult reapOrphanedRuntimeContainers(boolean dryRun) {
            return SandboxRuntimeContainerReapResult.empty(NOW, dryRun, 0);
        }

        @Override
        public List<SandboxExecution> listExecutions(String sessionId) {
            return List.of();
        }

        @Override
        public List<SandboxArtifact> listArtifacts(String sessionId) {
            return List.of();
        }

        @Override
        public SandboxArtifactDetailDecision describeArtifact(String artifactId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SandboxArtifactDownloadDecision downloadArtifact(String artifactId) {
            throw new UnsupportedOperationException();
        }
    }
}
