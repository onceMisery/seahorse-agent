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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldQuality;
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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataGovernanceNodeFeatureTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        assertThat(savedRecords.get(0).rawCandidates())
                .extracting(MetadataFieldCandidate::fieldKey)
                .containsExactly("department");
        assertThat(writtenDocumentMetadata.get()).containsEntry("department", "FIN");
        assertThat(context.getChunks()).allSatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry("department", "FIN"));
    }

    @Test
    void shouldMapParserMetadataByParseKeysWithoutUsingSourceAliases() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("owner", MetadataValueType.STRING, false, Map.of("parseKeys", List.of("author")))));
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of(
                        "tenantId", "tenant-a",
                        "kbId", "kb-a",
                        "author", "source-author",
                        "parseMetadata", Map.of("author", "Data Team")))
                .build();

        NodeResult result = new MetadataExtractorNodeFeature(schemaRegistry)
                .execute(context, NodeConfig.builder().nodeType("metadata_extractor").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getMetadataCandidates()).hasSize(1);
        assertThat(context.getMetadataCandidates().get(0).fieldKey()).isEqualTo("owner");
        assertThat(context.getMetadataCandidates().get(0).sourceType()).isEqualTo("tika");
        assertThat(context.getMetadataCandidates().get(0).rawValue()).isEqualTo("Data Team");
        assertThat(context.getMetadataCandidates().get(0).evidence()).isEqualTo("author");
    }

    @Test
    void shouldUseContextExtractorVersionForReExtractCandidates() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 3, List.of(
                field("department", MetadataValueType.STRING, false, Map.of())));
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of(
                        "tenantId", "tenant-a",
                        "kbId", "kb-a",
                        "department", "Finance",
                        "extractorVersion", "extractor-v2"))
                .build();

        NodeResult result = new MetadataExtractorNodeFeature(schemaRegistry)
                .execute(context, NodeConfig.builder().nodeType("metadata_extractor").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getMetadataCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.fieldKey()).isEqualTo("department");
            assertThat(candidate.extractorVersion()).isEqualTo("extractor-v2");
        });
    }

    @Test
    void shouldPersistReExtractExtractorVersionAfterValidation() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 3, List.of(
                field("department", MetadataValueType.STRING, false, Map.of())));
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        List<MetadataExtractionRecord> savedRecords = new ArrayList<>();
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of(
                        "tenantId", "tenant-a",
                        "kbId", "kb-a",
                        "department", "Finance",
                        "extractorVersion", "extractor-v2"))
                .build();

        NodeResult extractResult = new MetadataExtractorNodeFeature(schemaRegistry)
                .execute(context, NodeConfig.builder().nodeType("metadata_extractor").build());
        NodeResult normalizeResult = new MetadataNormalizerNodeFeature(schemaRegistry, MetadataDictionaryPort.noop())
                .execute(context, NodeConfig.builder().nodeType("metadata_normalizer").build());
        NodeResult validateResult = new MetadataValidatorNodeFeature(schemaRegistry, savedRecords::add,
                MetadataReviewQueuePort.noop(), MetadataQuarantinePort.noop(), MetadataCanonicalWritePort.noop())
                .execute(context, NodeConfig.builder().nodeType("metadata_validator").build());

        assertThat(extractResult.isSuccess()).isTrue();
        assertThat(normalizeResult.isSuccess()).isTrue();
        assertThat(validateResult.isSuccess()).isTrue();
        assertThat(context.getMetadataValidationResult().decision()).isEqualTo(MetadataValidationDecision.ACCEPT);
        assertThat(savedRecords).singleElement()
                .satisfies(record -> assertThat(record.extractorVersion()).isEqualTo("extractor-v2"));
    }

    @Test
    void shouldKeepHighConfidenceDeterministicCandidateOverLlmConflict() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("department", MetadataValueType.STRING, false, Map.of())));
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a"))
                .metadataCandidates(List.of(
                        new MetadataFieldCandidate("department", "Finance", "source",
                                "SourceMetadataExtractor", 0.86D, "dept", 1, "deterministic-1"),
                        new MetadataFieldCandidate("department", "Legal", "llm",
                                "LlmMetadataExtractor", 0.99D, "paragraph", 1, "llm-1")))
                .build();

        NodeResult result = new MetadataNormalizerNodeFeature(schemaRegistry, MetadataDictionaryPort.noop())
                .execute(context, NodeConfig.builder().nodeType("metadata_normalizer").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getNormalizedMetadata()).containsEntry("department", "Finance");
        assertThat(context.getMetadataFieldQualities())
                .extracting(MetadataFieldQuality::sourceType)
                .containsExactly("source");
        assertThat(context.getMetadataIssues())
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("CANDIDATE_CONFLICT"));
    }

    @Test
    void shouldBoostConfidenceWhenMultipleExtractorsAgree() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("department", MetadataValueType.STRING, false, Map.of())));
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a"))
                .metadataCandidates(List.of(
                        new MetadataFieldCandidate("department", "Finance", "source",
                                "SourceMetadataExtractor", 0.78D, "dept", 1, "deterministic-1"),
                        new MetadataFieldCandidate("department", "Finance", "tika",
                                "TikaMetadataExtractor", 0.77D, "author", 1, "deterministic-1")))
                .build();

        NodeResult normalizeResult = new MetadataNormalizerNodeFeature(schemaRegistry, MetadataDictionaryPort.noop())
                .execute(context, NodeConfig.builder().nodeType("metadata_normalizer").build());
        NodeResult validateResult = new MetadataValidatorNodeFeature(schemaRegistry,
                MetadataExtractionResultRepositoryPort.noop(), MetadataReviewQueuePort.noop(),
                MetadataQuarantinePort.noop(), MetadataCanonicalWritePort.noop())
                .execute(context, NodeConfig.builder().nodeType("metadata_validator").build());

        assertThat(normalizeResult.isSuccess()).isTrue();
        assertThat(validateResult.isSuccess()).isTrue();
        assertThat(context.getMetadataFieldQualities()).singleElement()
                .satisfies(quality -> assertThat(quality.confidence()).isGreaterThanOrEqualTo(0.8D));
        assertThat(context.getMetadataValidationResult().decision()).isEqualTo(MetadataValidationDecision.ACCEPT);
        assertThat(context.getMetadataIssues()).isEmpty();
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

    @Test
    void shouldQuarantineUntrustedSecurityMetadataSource() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("securityLevel", MetadataValueType.STRING, false, Map.of())));
        List<String> quarantineReasons = new ArrayList<>();
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        MetadataQuarantinePort quarantinePort = item -> quarantineReasons.add(item.reasonCode());
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a"))
                .normalizedMetadata(Map.of("securityLevel", "internal"))
                .metadataFieldQualities(List.of(new MetadataFieldQuality(
                        "securityLevel", 0.99D, "llm", "LlmMetadataExtractor", true, "")))
                .build();

        NodeResult result = new MetadataValidatorNodeFeature(schemaRegistry,
                MetadataExtractionResultRepositoryPort.noop(), MetadataReviewQueuePort.noop(), quarantinePort,
                MetadataCanonicalWritePort.noop())
                .execute(context, NodeConfig.builder().nodeType("metadata_validator").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isShouldContinue()).isFalse();
        assertThat(context.isSkipIndexerWrite()).isTrue();
        assertThat(context.getMetadataValidationResult().decision()).isEqualTo(MetadataValidationDecision.QUARANTINE);
        assertThat(context.getMetadataIssues())
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("UNTRUSTED_METADATA_SOURCE"));
        assertThat(quarantineReasons).containsExactly("METADATA_QUARANTINE");
    }

    @Test
    void shouldRouteLowConfidenceMetadataToReviewWithoutCanonicalWrite() {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("department", MetadataValueType.STRING, false, Map.of())));
        MetadataSchemaRegistryPort schemaRegistry = (tenantId, knowledgeBaseId) -> schema;
        List<MetadataReviewItem> reviewItems = new ArrayList<>();
        List<MetadataExtractionRecord> savedRecords = new ArrayList<>();
        MetadataExtractionResultRepositoryPort resultRepository = new MetadataExtractionResultRepositoryPort() {
            @Override
            public void save(MetadataExtractionRecord record) {
                savedRecords.add(record);
            }

            @Override
            public String saveAndReturnId(MetadataExtractionRecord record) {
                save(record);
                return "result-a";
            }
        };
        AtomicReference<Map<String, Object>> writtenDocumentMetadata = new AtomicReference<>();
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a"))
                .normalizedMetadata(Map.of("department", "Finance"))
                .metadataFieldQualities(List.of(new MetadataFieldQuality(
                        "department", 0.5D, "llm", "LlmMetadataExtractor", true, "")))
                .metadataCandidates(List.of(new MetadataFieldCandidate(
                        "department", "Finance", "llm", "LlmMetadataExtractor",
                        0.5D, "财务部预算说明", 1, "extractor-v1")))
                .build();

        NodeResult result = new MetadataValidatorNodeFeature(schemaRegistry,
                resultRepository,
                reviewItems::add,
                MetadataQuarantinePort.noop(),
                (docId, acceptedMetadata) -> writtenDocumentMetadata.set(acceptedMetadata))
                .execute(context, NodeConfig.builder().nodeType("metadata_validator").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isShouldContinue()).isFalse();
        assertThat(context.isSkipIndexerWrite()).isTrue();
        assertThat(context.getMetadataValidationResult().decision()).isEqualTo(MetadataValidationDecision.REVIEW_REQUIRED);
        assertThat(reviewItems).extracting(MetadataReviewItem::reasonCode).containsExactly("METADATA_REVIEW_REQUIRED");
        assertThat(reviewItems.get(0).resultId()).isEqualTo("result-a");
        assertThat(reviewItems.get(0).reviewContext())
                .containsKeys("issues", "fieldQualities", "rawCandidates", "rejectedMetadata");
        assertThat(reviewItems.get(0).reviewContext().get("rawCandidates").toString())
                .contains("财务部预算说明");
        assertThat(writtenDocumentMetadata.get()).isNull();
        assertThat(context.getMetadata()).doesNotContainKeys("department", "acceptedMetadata");
    }

    @Test
    void shouldExtractOnlyRegisteredSchemaFieldsFromLlm() throws Exception {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("department", MetadataValueType.STRING, false, Map.of("description", "所属部门"))));
        AtomicReference<String> modelIdRef = new AtomicReference<>();
        AtomicReference<List<ChatMessage>> messagesRef = new AtomicReference<>();
        ChatModelPort modelPort = (request, modelId) -> {
            modelIdRef.set(modelId);
            messagesRef.set(request.getMessages());
            return """
                    {
                      "department": {"value": "Finance", "confidence": 0.73, "evidence": "财务部预算说明"},
                      "tenant_id": "evil"
                    }
                    """;
        };
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .rawText("财务部预算说明，面向内部员工。")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a"))
                .build();
        NodeConfig config = NodeConfig.builder()
                .nodeType("metadata_extractor")
                .settings(objectMapper.readTree("""
                        {
                          "tenantId": "tenant-a",
                          "kbId": "kb-a",
                          "llmEnabled": true,
                          "llmModel": "metadata-model",
                          "llmConfidence": 0.7,
                          "llmExtractorVersion": "llm-v2",
                          "llmPromptVersion": "prompt-v2"
                        }
                        """))
                .build();

        NodeResult result = new MetadataExtractorNodeFeature((tenantId, knowledgeBaseId) -> schema, modelPort)
                .execute(context, config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(modelIdRef.get()).isEqualTo("metadata-model");
        assertThat(messagesRef.get())
                .extracting(ChatMessage::getContent)
                .anySatisfy(content -> assertThat(content).contains("department").contains("只返回 JSON"));
        assertThat(messagesRef.get())
                .extracting(ChatMessage::getContent)
                .anySatisfy(content -> assertThat(content).contains("prompt-v2"));
        assertThat(context.getMetadataCandidates())
                .extracting(MetadataFieldCandidate::fieldKey)
                .containsExactly("department");
        assertThat(context.getMetadataCandidates().get(0).sourceType()).isEqualTo("llm");
        assertThat(context.getMetadataCandidates().get(0).extractorVersion()).isEqualTo("llm-v2");
        assertThat(context.getMetadataCandidates().get(0).confidence()).isEqualTo(0.73D);
        assertThat(context.getMetadataIssues())
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("LLM_FORBIDDEN_FIELD"));
    }

    @Test
    void shouldCapLlmConfidenceWhenEvidenceMissing() throws Exception {
        MetadataSchema schema = new MetadataSchema("tenant-a", "kb-a", 1, List.of(
                field("department", MetadataValueType.STRING, false, Map.of("description", "所属部门"))));
        ChatModelPort modelPort = (request, modelId) -> """
                {
                  "department": {"value": "Finance", "confidence": 0.95}
                }
                """;
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-a")
                .rawText("财务部预算说明，面向内部员工。")
                .metadata(Map.of("tenantId", "tenant-a", "kbId", "kb-a"))
                .build();
        NodeConfig config = NodeConfig.builder()
                .nodeType("metadata_extractor")
                .settings(objectMapper.readTree("""
                        {
                          "tenantId": "tenant-a",
                          "kbId": "kb-a",
                          "llmEnabled": true,
                          "llmModel": "metadata-model"
                        }
                        """))
                .build();

        NodeResult result = new MetadataExtractorNodeFeature((tenantId, knowledgeBaseId) -> schema, modelPort)
                .execute(context, config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getMetadataCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.fieldKey()).isEqualTo("department");
            assertThat(candidate.confidence()).isEqualTo(0.6D);
            assertThat(candidate.evidence()).isEmpty();
        });
        assertThat(context.getMetadataIssues())
                .anySatisfy(issue -> assertThat(issue.code()).isEqualTo("LLM_EVIDENCE_MISSING"));
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
