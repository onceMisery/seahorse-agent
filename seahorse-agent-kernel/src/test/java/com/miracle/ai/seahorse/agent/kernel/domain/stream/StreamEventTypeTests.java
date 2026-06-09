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

package com.miracle.ai.seahorse.agent.kernel.domain.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamEventTypeTests {

    @Test
    void shouldExposeConsumerWebAgentEventNames() {
        assertEquals("step_started", StreamEventType.STEP_STARTED.value());
        assertEquals("step_progress", StreamEventType.STEP_PROGRESS.value());
        assertEquals("step_finished", StreamEventType.STEP_FINISHED.value());
        assertEquals("tool_call_started", StreamEventType.TOOL_CALL_STARTED.value());
        assertEquals("tool_call_waiting_user", StreamEventType.TOOL_CALL_WAITING_USER.value());
        assertEquals("tool_call_finished", StreamEventType.TOOL_CALL_FINISHED.value());
        assertEquals("source_found", StreamEventType.SOURCE_FOUND.value());
        assertEquals("artifact_created", StreamEventType.ARTIFACT_CREATED.value());
        assertEquals("artifact_start", StreamEventType.ARTIFACT_START.value());
        assertEquals("artifact_content", StreamEventType.ARTIFACT_CONTENT.value());
        assertEquals("artifact_end", StreamEventType.ARTIFACT_END.value());
        assertEquals("recoverable_error", StreamEventType.RECOVERABLE_ERROR.value());
        assertEquals("agent.source", StreamEventType.AGENT_SOURCE.value());
        assertEquals("agent.artifact", StreamEventType.AGENT_ARTIFACT.value());
        assertEquals("agent.approval", StreamEventType.AGENT_APPROVAL.value());
        assertEquals("agent.quota", StreamEventType.AGENT_QUOTA.value());
        assertEquals("agent.memory", StreamEventType.AGENT_MEMORY.value());
    }
}
