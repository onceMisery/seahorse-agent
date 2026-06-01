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

package com.miracle.ai.seahorse.agent.adapters.spring.metadata;

import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentVectorPorts;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 默认的元数据索引补偿适配器。
 *
 * <p>文档级补偿同时刷新关键词索引和向量 metadata；Schema 级补偿统一转换为异步回填任务。
 */
public class MetadataIndexCompensationAdapter implements MetadataIndexCompensationPort {

    private static final String SCHEMA_COMPENSATION_OPERATOR = "metadata-schema-compensation";
    private static final String SCHEMA_COMPENSATION_REASON = "SCHEMA_CHANGE";
    private static final Set<String> VECTOR_SYSTEM_METADATA_KEYS = Set.of(
            "tenant_id", "kb_id", "doc_id", "chunk_id", "chunk_index", "collection_name",
            "enabled", "acl_subjects", "security_level", "file_type", "source_type", "created_at", "updated_at");

    private final KnowledgeDocumentRepositoryPort documentRepositoryPort;
    private final KeywordIndexMaintenanceInboundPort keywordMaintenancePort;
    private final KnowledgeDocumentVectorPorts vectorPorts;
    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final MetadataBackfillInboundPort backfillInboundPort;

    public MetadataIndexCompensationAdapter(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                            KeywordIndexMaintenanceInboundPort keywordMaintenancePort,
                                            KnowledgeDocumentVectorPorts vectorPorts) {
        this(documentRepositoryPort, keywordMaintenancePort, vectorPorts, MetadataSchemaRegistryPort.empty(), null);
    }

    public MetadataIndexCompensationAdapter(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                            KeywordIndexMaintenanceInboundPort keywordMaintenancePort,
                                            KnowledgeDocumentVectorPorts vectorPorts,
                                            MetadataSchemaRegistryPort schemaRegistryPort,
                                            MetadataBackfillInboundPort backfillInboundPort) {
        this.documentRepositoryPort = Objects.requireNonNull(documentRepositoryPort,
                "documentRepositoryPort must not be null");
        this.keywordMaintenancePort = Objects.requireNonNull(keywordMaintenancePort,
                "keywordMaintenancePort must not be null");
        this.vectorPorts = Objects.requireNonNull(vectorPorts, "vectorPorts must not be null");
        this.schemaRegistryPort = Objects.requireNonNullElseGet(schemaRegistryPort, MetadataSchemaRegistryPort::empty);
        this.backfillInboundPort = backfillInboundPort;
    }

    @Override
    public void rebuildDocument(Long documentId) {
        rebuildDocument("", null, documentId);
    }

    @Override
    public void rebuildDocument(String tenantId, Long knowledgeBaseId, Long documentId) {
        Long safeDocumentId = Objects.requireNonNull(documentId, "documentId must not be null");
        keywordMaintenancePort.rebuildDocument(safeDocumentId);
        rebuildVectorIndex(tenantId, knowledgeBaseId, requireDocument(safeDocumentId));
    }

