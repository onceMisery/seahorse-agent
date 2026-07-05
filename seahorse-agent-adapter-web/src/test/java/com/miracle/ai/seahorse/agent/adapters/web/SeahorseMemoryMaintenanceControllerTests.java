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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunAggregate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseMemoryMaintenanceControllerTests {

    @Test
    void shouldRequestCompactionByDefaultWhenRunningMaintenance() throws Exception {
        RecordingMaintenancePort maintenancePort = new RecordingMaintenancePort();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(maintenancePort)).build();

        mvc.perform(post("/memories/maintenance/run")
                        .param("reason", "manual-maintenance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.compactionEnabled").value(true))
                .andExpect(jsonPath("$.data.garbageCollectionEnabled").value(true));

        assertThat(maintenancePort.commands).hasSize(1);
        MemoryMaintenanceRunCommand command = maintenancePort.commands.get(0);
        assertThat(command.reason()).isEqualTo("manual-maintenance");
        assertThat(command.compactionEnabled()).isTrue();
        assertThat(command.aliasEnabled()).isFalse();
        assertThat(command.garbageCollectionEnabled()).isTrue();
    }

    private static SeahorseMemoryMaintenanceController controller(MemoryMaintenanceInboundPort maintenancePort) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("memoryMaintenanceInboundPort", maintenancePort);
        return new SeahorseMemoryMaintenanceController(beanFactory.getBeanProvider(MemoryMaintenanceInboundPort.class));
    }

    private static final class RecordingMaintenancePort implements MemoryMaintenanceInboundPort {

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
                    null,
                    List.of(),
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
