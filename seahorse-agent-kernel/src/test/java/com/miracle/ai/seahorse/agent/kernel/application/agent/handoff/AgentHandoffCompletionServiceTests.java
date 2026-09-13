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

package com.miracle.ai.seahorse.agent.kernel.application.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHandoffCompletionServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCompleteHandoffAsSucceededByChildRunId() {
        MemoryHandoffRepository repository = new MemoryHandoffRepository();
        AgentHandoffCompletionService service = new AgentHandoffCompletionService(repository, null, FIXED_CLOCK);
        repository.save(runningHandoff("handoff-1", "child-1"));

        AgentHandoff completed = service.completeForChildRun("child-1", true, null);

        assertEquals(AgentHandoffStatus.SUCCEEDED, completed.status());
        assertEquals(AgentHandoffStatus.SUCCEEDED, repository.handoffs.get("handoff-1").status());
    }

    @Test
    void shouldCompleteHandoffAsFailedWithDefaultFailureCode() {
        MemoryHandoffRepository repository = new MemoryHandoffRepository();
        AgentHandoffCompletionService service = new AgentHandoffCompletionService(repository, null, FIXED_CLOCK);
        repository.save(runningHandoff("handoff-1", "child-1"));

        AgentHandoff completed = service.completeForChildRun("child-1", false, null);

        assertEquals(AgentHandoffStatus.FAILED, completed.status());
        assertEquals(AgentHandoffFailureCode.CHILD_RUN_FAILED, completed.failureCode());
    }

    @Test
    void shouldCancelHandoffByChildRunId() {
        MemoryHandoffRepository repository = new MemoryHandoffRepository();
        AgentHandoffCompletionService service = new AgentHandoffCompletionService(repository, null, FIXED_CLOCK);
        repository.save(runningHandoff("handoff-1", "child-1"));

        AgentHandoff cancelled = service.cancelForChildRun("child-1");

        assertEquals(AgentHandoffStatus.CANCELLED, cancelled.status());
    }

    @Test
    void shouldBeIdempotentForTerminalHandoffs() {
        MemoryHandoffRepository repository = new MemoryHandoffRepository();
        AgentHandoffCompletionService service = new AgentHandoffCompletionService(repository, null, FIXED_CLOCK);
        repository.save(runningHandoff("handoff-1", "child-1"));
        service.completeForChildRun("child-1", true, null);

        assertNull(service.completeForChildRun("child-1", false, AgentHandoffFailureCode.CHILD_RUN_FAILED));
        assertEquals(AgentHandoffStatus.SUCCEEDED, repository.handoffs.get("handoff-1").status());
    }

    @Test
    void shouldReturnNullWhenNoHandoffMatchesChildRun() {
        MemoryHandoffRepository repository = new MemoryHandoffRepository();
        AgentHandoffCompletionService service = new AgentHandoffCompletionService(repository, null, FIXED_CLOCK);

        assertNull(service.completeForChildRun("unknown-child", true, null));
        assertNull(service.cancelForChildRun("unknown-child"));
    }

    private AgentHandoff runningHandoff(String handoffId, String childRunId) {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        return new AgentHandoff(
                handoffId,
                "tenant-1",
                "run-parent",
                childRunId,
                "agent-source",
                "agent-target",
                AgentHandoffStatus.RUNNING,
                null,
                "TEAM_DISPATCH",
                null,
                "{}",
                null,
                now,
                now,
                null);
    }

    private static final class MemoryHandoffRepository implements com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort {

        final Map<String, AgentHandoff> handoffs = new LinkedHashMap<>();

        @Override
        public AgentHandoff save(AgentHandoff handoff) {
            handoffs.put(handoff.handoffId(), handoff);
            return handoff;
        }

        @Override
        public AgentHandoff update(AgentHandoff handoff) {
            handoffs.put(handoff.handoffId(), handoff);
            return handoff;
        }

        @Override
        public Optional<AgentHandoff> findById(String handoffId) {
            return Optional.ofNullable(handoffs.get(handoffId));
        }

        @Override
        public List<AgentHandoff> listByParentRunId(String tenantId, String parentRunId) {
            return List.copyOf(handoffs.values());
        }

        @Override
        public Optional<AgentHandoff> findByChildRunId(String childRunId) {
            return handoffs.values().stream()
                    .filter(handoff -> childRunId.equals(handoff.childRunId()))
                    .findFirst();
        }
    }
}
