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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxPythonToolPortAdapterTests {

    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(objectMapper);

    @Test
    void shouldExecutePythonThroughSandboxWithToolInvocationContext() throws Exception {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.succeeded(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.CODE_INTERPRETER,
                        SandboxExecutionStatus.SUCCEEDED,
                        "exitCode=0; stdout=42",
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        NOW,
                        NOW),
                List.of()));
        SandboxPythonToolPortAdapter adapter = new SandboxPythonToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "code", "print(40 + 2)",
                "networkRequested", false)));

        assertTrue(result.success());
        assertEquals("tenant-1", runtime.createCommand.tenantId());
        assertEquals("run-1", runtime.createCommand.runId());
        assertEquals(SandboxRuntimeType.CODE_INTERPRETER, runtime.createCommand.runtimeType());
        assertFalse(runtime.createCommand.networkRequested());
        assertEquals("session-1", runtime.executeCommand.sessionId());
        assertEquals("print(40 + 2)", runtime.executeCommand.input());
        assertEquals("session-1", runtime.closedSessionId);
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals(SandboxPythonToolPortAdapter.TOOL_ID, root.path("toolId").asText());
        assertEquals("session-1", root.path("sessionId").asText());
        assertEquals("exec-1", root.path("executionId").asText());
        assertEquals("SUCCEEDED", root.path("executionStatus").asText());
        assertTrue(root.path("resultSummary").asText().contains("stdout=42"));
    }

    @Test
    void shouldRejectBlankCodeBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxPythonToolPortAdapter adapter = new SandboxPythonToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of("code", " ")));

        assertFalse(result.success());
        assertTrue(result.error().contains("code is required"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldCloseSessionWhenSandboxExecutionFails() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.failed(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.CODE_INTERPRETER,
                        SandboxExecutionStatus.FAILED,
                        "exitCode=1; stderr=boom",
                        SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                        NOW,
                        NOW),
                SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED));
        SandboxPythonToolPortAdapter adapter = new SandboxPythonToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of("code", "raise Exception('boom')")));

        assertFalse(result.success());
        assertTrue(result.error().contains("RUNTIME_EXECUTION_FAILED"));
        assertTrue(result.error().contains("stderr=boom"));
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
                SandboxPythonToolPortAdapter.TOOL_ID,
                arguments,
                Map.of(),
                "run-1:call-1",
                List.of(SandboxPythonToolPortAdapter.TOOL_ID));
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
        public List<SandboxExecution> listExecutions(String sessionId) {
            return List.of();
        }

        @Override
        public List<SandboxArtifact> listArtifacts(String sessionId) {
            return List.of();
        }
    }
}
