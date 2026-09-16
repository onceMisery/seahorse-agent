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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopCheckpointsTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldDoNothingWhenDisabled() {
        InMemoryCheckpoints repository = new InMemoryCheckpoints();
        AgentLoopCheckpoints checkpoints = new AgentLoopCheckpoints(repository, new ObjectMapper(), CLOCK, false);

        checkpoints.saveStepBoundary("run-1", 2, List.of(ChatMessage.user("hello")), Set.of());

        assertTrue(repository.saved.isEmpty(), "关闭时不应产生任何 IO");
        assertFalse(checkpoints.load("run-1").present());
    }

    @Test
    void shouldRoundTripMessageHistoryAndProgress() {
        InMemoryCheckpoints repository = new InMemoryCheckpoints();
        AgentLoopCheckpoints checkpoints = new AgentLoopCheckpoints(repository, new ObjectMapper(), CLOCK, true);
        List<ChatMessage> history = List.of(
                ChatMessage.user("问题"),
                ChatMessage.assistantToolCalls("", List.of(
                        AgentToolCall.of("call-1", "web_search", Map.of("q", "redis")))),
                ChatMessage.tool("call-1", "观察结果"));

        checkpoints.saveStepBoundary("run-1", 3, history, Set.of("exhausted-tool"));
        AgentLoopCheckpoints.ResumeState resumed = checkpoints.load("run-1");

        assertTrue(resumed.present());
        assertEquals(3, resumed.completedSteps());
        assertEquals(Set.of("exhausted-tool"), resumed.exhaustedToolIds());
        assertEquals(3, resumed.messages().size());
        assertEquals(ChatRole.USER, resumed.messages().get(0).getRole());
        assertEquals("问题", resumed.messages().get(0).getContent());
        assertEquals(ChatRole.ASSISTANT, resumed.messages().get(1).getRole());
        assertEquals(1, resumed.messages().get(1).getToolCalls().size());
        assertEquals("web_search", resumed.messages().get(1).getToolCalls().get(0).toolId());
        assertEquals(Map.of("q", "redis"), resumed.messages().get(1).getToolCalls().get(0).arguments());
        assertEquals(ChatRole.TOOL, resumed.messages().get(2).getRole());
        assertEquals("观察结果", resumed.messages().get(2).getContent());
        assertEquals("call-1", resumed.messages().get(2).getToolCallId());
    }

    @Test
    void shouldPersistStepBoundaryCheckpointsWithIncreasingSequence() {
        InMemoryCheckpoints repository = new InMemoryCheckpoints();
        AgentLoopCheckpoints checkpoints = new AgentLoopCheckpoints(repository, new ObjectMapper(), CLOCK, true);

        checkpoints.saveStepBoundary("run-1", 1, List.of(ChatMessage.user("a")), Set.of());
        checkpoints.saveStepBoundary("run-1", 2, List.of(ChatMessage.user("a"), ChatMessage.user("b")), Set.of());

        assertEquals(2, repository.saved.size());
        assertEquals(AgentCheckpointType.STEP_BOUNDARY, repository.saved.get(0).checkpointType());
        assertEquals(1L, repository.saved.get(0).sequenceNo());
        assertEquals(2L, repository.saved.get(1).sequenceNo());
        assertEquals(CLOCK.instant(), repository.saved.get(0).createdAt());
    }

    @Test
    void shouldStartFromScratchWhenThereIsNoCheckpoint() {
        AgentLoopCheckpoints checkpoints =
                new AgentLoopCheckpoints(new InMemoryCheckpoints(), new ObjectMapper(), CLOCK, true);

        assertFalse(checkpoints.load("missing-run").present());
    }

    @Test
    void shouldIgnoreCheckpointsOfOtherTypes() {
        InMemoryCheckpoints repository = new InMemoryCheckpoints();
        repository.seed(new AgentCheckpoint(
                "checkpoint_1", "run-1", null, 1L, AgentCheckpointType.WAITING_APPROVAL,
                "{\"resumeSchema\":1,\"completedSteps\":4}",
                "[{\"role\":\"USER\",\"content\":\"hello\",\"toolCalls\":[]}]",
                null, null, CLOCK.instant()));

        assertFalse(new AgentLoopCheckpoints(repository, new ObjectMapper(), CLOCK, true)
                .load("run-1").present(), "只有步骤边界检查点可用于续跑");
    }

    @Test
    void shouldStartFromScratchWhenThePayloadIsCorrupted() {
        InMemoryCheckpoints repository = new InMemoryCheckpoints();
        repository.seed(new AgentCheckpoint(
                "checkpoint_1", "run-1", null, 1L, AgentCheckpointType.STEP_BOUNDARY,
                "not-json-at-all",
                "] also not json [",
                null, null, CLOCK.instant()));

        assertFalse(new AgentLoopCheckpoints(repository, new ObjectMapper(), CLOCK, true)
                .load("run-1").present(), "损坏的检查点必须退化为从头开始，而不是把半截状态塞回循环");
    }

    @Test
    void shouldStartFromScratchWhenTheResumeSchemaIsUnknown() {
        InMemoryCheckpoints repository = new InMemoryCheckpoints();
        repository.seed(new AgentCheckpoint(
                "checkpoint_1", "run-1", null, 1L, AgentCheckpointType.STEP_BOUNDARY,
                "{\"resumeSchema\":99,\"completedSteps\":4}",
                "[{\"role\":\"USER\",\"content\":\"hello\",\"toolCalls\":[]}]",
                null, null, CLOCK.instant()));

        assertFalse(new AgentLoopCheckpoints(repository, new ObjectMapper(), CLOCK, true)
                .load("run-1").present());
    }

    @Test
    void shouldRedactCredentialLikeTextBeforePersisting() {
        InMemoryCheckpoints repository = new InMemoryCheckpoints();
        AgentLoopCheckpoints checkpoints = new AgentLoopCheckpoints(repository, new ObjectMapper(), CLOCK, true);

        checkpoints.saveStepBoundary("run-1", 1,
                List.of(ChatMessage.user("access_token=abcdefghijklmnop")), Set.of());

        assertFalse(repository.saved.get(0).messageHistoryJson().contains("abcdefghijklmnop"),
                "落盘的检查点与审批检查点一样要做凭证脱敏");
        assertTrue(repository.saved.get(0).messageHistoryJson().contains("[REDACTED]"));
    }

    @Test
    void shouldNotFailTheLoopWhenSavingThrows() {
        AgentCheckpointRepositoryPort exploding = new AgentCheckpointRepositoryPort() {
            @Override
            public void save(AgentCheckpoint checkpoint) {
                throw new IllegalStateException("checkpoint store down");
            }

            @Override
            public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
                throw new IllegalStateException("checkpoint store down");
            }

            @Override
            public List<AgentCheckpoint> listByRunId(String runId) {
                throw new IllegalStateException("checkpoint store down");
            }
        };
        AgentLoopCheckpoints checkpoints = new AgentLoopCheckpoints(exploding, new ObjectMapper(), CLOCK, true);

        checkpoints.saveStepBoundary("run-1", 1, List.of(ChatMessage.user("a")), Set.of());

        assertFalse(checkpoints.load("run-1").present(), "检查点是加速器，它挂掉时循环必须照常从头跑");
    }

    private static final class InMemoryCheckpoints implements AgentCheckpointRepositoryPort {

        private final List<AgentCheckpoint> saved = new ArrayList<>();

        private void seed(AgentCheckpoint checkpoint) {
            saved.add(checkpoint);
        }

        @Override
        public void save(AgentCheckpoint checkpoint) {
            saved.add(checkpoint);
        }

        @Override
        public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
            return saved.stream()
                    .filter(item -> item.runId().equals(runId))
                    .reduce((first, second) -> second);
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return List.copyOf(saved);
        }
    }
}
