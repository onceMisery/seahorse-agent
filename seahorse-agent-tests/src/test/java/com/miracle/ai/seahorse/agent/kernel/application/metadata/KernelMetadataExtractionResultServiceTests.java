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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelMetadataExtractionResultServiceTests {

    @Test
    void shouldValidateAndDelegateReadonlyQueries() {
        InMemoryExtractionResultRepository repository = new InMemoryExtractionResultRepository();
        KernelMetadataExtractionResultService service = new KernelMetadataExtractionResultService(repository);

        MetadataExtractionResultPage page = service.page(
                "tenant-1", "kb-1", "doc-1", "job-1", "ACCEPTED", 1, 10);
        MetadataExtractionResultRecord detail = service.queryById("result-1");

        assertThat(repository.lastQuery.tenantId()).isEqualTo("tenant-1");
        assertThat(page.records()).hasSize(1);
        assertThat(detail.id()).isEqualTo("result-1");
    }

    @Test
    void shouldRejectBlankIdentity() {
        KernelMetadataExtractionResultService service =
                new KernelMetadataExtractionResultService(MetadataExtractionResultManagementRepositoryPort.empty());

        assertThatThrownBy(() -> service.page("", "", "", "", "", 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> service.queryById(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultId");
    }

    private static class InMemoryExtractionResultRepository
            implements MetadataExtractionResultManagementRepositoryPort {

        private MetadataExtractionResultQuery lastQuery;

        @Override
        public MetadataExtractionResultPage pageExtractionResults(MetadataExtractionResultQuery query) {
            lastQuery = query;
            return new MetadataExtractionResultPage(List.of(result()), 1, query.size(), query.current(), 1);
        }

        @Override
        public Optional<MetadataExtractionResultRecord> findExtractionResult(String resultId) {
            return Optional.of(result());
        }

        private MetadataExtractionResultRecord result() {
            return new MetadataExtractionResultRecord(
                    "result-1",
                    "tenant-1",
                    "kb-1",
                    "doc-1",
                    "job-1",
                    2,
                    "extractor-v2",
                    "ACCEPTED",
                    Map.of("department", "HR"),
                    List.of(Map.of("fieldKey", "department")),
                    List.of(Map.of("fieldKey", "department", "confidence", 0.93D)),
                    List.of(),
                    Map.of("department", "HR"),
                    "auditor",
                    Instant.EPOCH,
                    Instant.EPOCH,
                    Instant.EPOCH);
        }
    }
}
