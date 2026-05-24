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

package com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionFragment;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummarizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCompactionServiceObservationTests {

    @Test
    void shouldEmitGroupSuccessAndRunSuccessWhenCompactionCompletes() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        StubCompactionPort compactionPort = new StubCompactionPort(List.of(
                candidate("group-1", "rule-similarity", 3)));
        MemoryCompactionService service = new MemoryCompactionService(
                compactionPort,
                new RecordingLongTermMemoryPort(),
                new RecordingOutboxPort(),
                MemoryCompactionSummarizerPort.noop(),
                MemoryCompactionOptions.defaults(),
                observationPort);

        MemoryCompactionResult result = service.run("test");

        assertThat(result.compactedGroupCount()).isEqualTo(1);
        List<ObservationEvent> groupEvents = observationPort.filter(MemoryCompactionService.OBSERVATION_GROUP_EVENT);
        assertThat(groupEvents).singleElement().satisfies(event -> assertThat(event.attributes())
                .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_OUTCOME,
                        MemoryCompactionService.OBSERVATION_OUTCOME_SUCCESS)
                .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_STRATEGY, "rule-similarity"));
        List<ObservationEvent> runEvents = observationPort.filter(MemoryCompactionService.OBSERVATION_RUN_EVENT);
        assertThat(runEvents).singleElement().satisfies(event -> assertThat(event.attributes())
                .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_OUTCOME,
                        MemoryCompactionService.OBSERVATION_OUTCOME_SUCCESS));
    }

    @Test
    void shouldEmitGroupSkippedWhenFragmentCountBelowMinGroupSize() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        StubCompactionPort compactionPort = new StubCompactionPort(List.of(
                candidate("group-small", "rule-similarity", 2)));
        MemoryCompactionService service = new MemoryCompactionService(
                compactionPort,
                new RecordingLongTermMemoryPort(),
                new RecordingOutboxPort(),
                MemoryCompactionSummarizerPort.noop(),
                MemoryCompactionOptions.defaults(),
                observationPort);

        service.run("test");

        assertThat(observationPort.filter(MemoryCompactionService.OBSERVATION_GROUP_EVENT))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_OUTCOME,
                                MemoryCompactionService.OBSERVATION_OUTCOME_SKIPPED));
        assertThat(observationPort.filter(MemoryCompactionService.OBSERVATION_RUN_EVENT))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_OUTCOME,
                                MemoryCompactionService.OBSERVATION_OUTCOME_EMPTY));
    }

    @Test
    void shouldEmitErrorOutcomeWhenSaveFails() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        StubCompactionPort compactionPort = new StubCompactionPort(List.of(
                candidate("group-bad", "rule-similarity", 3)));
        FailingLongTermMemoryPort longTermMemoryPort = new FailingLongTermMemoryPort();
        MemoryCompactionService service = new MemoryCompactionService(
                compactionPort,
                longTermMemoryPort,
                new RecordingOutboxPort(),
                MemoryCompactionSummarizerPort.noop(),
                MemoryCompactionOptions.defaults(),
                observationPort);

        MemoryCompactionResult result = service.run("test");

        assertThat(result.errors()).isNotEmpty();
        assertThat(observationPort.filter(MemoryCompactionService.OBSERVATION_GROUP_EVENT))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_OUTCOME,
                                MemoryCompactionService.OBSERVATION_OUTCOME_ERROR));
        assertThat(observationPort.filter(MemoryCompactionService.OBSERVATION_RUN_EVENT))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_OUTCOME,
                                MemoryCompactionService.OBSERVATION_OUTCOME_ERROR));
    }

    @Test
    void shouldEmitEmptyRunOutcomeWhenNoCandidatesAreReturned() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        MemoryCompactionService service = new MemoryCompactionService(
                new StubCompactionPort(List.of()),
                new RecordingLongTermMemoryPort(),
                new RecordingOutboxPort(),
                MemoryCompactionSummarizerPort.noop(),
                MemoryCompactionOptions.defaults(),
                observationPort);

        service.run("test");

        assertThat(observationPort.filter(MemoryCompactionService.OBSERVATION_GROUP_EVENT)).isEmpty();
        assertThat(observationPort.filter(MemoryCompactionService.OBSERVATION_RUN_EVENT))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry(MemoryCompactionService.OBSERVATION_ATTR_OUTCOME,
                                MemoryCompactionService.OBSERVATION_OUTCOME_EMPTY));
    }

    @Test
    void shouldNormalizeBlankStrategyToUnknownTag() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        MemoryCompactionCandidate candidate = new MemoryCompactionCandidate(
                "user-1", "tenant-a", "group-blank", "  ",
                List.of(fragment("mem-1"), fragment("mem-2"), fragment("mem-3")));
        MemoryCompactionService service = new MemoryCompactionService(
                new StubCompactionPort(List.of(candidate)),
                new RecordingLongTermMemoryPort(),
                new RecordingOutboxPort(),
                MemoryCompactionSummarizerPort.noop(),
                MemoryCompactionOptions.defaults(),
                observationPort);

        service.run("test");

        assertThat(observationPort.filter(MemoryCompactionService.OBSERVATION_GROUP_EVENT))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsKey(MemoryCompactionService.OBSERVATION_ATTR_STRATEGY));
    }

    private static MemoryCompactionCandidate candidate(String groupKey, String strategy, int fragmentCount) {
        List<MemoryCompactionFragment> fragments = new ArrayList<>(fragmentCount);
        for (int index = 0; index < fragmentCount; index++) {
            fragments.add(fragment("mem-" + groupKey + "-" + index));
        }
        return new MemoryCompactionCandidate("user-1", "tenant-a", groupKey, strategy, fragments);
    }

    private static MemoryCompactionFragment fragment(String memoryId) {
        return new MemoryCompactionFragment(memoryId, "long_term", "FACT", "content-" + memoryId,
                Map.of(), Instant.parse("2026-05-24T08:00:00Z"));
    }

    private static final class StubCompactionPort implements MemoryCompactionPort {

        private final List<MemoryCompactionCandidate> candidates;

        StubCompactionPort(List<MemoryCompactionCandidate> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public List<MemoryCompactionCandidate> scanCandidates(int scanLimit) {
            return candidates;
        }

        @Override
        public List<MemoryCompactionCandidate> scanCandidates(int scanLimit, int minGroupSize) {
            return candidates;
        }

        @Override
        public int markCompacted(MemoryCompactionCandidate candidate, String masterId, Instant compactedAt) {
            return candidate.fragments().size();
        }
    }

    private static final class RecordingLongTermMemoryPort implements LongTermMemoryPort {

        private final AtomicInteger saveCount = new AtomicInteger();

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return List.of();
        }

        @Override
        public void save(MemoryRecord record) {
            saveCount.incrementAndGet();
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static final class FailingLongTermMemoryPort implements LongTermMemoryPort {

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return List.of();
        }

        @Override
        public void save(MemoryRecord record) {
            throw new IllegalStateException("save failed");
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static final class RecordingOutboxPort implements MemoryOutboxPort {

        private final List<MemoryOutboxTask> tasks = new ArrayList<>();

        @Override
        public void enqueue(MemoryOutboxTask task) {
            tasks.add(task);
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

        List<ObservationEvent> filter(String name) {
            return events.stream().filter(event -> name.equals(event.name())).toList();
        }
    }
}
