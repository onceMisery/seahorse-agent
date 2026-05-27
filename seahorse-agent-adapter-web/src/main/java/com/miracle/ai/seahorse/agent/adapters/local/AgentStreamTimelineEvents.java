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

package com.miracle.ai.seahorse.agent.adapters.local;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

final class AgentStreamTimelineEvents {

    private static final String RUN_STARTED_ID_PREFIX = "run-started-";
    private static final String RUN_SUCCEEDED_ID_PREFIX = "run-succeeded-";
    private static final String RUN_STARTED_TITLE = "Run started";
    private static final String RUN_SUCCEEDED_TITLE = "Run completed";

    private static final Clock CLOCK = Clock.systemUTC();

    private AgentStreamTimelineEvents() {
    }

    static AgentTimelinePayload runStarted(String runId) {
        String normalizedRunId = requireRunId(runId);
        return payload(new AgentTimelineItem(
                RUN_STARTED_ID_PREFIX + normalizedRunId,
                RUN_STARTED_TITLE,
                AgentTimelineStatus.RUNNING,
                "Agent run " + normalizedRunId + " started",
                now(),
                null));
    }

    static RunStartedPayload runStartedProtocol(String runId, String conversationId, String taskId) {
        String normalizedRunId = requireRunId(runId);
        return new RunStartedPayload(
                normalizedRunId,
                trimToNull(conversationId),
                trimToNull(taskId),
                RUN_STARTED_TITLE,
                AgentTimelineStatus.RUNNING,
                now());
    }

    static AgentTimelinePayload runSucceeded(String runId, long durationMs) {
        String normalizedRunId = requireRunId(runId);
        return payload(new AgentTimelineItem(
                RUN_SUCCEEDED_ID_PREFIX + normalizedRunId,
                RUN_SUCCEEDED_TITLE,
                AgentTimelineStatus.SUCCEEDED,
                "Agent run " + normalizedRunId + " completed",
                now(),
                Math.max(0L, durationMs)));
    }

    /**
     * 产物增量内容事件 payload。
     */
    static ArtifactContentPayload artifactContent(String artifactId, String delta, long offset) {
        return new ArtifactContentPayload(
                Objects.requireNonNull(artifactId, "artifactId must not be null"),
                Objects.requireNonNullElse(delta, ""),
                offset);
    }

    /**
     * 产物生成完成事件 payload。
     */
    static ArtifactCompletePayload artifactComplete(String artifactId, String title,
                                                     String mimeType, long size, String scanStatus) {
        return new ArtifactCompletePayload(
                Objects.requireNonNull(artifactId, "artifactId must not be null"),
                title, mimeType, size,
                Objects.requireNonNullElse(scanStatus, "PENDING"));
    }

    private static AgentTimelinePayload payload(AgentTimelineItem item) {
        return new AgentTimelinePayload(List.of(item));
    }

    private static String requireRunId(String runId) {
        String normalizedRunId = Objects.requireNonNull(runId, "runId must not be null").trim();
        if (normalizedRunId.isEmpty()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        return normalizedRunId;
    }

    private static String now() {
        return Instant.now(CLOCK).toString();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
