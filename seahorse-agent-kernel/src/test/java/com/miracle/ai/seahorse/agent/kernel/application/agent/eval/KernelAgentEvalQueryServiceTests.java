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

package com.miracle.ai.seahorse.agent.kernel.application.agent.eval;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummaryPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummaryHistoryQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummarySaveCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelAgentEvalQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldAppendLatestAndHistoryByTenantAgentVersionAndType() {
        MemoryAgentEvalSummaryRepository repository = new MemoryAgentEvalSummaryRepository();
        KernelAgentEvalQueryService service = new KernelAgentEvalQueryService(repository, CLOCK);

        AgentEvalSummary first = service.saveSummary(command("summary-1", AgentEvalType.SAFETY, NOW.minusSeconds(60)));
        AgentEvalSummary second = service.saveSummary(command("summary-2", AgentEvalType.SAFETY, NOW));
        service.saveSummary(command("summary-3", AgentEvalType.RAG, NOW.plusSeconds(60)));

        assertEquals(first, repository.records.get(0));
        assertEquals("summary-2", second.summaryId());
        assertEquals("summary-2", service.latestSummary(
                "tenant-1",
                "agent-1",
                "version-1",
                AgentEvalType.SAFETY).orElseThrow().summaryId());
        AgentEvalSummaryPage history = service.history(new AgentEvalSummaryHistoryQuery(
                "tenant-1",
                "agent-1",
                "version-1",
                AgentEvalType.SAFETY,
                1L,
                10L));

        assertEquals(2L, history.total());
        assertEquals(List.of("summary-2", "summary-1"),
                history.records().stream().map(AgentEvalSummary::summaryId).toList());
    }

    private static AgentEvalSummarySaveCommand command(String summaryId,
                                                       AgentEvalType evalType,
                                                       Instant createdAt) {
        return new AgentEvalSummarySaveCommand(
                summaryId,
                "tenant-1",
                "agent-1",
                "version-1",
                evalType,
                AgentEvalStatus.PASS,
                0.95d,
                0.9d,
                0.7d,
                8,
                "dataset:v1",
                "eval-run-1",
                List.of("trace:1"),
                "admin-1",
                createdAt);
    }

    private static final class MemoryAgentEvalSummaryRepository implements AgentEvalSummaryRepositoryPort {

        private final List<AgentEvalSummary> records = new ArrayList<>();

        @Override
        public AgentEvalSummary append(AgentEvalSummary summary) {
            records.add(summary);
            return summary;
        }

        @Override
        public Optional<AgentEvalSummary> findLatest(String tenantId,
                                                     String agentId,
                                                     String versionId,
                                                     AgentEvalType evalType) {
            return records.stream()
                    .filter(summary -> summary.tenantId().equals(tenantId))
                    .filter(summary -> summary.agentId().equals(agentId))
                    .filter(summary -> summary.versionId().equals(versionId))
                    .filter(summary -> summary.evalType() == evalType)
                    .max(Comparator.comparing(AgentEvalSummary::createdAt)
                            .thenComparing(AgentEvalSummary::summaryId));
        }

        @Override
        public AgentEvalSummaryPage findHistory(AgentEvalSummaryQuery query) {
            List<AgentEvalSummary> filtered = records.stream()
                    .filter(summary -> summary.tenantId().equals(query.tenantId()))
                    .filter(summary -> summary.agentId().equals(query.agentId()))
                    .filter(summary -> summary.versionId().equals(query.versionId()))
                    .filter(summary -> query.evalType() == null || summary.evalType() == query.evalType())
                    .sorted(Comparator.comparing(AgentEvalSummary::createdAt)
                            .thenComparing(AgentEvalSummary::summaryId)
                            .reversed())
                    .toList();
            return new AgentEvalSummaryPage(filtered, filtered.size(), query.size(), query.current(), 1L);
        }
    }
}
