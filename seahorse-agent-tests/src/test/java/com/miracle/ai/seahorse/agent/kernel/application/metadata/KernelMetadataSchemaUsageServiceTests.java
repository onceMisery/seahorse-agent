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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMetadataSchemaUsageServiceTests {

    @Test
    void shouldNormalizeSchemaVersionBeforeDelegating() {
        MetadataSchemaUsageReport expected = new MetadataSchemaUsageReport(
                "tenant-1",
                "kb-1",
                2,
                4L,
                1L,
                1L,
                0.25D,
                0.2D,
                List.of(new MetadataSchemaUsageFieldRecord("department", "部门", 3L, 1L, 1L, 1D / 3D, 0.25D)),
                Instant.EPOCH);
        RecordingRepository repository = new RecordingRepository(expected);
        KernelMetadataSchemaUsageService service = new KernelMetadataSchemaUsageService(repository);

        MetadataSchemaUsageReport result = service.report("tenant-1", "kb-1", 2);

        assertThat(result).isEqualTo(expected);
        assertThat(repository.tenantId).isEqualTo("tenant-1");
        assertThat(repository.knowledgeBaseId).isEqualTo("kb-1");
        assertThat(repository.schemaVersion).isEqualTo(2);
    }

    @Test
    void shouldTreatNonPositiveSchemaVersionAsAllVersions() {
        RecordingRepository repository = new RecordingRepository(MetadataSchemaUsageReport.empty("tenant-1", "kb-1", null));
        KernelMetadataSchemaUsageService service = new KernelMetadataSchemaUsageService(repository);

        service.report("tenant-1", "kb-1", 0);

        assertThat(repository.schemaVersion).isNull();
    }

    private static final class RecordingRepository implements MetadataSchemaUsageReportRepositoryPort {

        private final MetadataSchemaUsageReport report;
        private String tenantId;
        private String knowledgeBaseId;
        private Integer schemaVersion;

        private RecordingRepository(MetadataSchemaUsageReport report) {
            this.report = report;
        }

        @Override
        public MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
            this.tenantId = tenantId;
            this.knowledgeBaseId = knowledgeBaseId;
            this.schemaVersion = schemaVersion;
            return report;
        }
    }
}
