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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryAgentRunStepRecorderTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldRedactModelTurnStepBeforePersistence() {
        RecordingAgentRunRepository repository = new RecordingAgentRunRepository();
        RepositoryAgentRunStepRecorder recorder = new RepositoryAgentRunStepRecorder(repository, FIXED_CLOCK);

        recorder.recordModelTurn(
                "run-1",
                "{\"messages\":[{\"content\":\"api_key=secret-api-key-value\"}],\"safe\":\"ok\"}",
                "{\"authorization\":\"Bearer output-secret-123456\",\"safe\":\"ok\"}",
                new IllegalStateException("failed with session_token=session-secret-value"));

        AgentStep step = repository.steps.get(0);
        assertEquals(AgentStepType.MODEL_TURN, step.stepType());
        assertEquals(AgentStepStatus.FAILED, step.status());
        assertEquals("{\"messages\":[{\"content\":\"[REDACTED]\"}],\"safe\":\"ok\"}", step.inputJson());
        assertEquals("{\"authorization\":\"[REDACTED]\",\"safe\":\"ok\"}", step.outputJson());
        assertEquals("failed with [REDACTED]", step.errorMessage());
        assertFalse(step.inputJson().contains("secret-api-key-value"));
        assertFalse(step.outputJson().contains("output-secret-123456"));
        assertFalse(step.errorMessage().contains("session-secret-value"));
    }

    @Test
    void shouldRedactToolCallStepBeforePersistence() {
        RecordingAgentRunRepository repository = new RecordingAgentRunRepository();
        RepositoryAgentRunStepRecorder recorder = new RepositoryAgentRunStepRecorder(repository, FIXED_CLOCK);

        recorder.recordToolCall(
                "run-1",
                AgentToolCall.of("call-1", "tool-1", Map.of(
                        "apiKey", "secret-api-key-value",
                        "query", "safe")),
                AgentObservation.failed("call-1", "tool failed with Authorization: Bearer tool-secret-123456"));

        AgentStep step = repository.steps.get(0);
        assertEquals(AgentStepType.TOOL_CALL, step.stepType());
        assertEquals(AgentStepStatus.FAILED, step.status());
        assertTrue(step.inputJson().contains("\"toolCallId\":\"call-1\""), step.inputJson());
        assertTrue(step.inputJson().contains("\"toolId\":\"tool-1\""), step.inputJson());
        assertTrue(step.inputJson().contains("\"apiKey\":\"[REDACTED]\""), step.inputJson());
        assertTrue(step.outputJson().contains("\"error\":\"tool failed with [REDACTED]\""), step.outputJson());
        assertEquals("tool failed with [REDACTED]", step.errorMessage());
        assertFalse(step.inputJson().contains("secret-api-key-value"));
        assertFalse(step.outputJson().contains("tool-secret-123456"));
        assertFalse(step.errorMessage().contains("tool-secret-123456"));
    }

    @Test
    void shouldRedactSerializationFailureFallbackBeforePersistence() {
        RecordingAgentRunRepository repository = new RecordingAgentRunRepository();
        RepositoryAgentRunStepRecorder recorder = new RepositoryAgentRunStepRecorder(
                repository,
                FIXED_CLOCK,
                new FailingObjectMapper(
                        "json failed Authorization: Bearer serializer-secret-123456 api_key=plain-serializer-secret"));

        recorder.recordToolCall(
                "run-1",
                AgentToolCall.of("call-1", "tool-1", Map.of("query", "safe")),
                AgentObservation.failed("call-1", "tool failed"));

        AgentStep step = repository.steps.get(0);
        assertTrue(step.inputJson().contains("[REDACTED]"), step.inputJson());
        assertTrue(step.outputJson().contains("[REDACTED]"), step.outputJson());
        assertFalse(step.inputJson().contains("serializer-secret-123456"));
        assertFalse(step.outputJson().contains("plain-serializer-secret"));
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

    private static final class RecordingAgentRunRepository implements AgentRunRepositoryPort {
        private final List<AgentStep> steps = new ArrayList<>();

        @Override
        public void createRun(AgentRun run) {
        }

        @Override
        public void updateRun(AgentRun run) {
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.empty();
        }

        @Override
        public void appendStep(AgentStep step) {
            steps.add(step);
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return steps.stream()
                    .filter(step -> runId.equals(step.runId()))
                    .toList();
        }
    }
}
