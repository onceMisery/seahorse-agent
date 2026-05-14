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
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMetadataQualityServiceTests {

    @Test
    void shouldRecordObservationWhenQualityReportGenerated() {
        MetadataQualityReport report = new MetadataQualityReport(
                "tenant-1", "kb-1", 10, 8, 0.75D, 0.2D, 0.6D, 3, 2, 1,
                List.of(), List.of(), Instant.EPOCH);
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelMetadataQualityService service = new KernelMetadataQualityService(
                (tenantId, knowledgeBaseId, quarantineTopN) -> report,
                observationPort);

        MetadataQualityReport result = service.report("tenant-1", "kb-1", 5);

        assertThat(result).isEqualTo(report);
        assertThat(observationPort.events).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("metadata.quality.report.generated");
            assertThat(event.attributes())
                    .containsEntry("tenantId", "tenant-1")
                    .containsEntry("knowledgeBaseId", "kb-1")
                    .containsEntry("totalDocuments", "10")
                    .containsEntry("indexSyncFailureCount", "1");
        });
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
