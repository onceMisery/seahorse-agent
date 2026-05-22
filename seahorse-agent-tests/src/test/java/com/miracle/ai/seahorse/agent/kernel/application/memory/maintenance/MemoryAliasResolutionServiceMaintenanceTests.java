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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolutionRunResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAliasResolutionServiceMaintenanceTests {

    @Test
    void shouldRunGlobalScanWhenNoUserScopeIsConfigured() {
        RecordingAliasPort aliasPort = new RecordingAliasPort();
        aliasPort.globalCandidates.add(new MemoryAliasCandidate(
                "user-9",
                "tenant-9",
                "  Alpha Team  ",
                "entity-alpha",
                "Alpha Team",
                "PROJECT",
                0.99D));
        MemoryAliasResolutionService service = new MemoryAliasResolutionService(
                aliasPort,
                new MemoryAliasResolutionOptions(20, "", "default", 0.95D, Map.of()));

        MemoryAliasResolutionRunResult result = service.run("manual-maintenance");

        assertThat(aliasPort.globalScanLimits).containsExactly(20);
        assertThat(aliasPort.scopedScanRequests).isEmpty();
        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.normalizedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(aliasPort.commands).hasSize(1);
        MemoryAliasCommand command = aliasPort.commands.get(0);
        assertThat(command.userId()).isEqualTo("user-9");
        assertThat(command.tenantId()).isEqualTo("tenant-9");
        assertThat(command.aliasText()).isEqualTo("alpha team");
        assertThat(command.metadata()).containsEntry("canonicalEntityId", "entity-alpha");
        assertThat(command.metadata()).containsEntry("canonicalName", "Alpha Team");
        assertThat(command.metadata()).containsEntry("entityType", "PROJECT");
        assertThat(command.metadata()).containsEntry("normalizationStrategy", "trim_case_whitespace");
    }

    @Test
    void shouldRunScopedScanWhenUserScopeIsConfigured() {
        RecordingAliasPort aliasPort = new RecordingAliasPort();
        aliasPort.scopedCandidates.add(new MemoryAliasCandidate(
                "user-1",
                "tenant-1",
                "  BK  ",
                "entity-booking",
                "Booking",
                "BUSINESS_TERM",
                0.98D,
                List.of("memory-1", "memory-2")));
        MemoryAliasResolutionService service = new MemoryAliasResolutionService(
                aliasPort,
                new MemoryAliasResolutionOptions(10, "user-1", "tenant-1", 0.95D, java.util.Map.of()));

        MemoryAliasResolutionRunResult result = service.run("manual-maintenance");

        assertThat(aliasPort.scopedScanRequests)
                .containsExactly("user-1|tenant-1|10");
        assertThat(aliasPort.globalScanLimits).isEmpty();
        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.normalizedCount()).isEqualTo(1);
        assertThat(aliasPort.commands).hasSize(1);
        MemoryAliasCommand command = aliasPort.commands.get(0);
        assertThat(command.userId()).isEqualTo("user-1");
        assertThat(command.tenantId()).isEqualTo("tenant-1");
        assertThat(command.aliasText()).isEqualTo("bk");
        assertThat(command.sourceMemoryIds()).containsExactly("memory-1", "memory-2");
        assertThat(command.metadata()).containsEntry("canonicalEntityId", "entity-booking");
        assertThat(command.metadata()).containsEntry("normalizationStrategy", "trim_case_whitespace");
    }

    private static final class RecordingAliasPort implements MemoryAliasPort {

        private final List<String> scopedScanRequests = new ArrayList<>();
        private final List<Integer> globalScanLimits = new ArrayList<>();
        private final List<MemoryAliasCandidate> scopedCandidates = new ArrayList<>();
        private final List<MemoryAliasCandidate> globalCandidates = new ArrayList<>();
        private final List<MemoryAliasCommand> commands = new ArrayList<>();

        @Override
        public Optional<MemoryAliasResolution> resolveAlias(String userId, String tenantId, String aliasText) {
            return Optional.empty();
        }

        @Override
        public void upsertAlias(MemoryAliasCommand command) {
            commands.add(command);
        }

        @Override
        public List<MemoryAliasCandidate> findMergeCandidates(String userId, String tenantId, int limit) {
            scopedScanRequests.add(userId + "|" + tenantId + "|" + limit);
            return scopedCandidates.stream().limit(limit).toList();
        }

        @Override
        public List<MemoryAliasCandidate> findMergeCandidates(int limit) {
            globalScanLimits.add(limit);
            return globalCandidates.stream().limit(limit).toList();
        }
    }
}