    @Override
    public void compensateSchemaChange(MetadataSchemaFieldRecord previousField,
                                       MetadataSchemaFieldRecord currentField) {
        MetadataSchemaFieldRecord effectiveField = currentField != null ? currentField : previousField;
        if (effectiveField == null || backfillInboundPort == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", effectiveField.schemaVersion());
        metadata.put("forceRerun", true);
        metadata.put("overwriteApproved", true);
        metadata.put("schemaCompensation", true);
        metadata.put("schemaCompensationReason", SCHEMA_COMPENSATION_REASON);
        metadata.put("schemaTriggerAction", resolveSchemaTriggerAction(previousField, currentField));
        metadata.put("schemaTriggerFieldKey", effectiveField.fieldKey());
        // Schema 变更补偿走异步回填，避免在管理接口里同步重扫全库。
        backfillInboundPort.createJob(new MetadataBackfillCommand(
                effectiveField.tenantId(),
                effectiveField.knowledgeBaseId(),
                "",
                0,
                SCHEMA_COMPENSATION_OPERATOR,
                metadata));
    }

    private void rebuildVectorIndex(String tenantId, Long knowledgeBaseId, KnowledgeDocumentDetail document) {
        String collectionName = requireText(document.getCollectionName(), "collectionName");
        vectorPorts.vectorIndexPort().deleteDocumentVectors(collectionName, String.valueOf(document.getId()));
        if (Boolean.FALSE.equals(document.getEnabled())) {
            return;
        }
        List<KnowledgeChunkRecord> chunks = documentRepositoryPort.listEnabledChunks(document.getId());
        if (chunks.isEmpty()) {
            return;
        }
        String effectiveTenantId = defaultText(tenantId, tenantIdFromChunks(chunks));
        Long effectiveKnowledgeBaseId = knowledgeBaseId != null ? knowledgeBaseId : document.getKbId();
        MetadataSchema schema = loadSchema(effectiveTenantId, String.valueOf(effectiveKnowledgeBaseId));
        List<VectorChunk> vectorChunks = chunks.stream()
                .filter(Objects::nonNull)
                .map(chunk -> toVectorChunk(effectiveTenantId, document, schema, chunk))
                .toList();
        if (vectorChunks.isEmpty()) {
            return;
        }
        vectorPorts.vectorIndexPort().indexDocumentChunks(collectionName, String.valueOf(document.getId()), vectorChunks);
    }

    private KnowledgeDocumentDetail requireDocument(Long documentId) {
        return documentRepositoryPort.findDetailById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + documentId));
    }

    private MetadataSchema loadSchema(String tenantId, String knowledgeBaseId) {
        if (!hasText(tenantId) || !hasText(knowledgeBaseId)) {
            return MetadataSchema.empty(tenantId, knowledgeBaseId);
        }
        return Objects.requireNonNullElseGet(schemaRegistryPort.loadSchema(tenantId, knowledgeBaseId),
                () -> MetadataSchema.empty(tenantId, knowledgeBaseId));
    }

    private VectorChunk toVectorChunk(String tenantId,
                                      KnowledgeDocumentDetail document,
                                      MetadataSchema schema,
                                      KnowledgeChunkRecord record) {
        Map<String, Object> sourceMetadata = systemMetadata(tenantId, document, record);
        return VectorChunk.builder()
                .chunkId(String.valueOf(record.getId()))
                .index(record.getChunkIndex())
                .content(Objects.requireNonNullElse(record.getContent(), ""))
                .embedding(toArray(vectorPorts.embeddingModelPort().embed(
                        document.getEmbeddingModel(), Objects.requireNonNullElse(record.getContent(), ""))))
                .metadata(vectorMetadata(document, schema, record, sourceMetadata))
                .build();
    }

    private Map<String, Object> systemMetadata(String tenantId,
                                               KnowledgeDocumentDetail document,
                                               KnowledgeChunkRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(record.getMetadata(), Map.of()));
        putIfPresent(metadata, "tenant_id", defaultText(textValue(metadata.get("tenant_id")), tenantId));
        putIfPresent(metadata, "collection_name", document.getCollectionName());
        putIfPresent(metadata, "kb_id", document.getKbId());
        putIfPresent(metadata, "doc_id", document.getId());
        putIfPresent(metadata, "chunk_id", record.getId());
        putIfPresent(metadata, "chunk_index", record.getChunkIndex());
        metadata.put("enabled", record.getEnabled() == null || record.getEnabled() == 1);
        putIfPresent(metadata, "file_type", document.getFileType());
        putIfPresent(metadata, "source_type", document.getSourceType());
        putIfPresent(metadata, "created_at", document.getCreateTime());
        putIfPresent(metadata, "updated_at", document.getUpdateTime());
        return metadata;
    }

    private Map<String, Object> vectorMetadata(KnowledgeDocumentDetail document,
                                               MetadataSchema schema,
                                               KnowledgeChunkRecord record,
                                               Map<String, Object> sourceMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        VECTOR_SYSTEM_METADATA_KEYS.forEach(key -> putIfPresent(metadata, key, sourceMetadata.get(key)));
        putIfPresent(metadata, "collection_name", document.getCollectionName());
        putIfPresent(metadata, "kb_id", document.getKbId());
        putIfPresent(metadata, "doc_id", document.getId());
        putIfPresent(metadata, "chunk_id", record.getId());
        putIfPresent(metadata, "chunk_index", record.getChunkIndex());
        if (schema != null && !schema.empty()) {
            for (MetadataFieldDescriptor field : schema.fields()) {
                if (field.backendMapping().pushdownToVector()) {
                    String key = canonicalKey(field);
                    putIfPresent(metadata, key, sourceMetadata.get(key));
                }
            }
        }
        return metadata;
    }

    private String canonicalKey(MetadataFieldDescriptor field) {
        String canonicalName = field.backendMapping().canonicalName();
        return hasText(canonicalName) ? canonicalName.trim() : field.fieldKey();
    }

    private String tenantIdFromChunks(List<KnowledgeChunkRecord> chunks) {
        for (KnowledgeChunkRecord chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String tenantId = textValue(Objects.requireNonNullElse(chunk.getMetadata(), Map.of()).get("tenant_id"));
            if (hasText(tenantId)) {
                return tenantId;
            }
        }
        return "";
    }

    private String resolveSchemaTriggerAction(MetadataSchemaFieldRecord previousField,
                                              MetadataSchemaFieldRecord currentField) {
        if (previousField == null && currentField != null) {
            return "CREATE";
        }
        if (previousField != null && currentField == null) {
            return "DELETE";
        }
        return "UPDATE";
    }

    private float[] toArray(List<Float> values) {
        List<Float> safeValues = Objects.requireNonNullElse(values, List.of());
        float[] result = new float[safeValues.size()];
        for (int index = 0; index < safeValues.size(); index++) {
            result[index] = safeValues.get(index);
        }
        return result;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (!hasText(key) || !present(value)) {
            return;
        }
        metadata.put(key, value);
    }

    private boolean present(Object value) {
        return value != null && !(value instanceof String text && text.isBlank());
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String defaultText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private String textValue(Object value) {
        return Objects.toString(value, "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
