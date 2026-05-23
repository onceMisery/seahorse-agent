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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunAggregate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseMemoryMaintenanceJobTests {

    @Test
    void shouldRunConfiguredMaintenanceTracks() {
        RecordingMemoryMaintenancePort maintenancePort = new RecordingMemoryMaintenancePort();
        SeahorseMemoryMaintenanceJob job = new SeahorseMemoryMaintenanceJob(
                maintenancePort,
                null,
                true,
                true,
                false);

        job.runMaintenance();

        assertThat(maintenancePort.commands).hasSize(1);
        MemoryMaintenanceRunCommand command = maintenancePort.commands.get(0);
        assertThat(command.reason()).isEqualTo("scheduled-maintenance");
        assertThat(command.compactionEnabled()).isTrue();
        assertThat(command.aliasEnabled()).isTrue();
        assertThat(command.garbageCollectionEnabled()).isFalse();
    }

    private static final class RecordingMemoryMaintenancePort implements MemoryMaintenanceInboundPort {

        private final List<MemoryMaintenanceRunCommand> commands = new ArrayList<>();

        @Override
        public MemoryMaintenanceRunResult runMaintenance(MemoryMaintenanceRunCommand command) {
            commands.add(command);
            return new MemoryMaintenanceRunResult(
                    command.reason(),
                    command.compactionEnabled(),
                    command.aliasEnabled(),
                    command.garbageCollectionEnabled(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    Instant.EPOCH);
        }

        @Override
        public MemoryMaintenanceRunPage pageMaintenanceRuns(MemoryMaintenanceRunQuery query) {
            return MemoryMaintenanceRunPage.empty(query.current(), query.size());
        }

        @Override
        public MemoryMaintenanceRunAggregate aggregateRecent(int limit) {
            return MemoryMaintenanceRunAggregate.empty(MemoryMaintenanceRunAggregate.clampLimit(limit));
        }
    }
}
