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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCompactionServiceTests {

    @Test
    void shouldCreateMasterMemoryMarkFragmentsCompactedAndEnqueueDerivedIndexTasks() {
        RecordingCompactionPort compactionPort = new RecordingCompactionPort();
        RecordingLongTermMemoryPort longTermPort = new RecordingLongTermMemoryPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        compactionPort.candidates.add(new MemoryCompactionCandidate(
                "user-1",
                "default",
                "semanticKey:project.alpha",
                "semanticKey",
                List.of(
                        new MemoryCompactionFragment(
                                "stm-1",
                                "short_term",
                                "PROJECT_FACT",
                                "Alpha project uses Spring.",
                                Map.of("semanticKey", "project.alpha"),
                                Instant.EPOCH),
                        new MemoryCompactionFragment(
                                "sem-1",
                                "semantic",
                                "PROJECT_FACT",
                                "Alpha project target is May.",
                                Map.of("semanticKey", "project.alpha"),
                                Instant.EPOCH))));
        MemoryCompactionService service = new MemoryCompactionService(
                compactionPort,
                longTermPort,
                outboxPort,
                new MemoryCompactionOptions(10, 2, true, true, true, "text-embedding-test"));

        MemoryCompactionResult result = service.run("manual-maintenance");

        assertThat(result.scannedGroupCount()).isEqualTo(1);
        assertThat(result.compactedGroupCount()).isEqualTo(1);
        assertThat(result.compactedFragmentCount()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        assertThat(longTermPort.savedRecords).hasSize(1);
        MemoryRecord master = longTermPort.savedRecords.get(0);
        assertThat(master.layer()).isEqualTo("long_term");
        assertThat(master.type()).isEqualTo("COMPACTED_SUMMARY");
        assertThat(master.content()).contains("Alpha project uses Spring.", "Alpha project target is May.");
        assertThat(master.metadata()).containsEntry("compactionGroupKey", "semanticKey:project.alpha");
        assertThat(master.metadata()).containsEntry("compactionStrategy", "semanticKey");
        assertThat(master.metadata().get("sourceMemoryIds")).asList().containsExactly("stm-1", "sem-1");
        assertThat(master.metadata().get("compactionGenerationId")).asString().startsWith("compaction-");
        assertThat(compactionPort.markedMasterIds).containsExactly(master.id());
        assertThat(outboxPort.tasks)
                .extracting(MemoryOutboxPort.MemoryOutboxTask::taskType)
                .containsExactlyInAnyOrder(
                        MemoryOutboxTaskTypes.VECTOR_UPSERT,
                        MemoryOutboxTaskTypes.KEYWORD_UPSERT,
                        MemoryOutboxTaskTypes.GRAPH_UPSERT,
                        MemoryOutboxTaskTypes.VECTOR_DELETE,
                        MemoryOutboxTaskTypes.KEYWORD_DELETE,
                        MemoryOutboxTaskTypes.GRAPH_DELETE,
                        MemoryOutboxTaskTypes.VECTOR_DELETE,
                        MemoryOutboxTaskTypes.KEYWORD_DELETE,
                        MemoryOutboxTaskTypes.GRAPH_DELETE);
    }

    @Test
    void shouldUsePluggableSummarizerForMasterMemoryContent() {
        RecordingCompactionPort compactionPort = new RecordingCompactionPort();
        RecordingLongTermMemoryPort longTermPort = new RecordingLongTermMemoryPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        compactionPort.candidates.add(new MemoryCompactionCandidate(
                "user-1",
                "default",
                "semanticKey:project.alpha",
                "semanticKey",
                List.of(
                        new MemoryCompactionFragment(
                                "stm-1",
                                "short_term",
                                "PROJECT_FACT",
                                "Alpha project uses Spring.",
                                Map.of("semanticKey", "project.alpha"),
                                Instant.EPOCH),
                        new MemoryCompactionFragment(
                                "sem-1",
                                "semantic",
                                "PROJECT_FACT",
                                "Alpha project target is May.",
                                Map.of("semanticKey", "project.alpha"),
                                Instant.EPOCH))));
        MemoryCompactionService service = new MemoryCompactionService(
                compactionPort,
                longTermPort,
                outboxPort,
                candidate -> new MemoryCompactionSummary(
                        "Alpha project uses Spring and targets May.",
                        "llm-test",
                        Map.of(
                                "model", "test-compactor",
                                "confidenceLevel", 0.93D,
                                "importanceScore", 0.91D)),
                new MemoryCompactionOptions(10, 2, false, false, false, "default"));

        MemoryCompactionResult result = service.run("manual-maintenance");

        assertThat(result.errors()).isEmpty();
        assertThat(longTermPort.savedRecords).hasSize(1);
        MemoryRecord master = longTermPort.savedRecords.get(0);
        assertThat(master.content()).isEqualTo("Alpha project uses Spring and targets May.");
        assertThat(master.metadata()).containsEntry("compactionSummaryStrategy", "llm-test");
        assertThat(master.metadata()).containsEntry("confidenceLevel", 0.93D);
        assertThat(master.metadata()).containsEntry("importanceScore", 0.91D);
        assertThat(master.metadata().get("compactionSummaryMetadata"))
                .isEqualTo(Map.of(
                        "model", "test-compactor",
                        "confidenceLevel", 0.93D,
                        "importanceScore", 0.91D));
    }

    @Test
    void shouldPassConfiguredMinGroupSizeToCompactionPortScan() {
        RecordingCompactionPort compactionPort = new RecordingCompactionPort();
        MemoryCompactionService service = new MemoryCompactionService(
                compactionPort,
                new RecordingLongTermMemoryPort(),
                new RecordingOutboxPort(),
                new MemoryCompactionOptions(25, 5, false, false, false, "default"));

        MemoryCompactionResult result = service.run("scheduled-maintenance");

        assertThat(result.errors()).isEmpty();
        assertThat(compactionPort.requestedLimit).isEqualTo(25);
        assertThat(compactionPort.requestedMinGroupSize).isEqualTo(5);
    }

    private static class RecordingCompactionPort implements MemoryCompactionPort {

        final List<MemoryCompactionCandidate> candidates = new ArrayList<>();
        final List<String> markedMasterIds = new ArrayList<>();
        int requestedLimit;
        int requestedMinGroupSize;

        @Override
        public List<MemoryCompactionCandidate> scanCandidates(int limit) {
            return candidates.stream().limit(limit).toList();
        }

        @Override
        public List<MemoryCompactionCandidate> scanCandidates(int limit, int minGroupSize) {
            requestedLimit = limit;
            requestedMinGroupSize = minGroupSize;
            return scanCandidates(limit);
        }

        @Override
        public int markCompacted(MemoryCompactionCandidate candidate, String masterMemoryId, Instant compactedAt) {
            markedMasterIds.add(masterMemoryId);
            return candidate.fragments().size();
        }
    }

    private static class RecordingLongTermMemoryPort implements LongTermMemoryPort {

        final List<MemoryRecord> savedRecords = new ArrayList<>();

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
            savedRecords.add(record);
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static class RecordingOutboxPort implements MemoryOutboxPort {

        final List<MemoryOutboxTask> tasks = new ArrayList<>();

        @Override
        public void enqueue(MemoryOutboxTask task) {
            tasks.add(task);
        }
    }
}
