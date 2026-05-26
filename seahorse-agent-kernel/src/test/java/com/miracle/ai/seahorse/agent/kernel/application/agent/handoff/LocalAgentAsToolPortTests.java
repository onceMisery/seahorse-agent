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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAgentAsToolPortTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldCreateChildRunOnlyThroughRunPort() {
        MemoryAgentHandoffRepository repository = new MemoryAgentHandoffRepository();
        RecordingRunPort runPort = new RecordingRunPort();
        KernelAgentHandoffService service = new KernelAgentHandoffService(
                repository,
                runPort,
                new DefaultMeshPolicyPort(),
                FIXED_CLOCK);
        LocalAgentAsToolPort tool = new LocalAgentAsToolPort(service);

        ToolInvocationResult result = tool.invoke("call-1", LocalAgentAsToolPort.TOOL_ID, Map.of(
                "tenantId", "tenant-1",
                "parentRunId", "parent-run-1",
                "sourceAgentId", "source-agent",
                "targetAgentId", "target-agent",
                "targetVersionId", "target-version-1",
                "handoffReason", "delegate analysis",
                "inputSummary", "summarized handoff input",
                "depth", 1,
                "ancestorAgentIds", List.of("source-agent")));

        assertTrue(result.success());
        assertTrue(result.content().contains("child-run-1"));
        assertEquals(1, runPort.startedCommands.size());
        assertEquals("target-agent", runPort.startedCommands.get(0).agentId());
        assertEquals("target-version-1", runPort.startedCommands.get(0).versionId());
        assertEquals(AgentRunTriggerType.A2A, runPort.startedCommands.get(0).triggerType());
        assertEquals(1, repository.handoffs.size());
        AgentHandoff handoff = repository.handoffs.values().iterator().next();
        assertEquals(AgentHandoffStatus.RUNNING, handoff.status());
        assertEquals("parent-run-1", handoff.parentRunId());
        assertEquals("child-run-1", handoff.childRunId());
    }

    private static final class MemoryAgentHandoffRepository implements AgentHandoffRepositoryPort {
        private final Map<String, AgentHandoff> handoffs = new LinkedHashMap<>();

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
            return handoffs.values().stream()
                    .filter(handoff -> tenantId.equals(handoff.tenantId()))
                    .filter(handoff -> parentRunId.equals(handoff.parentRunId()))
                    .toList();
        }
    }

    private static final class RecordingRunPort implements AgentRunInboundPort {
        private final List<AgentRunStartCommand> startedCommands = new ArrayList<>();

        @Override
        public AgentRun startRun(AgentRunStartCommand command) {
            startedCommands.add(command);
            return new AgentRun(
                    "child-run-1",
                    command.agentId(),
                    command.versionId(),
                    command.tenantId(),
                    "user-1",
                    command.conversationId(),
                    command.triggerType(),
                    command.inputSummary(),
                    AgentRunStatus.RUNNING,
                    command.traceId(),
                    0,
                    0,
                    BigDecimal.ZERO,
                    null,
                    null,
                    NOW,
                    null);
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.empty();
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep> listSteps(String runId) {
            return List.of();
        }

        @Override
        public AgentRun cancel(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentRun retry(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentRun succeed(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentRun fail(String runId, String errorCode, String errorMessage) {
            throw new UnsupportedOperationException();
        }
    }
}
