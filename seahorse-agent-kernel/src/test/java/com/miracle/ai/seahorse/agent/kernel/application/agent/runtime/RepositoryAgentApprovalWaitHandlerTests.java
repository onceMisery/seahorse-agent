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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryAgentApprovalWaitHandlerTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldRedactSerializationFailureFallbackBeforePersistingCheckpoint() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(new AgentRun(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "question",
                AgentRunStatus.RUNNING,
                null,
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        RepositoryAgentApprovalWaitHandler handler = new RepositoryAgentApprovalWaitHandler(
                runRepository,
                checkpointRepository,
                FIXED_CLOCK,
                new FailingObjectMapper(
                        "checkpoint failed Authorization: Bearer checkpoint-secret-123456 api_key=plain-checkpoint-secret"));

        handler.waitForApproval(new AgentApprovalWaitCommand(
                new ToolInvocationRequest(
                        "run-1",
                        "step-1",
                        "call-1",
                        "agent-1",
                        "version-1",
                        "tenant-1",
                        "user-1",
                        "identity-1",
                        "tool-1",
                        Map.of("query", "safe"),
                        Map.of(),
                        "idem-1",
                        List.of("tool-1")),
                "{\"exitReason\":\"WAITING_APPROVAL\"}",
                List.of(thinkingMessage()),
                resumeDescriptor()));

        AgentCheckpoint checkpoint = checkpointRepository.checkpoints.get(0);
        assertEquals(AgentRunStatus.WAITING_APPROVAL, runRepository.runs.get("run-1").status());
        assertTrue(checkpoint.messageHistoryJson().contains("[REDACTED]"), checkpoint.messageHistoryJson());
        assertTrue(checkpoint.pendingToolCallJson().contains("[REDACTED]"), checkpoint.pendingToolCallJson());
        assertFalse(checkpoint.messageHistoryJson().contains("checkpoint-secret-123456"));
        assertFalse(checkpoint.pendingToolCallJson().contains("plain-checkpoint-secret"));
        assertFalse(checkpoint.pendingToolCallJson().contains("idem-1"));
        assertFalse(checkpoint.messageHistoryJson().contains("private reasoning"));
    }

    @Test
    void shouldRecursivelyRedactCredentialShapedCheckpointData() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(new AgentRun(
                "run-1", "agent-1", "version-1", "tenant-1", "user-1", "conversation-1",
                AgentRunTriggerType.CHAT, "question", AgentRunStatus.RUNNING, null, 0L, 0L,
                BigDecimal.ZERO, null, null, FIXED_CLOCK.instant(), null));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        RepositoryAgentApprovalWaitHandler handler = new RepositoryAgentApprovalWaitHandler(
                runRepository, checkpointRepository, FIXED_CLOCK);
        ChatMessage message = ChatMessage.user(
                "continue with api_key=message-secret-123456 and {\"password\":\"json-secret-123456\"}");
        message.setToolCalls(List.of(AgentToolCall.of(
                "call-1", "tool-1", Map.of("nested", Map.of("access_token", "argument-secret-123456")))));

        handler.waitForApproval(new AgentApprovalWaitCommand(
                new ToolInvocationRequest(
                        "run-1", "step-1", "call-1", "agent-1", "version-1", "tenant-1", "user-1",
                        "identity-1", "tool-1",
                        Map.of("items", List.of(Map.of("client_secret", "argument-secret-123456"))),
                        Map.of("document", "{\"authorization\":\"Bearer resource-secret-123456\"}"),
                        "idem-1", List.of("tool-1")),
                "{\"runtime\":{\"password\":\"state-secret-123456\"}}",
                List.of(message),
                new AgentResumeDescriptor(
                        AgentResumeDescriptor.SCHEMA_VERSION,
                        "test-model",
                        new AgentResumeDescriptor.SamplingSnapshot(0.2D, null, null, 100, false),
                        AgentResumeDescriptor.RuntimeContextMode.SNAPSHOT,
                        "{\"apiKey\":\"runtime-secret-123456\"}",
                        "cookie=session-secret-123456",
                        null,
                        List.of())));

        AgentCheckpoint checkpoint = checkpointRepository.checkpoints.get(0);
        String persisted = checkpoint.stateJson()
                + checkpoint.messageHistoryJson()
                + checkpoint.pendingToolCallJson();
        assertTrue(persisted.contains("[REDACTED]"), persisted);
        assertFalse(checkpoint.pendingToolCallJson().contains("idem-1"));
        for (String secret : List.of(
                "message-secret-123456", "json-secret-123456", "argument-secret-123456",
                "resource-secret-123456", "state-secret-123456", "runtime-secret-123456",
                "session-secret-123456")) {
            assertFalse(persisted.contains(secret), persisted);
        }
    }

    private static ChatMessage thinkingMessage() {
        ChatMessage message = ChatMessage.user("safe question");
        message.setThinkingContent("private reasoning");
        message.setThinkingDuration(42);
        return message;
    }

    private static AgentResumeDescriptor resumeDescriptor() {
        return new AgentResumeDescriptor(
                AgentResumeDescriptor.SCHEMA_VERSION,
                "test-model",
                new AgentResumeDescriptor.SamplingSnapshot(0.2D, null, null, 100, false),
                AgentResumeDescriptor.RuntimeContextMode.SNAPSHOT,
                "runtime context",
                "skill context",
                null,
                List.of());
    }

    private static final class FailingObjectMapper extends ObjectMapper {
        private final String message;

        private FailingObjectMapper(String message) {
            this.message = message;
        }

        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            throw new JsonProcessingException(message) {
            };
        }
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();

        @Override
        public void createRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public void updateRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendStep(AgentStep step) {
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return List.of();
        }
    }

    private static final class MemoryAgentCheckpointRepository implements AgentCheckpointRepositoryPort {
        private final List<AgentCheckpoint> checkpoints = new ArrayList<>();

        @Override
        public void save(AgentCheckpoint checkpoint) {
            checkpoints.add(checkpoint);
        }

        @Override
        public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .findFirst();
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .toList();
        }
    }
}
