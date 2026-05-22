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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class RepositoryAgentRunStepRecorder implements AgentRunStepRecorder {

    private final AgentRunRepositoryPort runRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public RepositoryAgentRunStepRecorder(AgentRunRepositoryPort runRepository, Clock clock) {
        this(runRepository, clock, new ObjectMapper());
    }

    public RepositoryAgentRunStepRecorder(AgentRunRepositoryPort runRepository,
                                          Clock clock,
                                          ObjectMapper objectMapper) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public void recordModelTurn(String runId, String inputJson, String outputJson, Throwable error) {
        if (isBlank(runId)) {
            return;
        }
        Instant now = clock.instant();
        runRepository.appendStep(new AgentStep(
                nextStepId(),
                runId,
                nextStepNo(runId),
                AgentStepType.MODEL_TURN,
                error == null ? AgentStepStatus.SUCCEEDED : AgentStepStatus.FAILED,
                inputJson,
                outputJson,
                error == null ? null : AgentRuntimeConstants.AGENT_STEP_FAILURE_CODE,
                error == null ? null : errorMessage(error),
                now,
                now));
    }

    @Override
    public void recordToolCall(String runId, AgentToolCall toolCall, AgentObservation observation) {
        if (isBlank(runId) || toolCall == null) {
            return;
        }
        boolean succeeded = observation != null && observation.success();
        Instant now = clock.instant();
        runRepository.appendStep(new AgentStep(
                nextStepId(),
                runId,
                nextStepNo(runId),
                AgentStepType.TOOL_CALL,
                succeeded ? AgentStepStatus.SUCCEEDED : AgentStepStatus.FAILED,
                toolCallJson(toolCall),
                observationJson(observation),
                succeeded ? null : AgentRuntimeConstants.AGENT_STEP_FAILURE_CODE,
                succeeded ? null : observationError(observation),
                now,
                now));
    }

    private int nextStepNo(String runId) {
        return runRepository.listSteps(runId).size() + 1;
    }

    private String nextStepId() {
        return AgentRuntimeConstants.AGENT_STEP_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private String toolCallJson(AgentToolCall toolCall) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", toolCall.id());
        payload.put("toolId", toolCall.toolId());
        payload.put("arguments", toolCall.arguments());
        return toJson(payload);
    }

    private String observationJson(AgentObservation observation) {
        if (observation == null) {
            return "{\"success\":false}";
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", observation.toolCallId());
        payload.put("success", observation.success());
        payload.put("content", observation.content());
        payload.put("error", observation.error());
        return toJson(payload);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + escape(ex.getMessage()) + "\"}";
        }
    }

    private String observationError(AgentObservation observation) {
        if (observation == null) {
            return "Tool observation missing";
        }
        return observation.error();
    }

    private String errorMessage(Throwable error) {
        String message = error.getMessage();
        return isBlank(message) ? error.getClass().getName() : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
