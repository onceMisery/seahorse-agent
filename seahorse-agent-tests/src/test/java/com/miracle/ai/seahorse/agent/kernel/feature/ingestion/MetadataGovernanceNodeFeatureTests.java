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

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataGovernanceNodeFeatureTests {

    @Test
    void shouldExtractNormalizeValidateAndAttachAcceptedMetadataToChunks() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 3, List.of(
                field("department", MetadataValueType.STRING, true,
                        Map.of("dictionaryCode", "department", "sourceKeys", List.of("dept")))));
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        MetadataDictionaryPort dictionary = (tenantId, dictionaryCode, rawValue) ->
                "department".equals(dictionaryCode) && "Finance".equals(rawValue)
                        ? Optional.of("FIN") : Optional.empty();
        List<MetadataExtractionRecord> savedRecords = new ArrayList<>();
        AtomicReference<Map<String, Object>> writtenDocumentMetadata = new AtomicReference<>();
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .rawText("content")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a", "dept", "Finance"))
                .build();

        NodeResult extractResult = new MetadataExtractorNodeFeature(schemaRegistry)
                .execute(context, NodeConfig.builder().nodeType("metadata_extractor").build());
        NodeResult normalizeResult = new MetadataNormalizerNodeFeature(schemaRegistry, dictionary)
                .execute(context, NodeConfig.builder().nodeType("metadata_normalizer").build());
        NodeResult validateResult = new MetadataValidatorNodeFeature(schemaRegistry, savedRecords::add,
                MetadataReviewQueuePort.noop(), MetadataQuarantinePort.noop(),
                (docId, acceptedMetadata) -> writtenDocumentMetadata.set(acceptedMetadata))
                .execute(context, NodeConfig.builder().nodeType("metadata_validator").build());
        ChunkerNodeFeature chunker = new ChunkerNodeFeature((modelId, text) -> List.of(1.0F));
        NodeResult chunkResult = chunker.execute(context, NodeConfig.builder().nodeType("chunker").build());

        assertThat(extractResult.isSuccess()).isTrue();
        assertThat(normalizeResult.isSuccess()).isTrue();
        assertThat(validateResult.isSuccess()).isTrue();
        assertThat(chunkResult.isSuccess()).isTrue();
        assertThat(context.getMetadataValidationResult().decision()).isEqualTo(MetadataValidationDecision.ACCEPT);
        assertThat(context.getMetadataValidationResult().acceptedMetadata()).containsEntry("department", "FIN");
        assertThat(savedRecords).hasSize(1);
        assertThat(writtenDocumentMetadata.get()).containsEntry("department", "FIN");
        assertThat(context.getChunks()).allSatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry("department", "FIN"));
    }

    @Test
    void shouldQuarantineWhenRequiredMetadataMissing() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("securityLevel", MetadataValueType.STRING, true, Map.of())));
        List<String> quarantineReasons = new ArrayList<>();
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        MetadataQuarantinePort quarantinePort = item -> quarantineReasons.add(item.reasonCode());
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a"))
                .build();

        NodeResult result = new MetadataValidatorNodeFeature(schemaRegistry,
                MetadataExtractionResultRepositoryPort.noop(), MetadataReviewQueuePort.noop(), quarantinePort,
                MetadataCanonicalWritePort.noop())
                .execute(context, NodeConfig.builder().nodeType("metadata_validator").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isShouldContinue()).isFalse();
        assertThat(context.isSkipIndexerWrite()).isTrue();
        assertThat(context.getMetadataValidationResult().decision()).isEqualTo(MetadataValidationDecision.QUARANTINE);
        assertThat(quarantineReasons).containsExactly("METADATA_QUARANTINE");
    }

    private MetadataFieldDescriptor field(String fieldKey,
                                          MetadataValueType valueType,
                                          boolean required,
                                          Map<String, Object> hints) {
        return new MetadataFieldDescriptor(fieldKey, fieldKey, valueType, Set.of(MetadataOperator.EQ),
                required, true, false, false, true, MetadataIndexPolicy.SEARCH_KEYWORD, 0.8D,
                Set.of("source", "tika", "rule"), hints, BackendFieldMapping.defaults(fieldKey));
    }
}
