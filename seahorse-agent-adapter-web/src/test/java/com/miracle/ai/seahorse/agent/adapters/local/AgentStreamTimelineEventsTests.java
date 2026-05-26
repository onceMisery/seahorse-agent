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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStreamTimelineEventsTests {

    @Test
    void runStartedShouldCreateFrontendTimelinePayload() {
        AgentTimelinePayload payload = AgentStreamTimelineEvents.runStarted("run-1");

        assertThat(payload.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("run-started-run-1");
            assertThat(item.title()).isEqualTo("Run started");
            assertThat(item.status()).isEqualTo(AgentTimelineStatus.RUNNING);
            assertThat(item.detail()).isEqualTo("Agent run run-1 started");
            assertThat(item.timestamp()).isNotBlank();
            assertThat(item.durationMs()).isNull();
        });
    }

    @Test
    void runStartedShouldCreateDetailedProtocolPayload() {
        RunStartedPayload payload = AgentStreamTimelineEvents.runStartedProtocol("run-1", "conversation-1", "task-1");

        assertThat(payload.runId()).isEqualTo("run-1");
        assertThat(payload.conversationId()).isEqualTo("conversation-1");
        assertThat(payload.taskId()).isEqualTo("task-1");
        assertThat(payload.title()).isEqualTo("Run started");
        assertThat(payload.status()).isEqualTo(AgentTimelineStatus.RUNNING);
        assertThat(payload.startedAt()).isNotBlank();
    }

    @Test
    void runSucceededShouldCreateFrontendTimelinePayloadWithDuration() {
        AgentTimelinePayload payload = AgentStreamTimelineEvents.runSucceeded("run-1", 1250L);

        assertThat(payload.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("run-succeeded-run-1");
            assertThat(item.title()).isEqualTo("Run completed");
            assertThat(item.status()).isEqualTo(AgentTimelineStatus.SUCCEEDED);
            assertThat(item.detail()).isEqualTo("Agent run run-1 completed");
            assertThat(item.timestamp()).isNotBlank();
            assertThat(item.durationMs()).isEqualTo(1250L);
        });
    }
}
