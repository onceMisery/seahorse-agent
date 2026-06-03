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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        String content = command.writeRequest().message().getContent();
        Assertions.assertTrue(content.startsWith("MEMORY_CONTEXT_BLOCK: v1"));
        Assertions.assertTrue(content.contains("snapshot_id:"));
        Assertions.assertTrue(content.contains("turn_count: 2"));
        Assertions.assertTrue(content.contains("[turns]"));
        Assertions.assertTrue(content.contains("user: Remember I use Java"));
        Assertions.assertTrue(content.contains("user: I prefer concise answers"));
        Assertions.assertTrue(content.contains("assistant: Understood"));
        Assertions.assertTrue(content.contains("[source_spans]"));
        Assertions.assertTrue(content.contains("span_2: task-2 -> task-2-assistant"));
        Assertions.assertTrue(service.state("user-1", "conversation-1", "default").isEmpty());
    }

    @Test
    void shouldFlushIdleSnapshotOnlyAfterIdleWindow() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        DefaultMemoryAggregationService service = service(policy(10, 1_000), workflow, new RecordingScheduler());

        service.appendTurn(turn("task-1", "Remember I use PostgreSQL", "Saved", 7));
        MemoryIngestionResult early = service.flushReady(
                "user-1", "conversation-1", "default", MemoryFlushTrigger.IDLE_TIMEOUT, BASE_TIME.plusMillis(999));
        MemoryIngestionResult ready = service.flushReady(
                "user-1", "conversation-1", "default", MemoryFlushTrigger.IDLE_TIMEOUT, BASE_TIME.plusMillis(1_000));

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

    @Test
    void shouldFlushExistingBufferBeforeAppendingExplicitTopicShiftTurn() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        DefaultMemoryAggregationService service = service(topicShiftPolicy(), workflow, new RecordingScheduler());

        service.appendTurn(turn("task-1", "Remember I use Java", "Noted", 8));
        MemoryAggregationAppendResult result = service.appendTurn(
                turn("task-2", "New topic: help me plan a vacation", "Sure", 9));

        Assertions.assertTrue(result.flushed());
        Assertions.assertEquals(MemoryFlushTrigger.TOPIC_SHIFT, result.snapshot().trigger());
        Assertions.assertEquals(1, workflow.commands.size());
        String flushedContent = workflow.commands.get(0).writeRequest().message().getContent();
        Assertions.assertTrue(flushedContent.contains("user: Remember I use Java"));
        Assertions.assertFalse(flushedContent.contains("user: New topic: help me plan a vacation"));
        assertThat(service.state("user-1", "conversation-1", "default"))
                .hasValueSatisfying(state -> Assertions.assertEquals(1, state.turnCount()));
    }

    @Test
    void shouldRecordTraceEventsForAggregationAndFlushLifecycle() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        DefaultMemoryAggregationService service = service(policy(2, 60_000), workflow, new RecordingScheduler(),
                traceRecorder);

        service.appendTurn(turn("task-1", "Remember I use Java", "Noted", 8));
        service.appendTurn(turn("task-2", "I prefer concise answers", "Understood", 9));

        assertThat(traceRecorder.events).isNotEmpty();
        assertThat(traceRecorder.events)
                .anyMatch(event -> "memory-aggregation".equals(event.component())
                        && "append-turn".equals(event.eventType()))
                .anyMatch(event -> "flush-ready".equals(event.eventType())
                        && MemoryTraceEvent.STATUS_SUCCESS.equals(event.status()));
    }

    @Test
    void shouldRecordAggregationTraceWithTenantUserConversationAndSessionContext() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        DefaultMemoryAggregationService service = service(policy(2, 60_000), workflow, new RecordingScheduler(),
                traceRecorder);

        service.appendTurn(turn("task-1", "Remember I use Java", "Noted", 8));
        service.appendTurn(turn("task-2", "I prefer concise answers", "Understood", 9));

        assertThat(traceRecorder.events)
                .filteredOn(event -> "memory-aggregation".equals(event.component()))
                .allSatisfy(event -> {
                    assertThat(event.tenantId()).isEqualTo("default");
                    assertThat(event.userId()).isEqualTo("user-1");
                    assertThat(event.conversationId()).isEqualTo("conversation-1");
                    assertThat(event.sessionId()).isEqualTo("conversation-1");
                });
    }

    @Test
    void shouldRecordFlushTraceExplanationWithoutRawTurnContent() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        DefaultMemoryAggregationService service = service(policy(2, 60_000), workflow, new RecordingScheduler(),
                traceRecorder);
        String rawFirstTurn = "Remember I use Java";
        String rawSecondTurn = "I prefer concise answers";

        service.appendTurn(turn("task-1", rawFirstTurn, "Noted", 8));
        service.appendTurn(turn("task-2", rawSecondTurn, "Understood", 9));

        assertThat(traceRecorder.events)
                .filteredOn(event -> "flush-ready".equals(event.eventType())
                        && MemoryTraceEvent.STATUS_SUCCESS.equals(event.status()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.details())
                            .containsEntry("trigger", MemoryFlushTrigger.FORCE_TURNS.name())
                            .containsEntry("turnCount", 2)
                            .containsEntry("totalTokens", 17)
                            .containsEntry("sourceSpanCount", 2)
                            .containsEntry("sourceUserMessageIds", List.of("task-1", "task-2"))
                            .containsEntry("sourceAssistantMessageIds",
                                    List.of("task-1-assistant", "task-2-assistant"))
                            .containsEntry("from", BASE_TIME.toString())
                            .containsEntry("to", BASE_TIME.toString())
                            .containsEntry("windowDurationMillis", 0L);
                    assertThat(event.details().toString())
                            .doesNotContain(rawFirstTurn)
                            .doesNotContain(rawSecondTurn);
                });
    }

    @Test
    void shouldEmitFlushObservationCounterTaggedWithTriggerAndStatus() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        DefaultMemoryAggregationService service = service(policy(2, 60_000),
                workflow,
                new RecordingScheduler(),
                MemoryTraceRecorder.noop(),
                observationPort);

        service.appendTurn(turn("task-1", "Remember I use Java", "Noted", 8));
        service.appendTurn(turn("task-2", "I prefer concise answers", "Understood", 9));

        assertThat(observationPort.events)
                .as("flush observation events should be emitted on force-flush")
                .extracting(ObservationEvent::name)
                .containsOnly(DefaultMemoryAggregationService.OBSERVATION_FLUSH_EVENT);
        assertThat(observationPort.events).hasSize(1);
        ObservationEvent flushEvent = observationPort.events.get(0);
        assertThat(flushEvent.attributes())
                .containsEntry(DefaultMemoryAggregationService.OBSERVATION_ATTR_TRIGGER,
                        MemoryFlushTrigger.FORCE_TURNS.name())
                .containsEntry(DefaultMemoryAggregationService.OBSERVATION_ATTR_STATUS,
                        MemoryTraceEvent.STATUS_SUCCESS);
        assertThat(flushEvent.amount()).isEqualTo(ObservationEvent.DEFAULT_AMOUNT);
    }

    @Test
    void shouldNotEmitFlushObservationForNonFlushTraceEvents() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        DefaultMemoryAggregationService service = service(policy(3, 60_000),
                workflow,
                new RecordingScheduler(),
                MemoryTraceRecorder.noop(),
                observationPort);

        service.appendTurn(turn("task-1", "Remember I use Java", "Noted", 8));

        assertThat(observationPort.events)
                .as("append-turn alone should not emit flush counter")
                .isEmpty();
    }

    private DefaultMemoryAggregationService service(MemoryAggregationPolicy policy,
                                                    RecordingWorkflow workflow,
                                                    RecordingScheduler scheduler) {
        return service(policy, workflow, scheduler, MemoryTraceRecorder.noop());
    }

    private DefaultMemoryAggregationService service(MemoryAggregationPolicy policy,
                                                    RecordingWorkflow workflow,
                                                    RecordingScheduler scheduler,
                                                    MemoryTraceRecorder traceRecorder) {
        return service(policy, workflow, scheduler, traceRecorder, ObservationPort.noop());
    }

    private DefaultMemoryAggregationService service(MemoryAggregationPolicy policy,
                                                    RecordingWorkflow workflow,
                                                    RecordingScheduler scheduler,
                                                    MemoryTraceRecorder traceRecorder,
                                                    ObservationPort observationPort) {
        return new DefaultMemoryAggregationService(
                policy,
                new InMemoryMemoryAggregationBufferPort(policy),
                scheduler,
                workflow,
                traceRecorder,
                new ExplicitCueMemoryAggregationTopicShiftDetector(),
                observationPort,
                Clock.fixed(BASE_TIME, ZoneOffset.UTC));
    }

    private MemoryAggregationPolicy policy(int maxTurns, long idleFlushMillis) {
        return new MemoryAggregationPolicy(true, idleFlushMillis, maxTurns, 1_000, 32, 86_400_000L, false, false);
    }

    private MemoryAggregationPolicy topicShiftPolicy() {
        return new MemoryAggregationPolicy(true, 60_000, 10, 1_000, 32, 86_400_000L, false, true);
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

    private static final class RecordingTraceRecorder implements MemoryTraceRecorder {

        private final List<MemoryTraceEvent> events = new ArrayList<>();

        @Override
        public void record(MemoryTraceEvent event) {
            events.add(event);
        }

        @Override
        public List<MemoryTraceEvent> listRecent(int limit) {
            return List.copyOf(events);
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }
}
