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

package com.miracle.ai.seahorse.agent.kernel.application.agent.cost;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelCostUsageQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAppendRecordsAndAggregateByTenantAgentAndRun() {
        MemoryCostUsageRepository repository = new MemoryCostUsageRepository();
        KernelCostUsageQueryService service = new KernelCostUsageQueryService(repository);

        service.append(record("usage-1", "tenant-a", "agent-1", "run-1", 100L, 2L, 0.5d));
        service.append(record("usage-2", "tenant-a", "agent-1", "run-1", 50L, 1L, 0.25d));
        service.append(record("usage-3", "tenant-a", "agent-1", "run-2", 10L, 1L, 0.05d));

        CostUsageAggregate aggregate = service.aggregate(new CostUsageQuery(
                "tenant-a",
                "agent-1",
                "run-1",
                null,
                null));

        assertEquals("tenant-a", aggregate.tenantId());
        assertEquals("agent-1", aggregate.agentId());
        assertEquals("run-1", aggregate.runId());
        assertEquals(150L, aggregate.totalTokens());
        assertEquals(3L, aggregate.totalCalls());
        assertEquals(0.75d, aggregate.totalCost());
        assertEquals(2L, aggregate.recordCount());
    }

    private static CostUsageRecord record(String usageId,
                                          String tenantId,
                                          String agentId,
                                          String runId,
                                          Long tokens,
                                          Long calls,
                                          Double cost) {
        return new CostUsageRecord(
                usageId,
                tenantId,
                agentId,
                runId,
                "user-1",
                "tool-1",
                "model-1",
                CostUsageSource.MODEL,
                tokens,
                calls,
                cost,
                null,
                NOW);
    }

    private static final class MemoryCostUsageRepository implements CostUsageRepositoryPort {

        private final List<CostUsageRecord> records = new ArrayList<>();

        @Override
        public CostUsageRecord append(CostUsageRecord record) {
            records.add(record);
            return record;
        }

        @Override
        public CostUsageAggregate aggregate(CostUsageQuery query) {
            List<CostUsageRecord> matched = records.stream()
                    .filter(record -> record.tenantId().equals(query.tenantId()))
                    .filter(record -> query.agentId() == null || record.agentId().equals(query.agentId()))
                    .filter(record -> query.runId() == null || record.runId().equals(query.runId()))
                    .toList();
            return new CostUsageAggregate(
                    query.tenantId(),
                    query.agentId(),
                    query.runId(),
                    matched.stream().mapToLong(CostUsageRecord::tokens).sum(),
                    matched.stream().mapToLong(CostUsageRecord::calls).sum(),
                    matched.stream().mapToDouble(CostUsageRecord::cost).sum(),
                    matched.size());
        }
    }
}
