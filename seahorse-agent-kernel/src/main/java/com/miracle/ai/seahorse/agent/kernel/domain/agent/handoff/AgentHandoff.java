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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;

import java.time.Instant;
import java.util.Objects;

public record AgentHandoff(String handoffId,
                           String tenantId,
                           String parentRunId,
                           String childRunId,
                           String sourceAgentId,
                           String targetAgentId,
                           AgentHandoffStatus status,
                           AgentHandoffFailureCode failureCode,
                           String handoffReason,
                           String inputSummaryJson,
                           String contextSummaryJson,
                           Instant createdAt,
                           Instant updatedAt,
                           Instant finishedAt) {

    public static final String EMPTY_JSON_OBJECT = "{}";

    public AgentHandoff {
        handoffId = requireText(handoffId, "handoffId must not be blank");
        tenantId = defaultText(tenantId, AgentDefinition.DEFAULT_TENANT_ID);
        parentRunId = requireText(parentRunId, "parentRunId must not be blank");
        childRunId = trimToNull(childRunId);
        sourceAgentId = requireText(sourceAgentId, "sourceAgentId must not be blank");
        targetAgentId = requireText(targetAgentId, "targetAgentId must not be blank");
        status = Objects.requireNonNullElse(status, AgentHandoffStatus.CREATED);
        if (status == AgentHandoffStatus.RUNNING && childRunId == null) {
            throw new IllegalArgumentException("running handoff requires childRunId");
        }
        failureCode = normalizeFailure(status, failureCode);
        handoffReason = trimToNull(handoffReason);
        inputSummaryJson = defaultJson(inputSummaryJson);
        contextSummaryJson = defaultJson(contextSummaryJson);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public AgentHandoff running(String nextChildRunId, Instant now) {
        if (status.isTerminal()) {
            return this;
        }
        return withStatus(AgentHandoffStatus.RUNNING, null, requireText(nextChildRunId, "childRunId must not be blank"),
                now, null);
    }

    public AgentHandoff succeed(Instant now) {
        if (status.isTerminal()) {
            return this;
        }
        return withStatus(AgentHandoffStatus.SUCCEEDED, null, childRunId, now, now);
    }

    public AgentHandoff cancel(Instant now) {
        if (status.isTerminal()) {
            return this;
        }
        return withStatus(AgentHandoffStatus.CANCELLED, null, childRunId, now, now);
    }

    public AgentHandoff fail(AgentHandoffFailureCode nextFailureCode, Instant now) {
        if (status.isTerminal()) {
            return this;
        }
        return withStatus(AgentHandoffStatus.FAILED,
                Objects.requireNonNullElse(nextFailureCode, AgentHandoffFailureCode.CHILD_RUN_FAILED),
                childRunId,
                now,
                now);
    }

    private AgentHandoff withStatus(AgentHandoffStatus nextStatus,
                                    AgentHandoffFailureCode nextFailureCode,
                                    String nextChildRunId,
                                    Instant updatedAt,
                                    Instant finishedAt) {
        return new AgentHandoff(
                handoffId,
                tenantId,
                parentRunId,
                nextChildRunId,
                sourceAgentId,
                targetAgentId,
                nextStatus,
                nextFailureCode,
                handoffReason,
                inputSummaryJson,
                contextSummaryJson,
                createdAt,
                Objects.requireNonNull(updatedAt, "updatedAt must not be null"),
                finishedAt);
    }

    private static AgentHandoffFailureCode normalizeFailure(AgentHandoffStatus status,
                                                            AgentHandoffFailureCode failureCode) {
        if (status == AgentHandoffStatus.FAILED) {
            return Objects.requireNonNullElse(failureCode, AgentHandoffFailureCode.CHILD_RUN_FAILED);
        }
        return null;
    }

    private static String defaultJson(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? EMPTY_JSON_OBJECT : trimmed;
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
