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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout;

import java.time.Instant;
import java.util.Objects;

public record AgentVersionRollout(String rolloutId,
                                  String tenantId,
                                  String agentId,
                                  String versionId,
                                  int canaryPercent,
                                  AgentRolloutStatus status,
                                  AgentRolloutFailureCode failureCode,
                                  String gateReportId,
                                  String startedBy,
                                  Instant startedAt,
                                  Instant updatedAt,
                                  Instant finishedAt) {

    public AgentVersionRollout {
        rolloutId = requireText(rolloutId, "rolloutId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        versionId = requireText(versionId, "versionId must not be blank");
        requireCanaryPercent(canaryPercent);
        status = Objects.requireNonNullElse(status, AgentRolloutStatus.RUNNING);
        if (status == AgentRolloutStatus.FAILED && failureCode == null) {
            throw new IllegalArgumentException("failureCode is required for failed rollout");
        }
        if (status != AgentRolloutStatus.FAILED && failureCode != null) {
            throw new IllegalArgumentException("failureCode is only allowed for failed rollout");
        }
        gateReportId = trimToNull(gateReportId);
        startedBy = requireText(startedBy, "startedBy must not be blank");
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        updatedAt = Objects.requireNonNullElse(updatedAt, startedAt);
    }

    public AgentVersionRollout pause(Instant pausedAt) {
        ensureNotTerminal();
        return withStatus(AgentRolloutStatus.PAUSED, null, gateReportId, pausedAt, null);
    }

    public AgentVersionRollout promote(String gateReportId, Instant promotedAt) {
        ensureNotTerminal();
        return withStatus(
                AgentRolloutStatus.PROMOTED,
                null,
                requireText(gateReportId, "gateReportId must not be blank"),
                promotedAt,
                promotedAt);
    }

    public AgentVersionRollout rolledBack(Instant rolledBackAt) {
        ensureNotTerminal();
        return withStatus(AgentRolloutStatus.ROLLED_BACK, null, gateReportId, rolledBackAt, rolledBackAt);
    }

    public AgentVersionRollout fail(AgentRolloutFailureCode failureCode, Instant failedAt) {
        ensureNotTerminal();
        return withStatus(
                AgentRolloutStatus.FAILED,
                Objects.requireNonNull(failureCode, "failureCode must not be null"),
                gateReportId,
                failedAt,
                failedAt);
    }

    private AgentVersionRollout withStatus(AgentRolloutStatus nextStatus,
                                           AgentRolloutFailureCode nextFailureCode,
                                           String nextGateReportId,
                                           Instant nextUpdatedAt,
                                           Instant nextFinishedAt) {
        return new AgentVersionRollout(
                rolloutId,
                tenantId,
                agentId,
                versionId,
                canaryPercent,
                nextStatus,
                nextFailureCode,
                nextGateReportId,
                startedBy,
                startedAt,
                Objects.requireNonNull(nextUpdatedAt, "updatedAt must not be null"),
                nextFinishedAt);
    }

    private void ensureNotTerminal() {
        if (status.terminal()) {
            throw new IllegalStateException("terminal rollout cannot transition");
        }
    }

    private static void requireCanaryPercent(int canaryPercent) {
        if (canaryPercent < AgentRolloutLimits.MIN_CANARY_PERCENT
                || canaryPercent > AgentRolloutLimits.MAX_CANARY_PERCENT) {
            throw new IllegalArgumentException("canaryPercent is outside allowed range");
        }
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
