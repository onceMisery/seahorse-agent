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

package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineRetryCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMetadataQuarantineServiceTests {

    @Test
    void shouldResolveAndScheduleRetryThroughRepository() {
        InMemoryQuarantineRepository repository = new InMemoryQuarantineRepository();
        repository.put(quarantine("q-1", false, 1, null));
        KernelMetadataQuarantineService service = new KernelMetadataQuarantineService(repository);

        MetadataQuarantineRecord resolved = service.resolve("q-1", "auditor");
        MetadataQuarantineRecord retried = service.retry("q-1",
                new MetadataQuarantineRetryCommand("auditor", Instant.parse("2026-05-13T10:00:00Z")));

        assertThat(resolved.resolved()).isTrue();
        assertThat(retried.resolved()).isFalse();
        assertThat(retried.retryCount()).isEqualTo(2);
        assertThat(retried.nextRetryTime()).isEqualTo(Instant.parse("2026-05-13T10:00:00Z"));
    }

    private static MetadataQuarantineRecord quarantine(String id,
                                                       boolean resolved,
                                                       int retryCount,
                                                       Instant nextRetryTime) {
        return new MetadataQuarantineRecord(
                id,
                "tenant-1",
                "kb-1",
                "doc-1",
                "job-1",
                "VALIDATE",
                "SCHEMA_MISSING",
                "缺少 Schema",
                Map.of("source", "test"),
                retryCount,
                nextRetryTime,
                resolved,
                resolved ? "auditor" : "",
                resolved ? Instant.EPOCH : null,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static final class InMemoryQuarantineRepository implements MetadataQuarantineManagementRepositoryPort {

        private final Map<String, MetadataQuarantineRecord> records = new LinkedHashMap<>();

        void put(MetadataQuarantineRecord record) {
            records.put(record.id(), record);
        }

        @Override
        public MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
            return new MetadataQuarantinePage(List.copyOf(records.values()), records.size(), query.size(),
                    query.current(), 1);
        }

        @Override
        public Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
            return Optional.ofNullable(records.get(itemId));
        }

        @Override
        public MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
            MetadataQuarantineRecord current = findQuarantineItem(resolution.itemId()).orElseThrow();
            MetadataQuarantineRecord updated = quarantine(current.id(), true, current.retryCount(),
                    current.nextRetryTime());
            records.put(updated.id(), updated);
            return updated;
        }

        @Override
        public MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
            MetadataQuarantineRecord current = findQuarantineItem(retry.itemId()).orElseThrow();
            MetadataQuarantineRecord updated = quarantine(current.id(), false, current.retryCount() + 1,
                    retry.nextRetryTime());
            records.put(updated.id(), updated);
            return updated;
        }
    }
}
