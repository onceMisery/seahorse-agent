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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategy;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategyDelta;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverageDelta;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonDelta;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineReasonCount;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.VersionQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KernelVersionQualityComparisonServiceTests {

    @Test
    void shouldCombineMetadataAndRetrievalComparison() {
        RecordingMetadataQualityPort metadataPort = new RecordingMetadataQualityPort(metadataComparisonReport());
        RecordingRetrievalEvaluationPort retrievalPort = new RecordingRetrievalEvaluationPort(
                new RetrievalEvaluationComparisonReport(
                        "baseline",
                        "candidate",
                        List.of(
                                new RetrievalEvaluationReport("baseline", 2, 1, 1,
                                        0.5D, 0.5D, 0.5D, 0.0D,
                                        20.0D, 20.0D, List.of()),
                                new RetrievalEvaluationReport("candidate", 2, 1, 1,
                                        1.0D, 1.0D, 1.0D, 0.0D,
                                        18.0D, 18.0D, List.of())),
                        List.of(
                                new RetrievalEvaluationStrategyDelta("baseline", 0D, 0D, 0D, 0D, 0D, 0D),
                                new RetrievalEvaluationStrategyDelta("candidate", 0.5D, 0.5D, 0.5D, 0D, -2D, -2D))));
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelVersionQualityComparisonService service = new KernelVersionQualityComparisonService(
                metadataPort, retrievalPort, observationPort);

        VersionQualityComparisonReport report = service.compare(new VersionQualityComparisonCommand(
                "tenant-1",
                "kb-1",
                3,
                1,
                "extractor-v1",
                "prompt-v1",
                2,
                "extractor-v2",
                "prompt-v3",
                new RetrievalEvaluationComparisonCommand(
                        "baseline",
                        2,
                        List.of(
                                new RetrievalEvaluationStrategy("baseline", 2, null),
                                new RetrievalEvaluationStrategy("candidate", 2, null)),
                        List.of(new RetrievalEvaluationCase(
                                "case-1", "question-a", List.of(), List.of("doc-1"), List.of(), null, null)))));

        assertThat(report.tenantId()).isEqualTo("tenant-1");
        assertThat(report.knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(report.metadataQuality()).isEqualTo(metadataComparisonReport());
        assertThat(report.retrievalQuality().winnerStrategyName()).isEqualTo("candidate");
        assertThat(metadataPort.tenantId).isEqualTo("tenant-1");
        assertThat(metadataPort.knowledgeBaseId).isEqualTo("kb-1");
        assertThat(metadataPort.quarantineTopN).isEqualTo(3);
        assertThat(metadataPort.baselineSchemaVersion).isEqualTo(1);
        assertThat(metadataPort.candidateSchemaVersion).isEqualTo(2);
        assertThat(retrievalPort.command.baselineStrategyName()).isEqualTo("baseline");
        assertThat(retrievalPort.command.strategies()).hasSize(2);
        assertThat(retrievalPort.command.cases()).hasSize(1);
        assertThat(observationPort.lastEvent).isNotNull();
        assertThat(observationPort.lastEvent.name()).isEqualTo("version.quality.compare.generated");
        assertThat(observationPort.lastEvent.attributes())
                .containsEntry("retrievalStrategyCount", "2")
                .containsEntry("retrievalCaseCount", "1")
                .containsEntry("metadataFieldDeltaCount", "1");
    }

    @Test
    void shouldNormalizeNonPositiveSchemaVersionAndMissingRetrievalCommand() {
        RecordingMetadataQualityPort metadataPort = new RecordingMetadataQualityPort(metadataComparisonReport());
        RecordingRetrievalEvaluationPort retrievalPort = new RecordingRetrievalEvaluationPort(
                new RetrievalEvaluationComparisonReport("", "", List.of(), List.of()));
        KernelVersionQualityComparisonService service = new KernelVersionQualityComparisonService(
                metadataPort, retrievalPort);

        VersionQualityComparisonReport report = service.compare(new VersionQualityComparisonCommand(
                "tenant-1",
                "kb-1",
                0,
                0,
                null,
                null,
                -1,
                null,
                null,
                null));

        assertThat(metadataPort.quarantineTopN).isEqualTo(5);
        assertThat(metadataPort.baselineSchemaVersion).isNull();
        assertThat(metadataPort.candidateSchemaVersion).isNull();
        assertThat(retrievalPort.command).isNotNull();
        assertThat(retrievalPort.command.strategies()).isEmpty();
        assertThat(retrievalPort.command.cases()).isEmpty();
        assertThat(report.retrievalQuality().reports()).isEmpty();
    }

    private static MetadataQualityComparisonReport metadataComparisonReport() {
        MetadataQualityReport baseline = new MetadataQualityReport(
                "tenant-1", "kb-1", 1, "extractor-v1", "prompt-v1",
                4, 2, 0.5D, 0.25D, 0.7D, 0.1D, 1, 1, 0,
                List.of(new MetadataFieldCoverage("department", "部门", true,
                        2, 4, 0.5D, 1, 0.5D, 1, 0, 0D)),
                List.of(),
                List.of(new MetadataQuarantineReasonCount("SCHEMA_MISSING", "缺少 Schema", 1)),
                Instant.EPOCH);
        MetadataQualityReport candidate = new MetadataQualityReport(
                "tenant-1", "kb-1", 2, "extractor-v2", "prompt-v3",
                4, 3, 0.75D, 0.2D, 0.8D, 0.2D, 1, 0, 0,
                List.of(new MetadataFieldCoverage("department", "部门", true,
                        3, 4, 0.75D, 0, 0D, 2, 1, 0.5D)),
                List.of(),
                List.of(),
                Instant.EPOCH);
        return new MetadataQualityComparisonReport(
                "tenant-1",
                "kb-1",
                baseline,
                candidate,
                new MetadataQualityComparisonDelta(0, 1, 0.25D, -0.05D, 0.1D, 0.1D, 0, -1, 0),
                List.of(new MetadataFieldCoverageDelta(
                        "department", "部门", 1, -1, 1, 1, 0.25D, -0.5D, 0.5D)));
    }

    private static final class RecordingMetadataQualityPort implements MetadataQualityInboundPort {

        private final MetadataQualityComparisonReport report;
        private String tenantId;
        private String knowledgeBaseId;
        private int quarantineTopN;
        private Integer baselineSchemaVersion;
        private Integer candidateSchemaVersion;

        private RecordingMetadataQualityPort(MetadataQualityComparisonReport report) {
            this.report = report;
        }

        @Override
        public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MetadataQualityComparisonReport compare(String tenantId,
                                                       String knowledgeBaseId,
                                                       int quarantineTopN,
                                                       Integer baselineSchemaVersion,
                                                       String baselineExtractorVersion,
                                                       String baselineLlmPromptVersion,
                                                       Integer candidateSchemaVersion,
                                                       String candidateExtractorVersion,
                                                       String candidateLlmPromptVersion) {
            this.tenantId = tenantId;
            this.knowledgeBaseId = knowledgeBaseId;
            this.quarantineTopN = quarantineTopN;
            this.baselineSchemaVersion = baselineSchemaVersion;
            this.candidateSchemaVersion = candidateSchemaVersion;
            return report;
        }
    }

    private static final class RecordingRetrievalEvaluationPort implements RetrievalEvaluationInboundPort {

        private final RetrievalEvaluationComparisonReport report;
        private RetrievalEvaluationComparisonCommand command;

        private RecordingRetrievalEvaluationPort(RetrievalEvaluationComparisonReport report) {
            this.report = report;
        }

        @Override
        public RetrievalEvaluationReport evaluate(com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RetrievalEvaluationComparisonReport compare(RetrievalEvaluationComparisonCommand command) {
            this.command = command;
            return report;
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private ObservationEvent lastEvent;

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    lastEvent = event;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            lastEvent = event;
        }
    }
}
