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

package com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationAppendResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationSchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

class MemoryAggregationServiceTests {

    private static final Instant BASE_TIME = Instant.parse("2026-05-21T10:00:00Z");

    @Test
    void shouldAppendTurnsWithoutFlushingBeforeThreshold() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingScheduler scheduler = new RecordingScheduler();
        DefaultMemoryAggregationService service = service(policy(3, 1_000), workflow, scheduler);

        MemoryAggregationAppendResult first = service.appendTurn(turn("task-1", "Remember I use Java", "Noted", 8));
        MemoryAggregationAppendResult second = service.appendTurn(turn("task-2", "I prefer concise answers", "Understood", 9));

        Assertions.assertFalse(first.flushed());
        Assertions.assertFalse(second.flushed());
        Assertions.assertEquals(0, workflow.commands.size());
        Assertions.assertEquals(2, second.state().turnCount());
        Assertions.assertEquals("conversation-1", scheduler.sessionId);
        Assertions.assertEquals(BASE_TIME.plusMillis(1_000), scheduler.runAt);
    }

    @Test
    void shouldForceFlushWhenMaxTurnsReachedAndSubmitContextBlock() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        DefaultMemoryAggregationService service = service(policy(2, 60_000), workflow, new RecordingScheduler());

        service.appendTurn(turn("task-1", "Remember I use Java", "Noted", 8));
        MemoryAggregationAppendResult result = service.appendTurn(
                turn("task-2", "I prefer concise answers", "Understood", 9));

        Assertions.assertTrue(result.flushed());
        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.ingestionResult().status());
        Assertions.assertEquals(1, workflow.commands.size());
        MemoryIngestionCommand command = workflow.commands.get(0);
        Assertions.assertEquals("memory-aggregation-flush", command.source());
        Assertions.assertTrue(command.operationId().startsWith("memory-aggregate-"));
        Assertions.assertEquals("conversation-1", command.writeRequest().conversationId());
        Assertions.assertEquals("user-1", command.writeRequest().userId());
        Assertions.assertEquals(ChatRole.USER, command.writeRequest().message().getRole());
        Assertions.assertTrue(command.writeRequest().message().getContent().startsWith("Remember I use Java"));
        Assertions.assertTrue(command.writeRequest().message().getContent().contains("I prefer concise answers"));
        Assertions.assertTrue(command.writeRequest().message().getContent().contains("Assistant: Understood"));
        Assertions.assertTrue(service.state("conversation-1", "default").isEmpty());
    }

    @Test
    void shouldFlushIdleSnapshotOnlyAfterIdleWindow() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        DefaultMemoryAggregationService service = service(policy(10, 1_000), workflow, new RecordingScheduler());

        service.appendTurn(turn("task-1", "Remember I use PostgreSQL", "Saved", 7));
        MemoryIngestionResult early = service.flushReady(
                "conversation-1", "default", MemoryFlushTrigger.IDLE_TIMEOUT, BASE_TIME.plusMillis(999));
        MemoryIngestionResult ready = service.flushReady(
                "conversation-1", "default", MemoryFlushTrigger.IDLE_TIMEOUT, BASE_TIME.plusMillis(1_000));

        Assertions.assertEquals(MemoryIngestionStatus.IGNORED, early.status());
        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, ready.status());
        Assertions.assertEquals(1, workflow.commands.size());
    }

    @Test
    void shouldScanAndFlushIdleBuffers() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        DefaultMemoryAggregationService service = service(policy(10, 1_000), workflow, new RecordingScheduler());

        service.appendTurn(turn("task-1", "Remember I use Redis", "Saved", 7));
        int flushed = service.flushIdleReady(BASE_TIME.plusMillis(1_001), 10);

        Assertions.assertEquals(1, flushed);
        Assertions.assertEquals(1, workflow.commands.size());
    }

    private DefaultMemoryAggregationService service(MemoryAggregationPolicy policy,
                                                    RecordingWorkflow workflow,
                                                    RecordingScheduler scheduler) {
        return new DefaultMemoryAggregationService(
                policy,
                new InMemoryMemoryAggregationBufferPort(policy),
                scheduler,
                workflow,
                Clock.fixed(BASE_TIME, ZoneOffset.UTC));
    }

    private MemoryAggregationPolicy policy(int maxTurns, long idleFlushMillis) {
        return new MemoryAggregationPolicy(true, idleFlushMillis, maxTurns, 1_000, 32, 86_400_000L, false);
    }

    private MemoryTurnEvent turn(String taskId, String userText, String assistantText, int estimatedTokens) {
        return new MemoryTurnEvent(
                "default",
                "user-1",
                "conversation-1",
                "conversation-1",
                taskId,
                taskId + "-assistant",
                userText,
                assistantText,
                BASE_TIME,
                estimatedTokens);
    }

    private static final class RecordingWorkflow implements MemoryIngestionWorkflowPort {

        private final List<MemoryIngestionCommand> commands = new ArrayList<>();

        @Override
        public MemoryIngestionResult ingest(MemoryIngestionCommand command) {
            commands.add(command);
            return MemoryIngestionResult.accepted(List.of("aggregate"));
        }
    }

    private static final class RecordingScheduler implements MemoryAggregationSchedulerPort {

        private String sessionId;
        private String tenantId;
        private Instant runAt;

        @Override
        public void scheduleIdleCheck(String sessionId, String tenantId, Instant runAt) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.runAt = runAt;
        }
    }
}
