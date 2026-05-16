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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMetadataQualityServiceTests {

    @Test
    void shouldRecordObservationWhenQualityReportGenerated() {
        MetadataQualityReport report = new MetadataQualityReport(
                "tenant-1", "kb-1", 2, "extractor-v2", "prompt-v3",
                10, 8, 0.75D, 0.2D, 0.6D, 0.25D, 3, 2, 1,
                List.of(), List.of(), List.of(), Instant.EPOCH);
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelMetadataQualityService service = new KernelMetadataQualityService(
                (tenantId, knowledgeBaseId, quarantineTopN) -> report,
                observationPort);

        MetadataQualityReport result = service.report("tenant-1", "kb-1", 5, 2, "extractor-v2", "prompt-v3");

        assertThat(result).isEqualTo(report);
        assertThat(observationPort.events).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("metadata.quality.report.generated");
            assertThat(event.attributes())
                    .containsEntry("tenantId", "tenant-1")
                    .containsEntry("knowledgeBaseId", "kb-1")
                    .containsEntry("schemaVersion", "2")
                    .containsEntry("extractorVersion", "extractor-v2")
                    .containsEntry("llmPromptVersion", "prompt-v3")
                    .containsEntry("totalDocuments", "10")
                    .containsEntry("reviewCorrectionRate", "0.25")
                    .containsEntry("indexSyncFailureCount", "1");
        });
    }

    @Test
    void shouldPassSchemaAndExtractorVersionToRepository() {
        MetadataQualityReport report = MetadataQualityReport.empty("tenant-1", "kb-1");
        RecordingQualityReportRepository repository = new RecordingQualityReportRepository(report);
        KernelMetadataQualityService service = new KernelMetadataQualityService(repository);

        MetadataQualityReport result = service.report("tenant-1", "kb-1", 500, 2, "extractor-v2", "prompt-v3");

        assertThat(result).isEqualTo(report);
        assertThat(repository.topN).isEqualTo(50);
        assertThat(repository.schemaVersion).isEqualTo(2);
        assertThat(repository.extractorVersion).isEqualTo("extractor-v2");
        assertThat(repository.llmPromptVersion).isEqualTo("prompt-v3");
    }

    @Test
    void shouldBuildComparisonReportFromTwoVersionedReports() {
        RecordingQualityReportRepository repository = new RecordingQualityReportRepository(
                new MetadataQualityReport("tenant-1", "kb-1", 1, "extractor-v1", "prompt-v1",
                        10, 8, 0.6D, 0.3D, 0.7D, 0.2D, 2, 1, 0,
                        List.of(), List.of(), List.of(), Instant.EPOCH),
                new MetadataQualityReport("tenant-1", "kb-1", 2, "extractor-v2", "prompt-v2",
                        10, 9, 0.8D, 0.1D, 0.9D, 0.4D, 1, 1, 0,
                        List.of(), List.of(), List.of(), Instant.EPOCH));
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelMetadataQualityService service = new KernelMetadataQualityService(repository, observationPort);

        MetadataQualityComparisonReport comparison = service.compare(
                "tenant-1", "kb-1", 5,
                1, "extractor-v1", "prompt-v1",
                2, "extractor-v2", "prompt-v2");

        assertThat(comparison.delta().extractedDocumentsDelta()).isEqualTo(1);
        assertThat(comparison.delta().averageFieldCoverageDelta()).isEqualTo(0.2D);
        assertThat(comparison.delta().reviewCorrectionRateDelta()).isEqualTo(0.2D);
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("metadata.quality.compare.generated"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("coverageDelta", "0.2")
                        .containsEntry("fieldDeltaCount", "0"));
    }

    private static final class RecordingQualityReportRepository
            implements com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort {

        private final MetadataQualityReport report;
        private final MetadataQualityReport baselineReport;
        private final MetadataQualityReport candidateReport;
        private int topN;
        private Integer schemaVersion;
        private String extractorVersion;
        private String llmPromptVersion;

        private RecordingQualityReportRepository(MetadataQualityReport report) {
            this.report = report;
            this.baselineReport = report;
            this.candidateReport = report;
        }

        private RecordingQualityReportRepository(MetadataQualityReport baselineReport,
                                                 MetadataQualityReport candidateReport) {
            this.report = baselineReport;
            this.baselineReport = baselineReport;
            this.candidateReport = candidateReport;
        }

        @Override
        public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
            return report(tenantId, knowledgeBaseId, quarantineTopN, null, "");
        }

        @Override
        public MetadataQualityReport report(String tenantId,
                                            String knowledgeBaseId,
                                            int quarantineTopN,
                                            Integer schemaVersion,
                                            String extractorVersion) {
            return report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion, extractorVersion, "");
        }

        @Override
        public MetadataQualityReport report(String tenantId,
                                            String knowledgeBaseId,
                                            int quarantineTopN,
                                            Integer schemaVersion,
                                            String extractorVersion,
                                            String llmPromptVersion) {
            this.topN = quarantineTopN;
            this.schemaVersion = schemaVersion;
            this.extractorVersion = extractorVersion;
            this.llmPromptVersion = llmPromptVersion;
            if (Objects.equals(schemaVersion, baselineReport.schemaVersion())
                    && Objects.equals(extractorVersion, baselineReport.extractorVersion())
                    && Objects.equals(llmPromptVersion, baselineReport.llmPromptVersion())) {
                return baselineReport;
            }
            if (Objects.equals(schemaVersion, candidateReport.schemaVersion())
                    && Objects.equals(extractorVersion, candidateReport.extractorVersion())
                    && Objects.equals(llmPromptVersion, candidateReport.llmPromptVersion())) {
                return candidateReport;
            }
            return report;
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }
}
