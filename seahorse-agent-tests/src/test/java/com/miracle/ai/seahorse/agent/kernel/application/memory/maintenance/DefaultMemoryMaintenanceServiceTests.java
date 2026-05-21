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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMemoryMaintenanceServiceTests {

    @Test
    void shouldRunGarbageCollectionWhenRequestedAndEnabled() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                false,
                false,
                true);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                false,
                false,
                true));

        assertThat(garbageCollectionService.reasons).containsExactly("manual-maintenance");
        assertThat(result.reason()).isEqualTo("manual-maintenance");
        assertThat(result.garbageCollectionResult()).isNotNull();
        assertThat(result.garbageCollectionResult().reason()).isEqualTo("manual-maintenance");
        assertThat(result.skippedTasks()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldSkipUnavailableMaintenanceTracksWithoutRunningGarbageCollectionWhenDisabled() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                false,
                false,
                false);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                true,
                true,
                true));

        assertThat(garbageCollectionService.reasons).isEmpty();
        assertThat(result.garbageCollectionResult()).isNull();
        assertThat(result.skippedTasks()).containsExactly(
                MemoryMaintenanceRunResult.SKIP_COMPACTION_UNAVAILABLE,
                MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE,
                MemoryMaintenanceRunResult.SKIP_GARBAGE_COLLECTION_DISABLED);
        assertThat(result.errors()).isEmpty();
    }

    private static class RecordingGarbageCollectionService extends MemoryGarbageCollectionService {

        private final List<String> reasons = new ArrayList<>();

        private RecordingGarbageCollectionService() {
            super(MemoryGarbageCollectionPort.noop(),
                    MemoryOutboxPort.noop(),
                    MemoryGarbageCollectionOptions.vectorOnly());
        }

        @Override
        public MemoryGarbageCollectionResult run(String reason) {
            reasons.add(reason);
            return new MemoryGarbageCollectionResult(
                    reason,
                    1,
                    1,
                    1,
                    false,
                    List.of(),
                    Instant.EPOCH);
        }
    }
}
