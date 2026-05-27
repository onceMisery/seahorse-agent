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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchTaskProfile;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTaskQueuePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ResearchRunOrchestratorTests {

    private InMemoryTaskQueue taskQueue;
    private InMemoryEventBuffer eventBuffer;
    private List<ResearchStepType> executedSteps;
    private ResearchRunOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        taskQueue = new InMemoryTaskQueue();
        eventBuffer = new InMemoryEventBuffer();
        executedSteps = new ArrayList<>();

        List<ResearchStepHandler> handlers = new ArrayList<>();
        for (ResearchStepType type : ResearchStepType.values()) {
            handlers.add(new TrackingHandler(type, executedSteps));
        }
        orchestrator = new ResearchRunOrchestrator(taskQueue, eventBuffer, handlers);
    }

    @Test
    void fullSevenStepFlow() {
        orchestrator.startResearch("run-1", ResearchTaskProfile.defaultProfile(),
                "What is AI?", "tenant", "user");

        int iterations = 0;
        while (orchestrator.pollAndExecute() && iterations < 20) {
            iterations++;
        }

        assertEquals(7, executedSteps.size());
        assertEquals(ResearchStepType.PLAN, executedSteps.get(0));
        assertEquals(ResearchStepType.SEARCH, executedSteps.get(1));
        assertEquals(ResearchStepType.FETCH, executedSteps.get(2));
        assertEquals(ResearchStepType.EXTRACT_EVIDENCE, executedSteps.get(3));
        assertEquals(ResearchStepType.SYNTHESIZE, executedSteps.get(4));
        assertEquals(ResearchStepType.WRITE_REPORT, executedSteps.get(5));
        assertEquals(ResearchStepType.VERIFY_CITATIONS, executedSteps.get(6));
    }

    @Test
    void eventSeqMonotonicallyIncreasing() {
        orchestrator.startResearch("run-2", ResearchTaskProfile.defaultProfile(),
                "test", "t", "u");

        while (orchestrator.pollAndExecute()) { }

        List<StreamEventEnvelope> events = eventBuffer.allEvents("run-2");
        assertFalse(events.isEmpty());
        long prev = 0;
        for (StreamEventEnvelope e : events) {
            assertTrue(e.eventSeq() > prev, "seq " + e.eventSeq() + " not > " + prev);
            prev = e.eventSeq();
        }
    }

    @Test
    void finishEventEmittedAtEnd() {
        orchestrator.startResearch("run-3", ResearchTaskProfile.defaultProfile(),
                "q", "t", "u");

        while (orchestrator.pollAndExecute()) { }

        List<StreamEventEnvelope> events = eventBuffer.allEvents("run-3");
        StreamEventEnvelope last = events.get(events.size() - 1);
        assertEquals(StreamEventType.FINISH, last.eventType());
    }

    @Test
    void retryableExceptionSchedulesRetry() {
        List<ResearchStepHandler> handlers = new ArrayList<>();
        handlers.add(new ResearchStepHandler() {
            private int calls = 0;
            @Override
            public ResearchStepType stepType() { return ResearchStepType.PLAN; }
            @Override
            public void execute(DurableTask task, ResearchStepContext context) {
                if (calls++ == 0) throw new RetryableResearchException("transient");
                context.addSearchQuery("recovered");
            }
        });
        for (ResearchStepType type : ResearchStepType.values()) {
            if (type != ResearchStepType.PLAN) {
                handlers.add(new TrackingHandler(type, executedSteps));
            }
        }
        orchestrator = new ResearchRunOrchestrator(taskQueue, eventBuffer, handlers);
        orchestrator.startResearch("run-retry", ResearchTaskProfile.defaultProfile(),
                "q", "t", "u");

        orchestrator.pollAndExecute();
        assertEquals(1, taskQueue.retryCount);

        while (orchestrator.pollAndExecute()) { }
        assertTrue(executedSteps.contains(ResearchStepType.VERIFY_CITATIONS));
    }

    @Test
    void contextPersistedInPayloadAndRestoredOnRestart() {
        orchestrator.startResearch("run-persist", ResearchTaskProfile.defaultProfile(),
                "persistence test", "t1", "u1");

        orchestrator.pollAndExecute();
        orchestrator.pollAndExecute();

        DurableTask nextTask = taskQueue.peek();
        assertNotNull(nextTask);
        assertNotNull(nextTask.payloadJson());

        ResearchStepContext restored = ResearchStepContext.fromJson(nextTask.payloadJson());
        assertNotNull(restored);
        assertEquals("run-persist", restored.runId());
        assertEquals("persistence test", restored.query());
        assertEquals("t1", restored.tenantId());
        assertEquals("u1", restored.userId());
    }

    @Test
    void unknownStepTypeFailsGracefully() {
        DurableTask badTask = new DurableTask("t1", "run-x", "UNKNOWN_STEP", 0, Instant.now(), null, null);
        taskQueue.enqueue(badTask);

        boolean result = orchestrator.pollAndExecute();
        assertFalse(result);
        assertEquals(1, taskQueue.failCount);
    }

    // --- In-memory test doubles ---

    private static class TrackingHandler implements ResearchStepHandler {
        private final ResearchStepType type;
        private final List<ResearchStepType> tracker;

        TrackingHandler(ResearchStepType type, List<ResearchStepType> tracker) {
            this.type = type;
            this.tracker = tracker;
        }

        @Override
        public ResearchStepType stepType() { return type; }

        @Override
        public void execute(DurableTask task, ResearchStepContext context) {
            tracker.add(type);
        }
    }

    private static class InMemoryTaskQueue implements DurableTaskQueuePort {
        private final Queue<DurableTask> queue = new LinkedList<>();
        int retryCount = 0;
        int failCount = 0;

        @Override
        public void enqueue(DurableTask task) { queue.add(task); }

        @Override
        public Optional<DurableTask> claimNext(String workerId) {
            return Optional.ofNullable(queue.poll());
        }

        @Override
        public void ack(String taskId) { }

        @Override
        public void retry(String taskId, Instant retryAt, String reason) {
            retryCount++;
            queue.add(new DurableTask(taskId, findRunId(taskId), findStepType(taskId),
                    1, Instant.now(), null, findPayload(taskId)));
        }

        @Override
        public void fail(String taskId, String reason) { failCount++; }

        @Override
        public void cancel(String runId) { queue.removeIf(t -> t.runId().equals(runId)); }

        DurableTask peek() { return queue.peek(); }

        private String findRunId(String taskId) { return "run-retry"; }
        private String findStepType(String taskId) { return "PLAN"; }
        private String findPayload(String taskId) { return null; }
    }

    private static class InMemoryEventBuffer implements AgentRunEventBufferPort {
        private final List<StreamEventEnvelope> events = new ArrayList<>();
        private final AtomicLong seqTracker = new AtomicLong(0);

        @Override
        public void append(String runId, StreamEventEnvelope event) {
            events.add(event);
            long s = event.eventSeq();
            seqTracker.updateAndGet(cur -> Math.max(cur, s));
        }

        @Override
        public List<StreamEventEnvelope> getAfter(String runId, long afterSeq) {
            return events.stream()
                    .filter(e -> e.runId().equals(runId) && e.eventSeq() > afterSeq)
                    .toList();
        }

        @Override
        public Optional<Long> getLatestSeq(String runId) {
            return events.stream()
                    .filter(e -> e.runId().equals(runId))
                    .mapToLong(StreamEventEnvelope::eventSeq)
                    .max()
                    .stream().boxed().findFirst();
        }

        @Override
        public void expire(String runId) {
            events.removeIf(e -> e.runId().equals(runId));
        }

        List<StreamEventEnvelope> allEvents(String runId) {
            return events.stream().filter(e -> e.runId().equals(runId)).toList();
        }
    }
}
