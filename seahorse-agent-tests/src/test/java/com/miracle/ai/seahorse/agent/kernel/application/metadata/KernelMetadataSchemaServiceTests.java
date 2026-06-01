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

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldCapabilityRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMetadataSchemaServiceTests {

    @Test
    void shouldSyncIndexMappingAndRequestSchemaCompensationAfterCreateUpdateAndDeleteField() {
        InMemorySchemaRepository repository = new InMemorySchemaRepository();
        CapturingSchemaIndexSyncPort syncPort = new CapturingSchemaIndexSyncPort();
        CapturingMetadataIndexCompensationPort compensationPort = new CapturingMetadataIndexCompensationPort();
        KernelMetadataSchemaService service = new KernelMetadataSchemaService(repository, syncPort, compensationPort);

        MetadataSchemaFieldRecord created = service.createField("kb-1", searchablePayload("department", 3));
        MetadataSchemaFieldRecord updated = service.updateField(created.id(), searchablePayload("region", 4));
        boolean deleted = service.deleteField(created.id());

        assertThat(syncPort.createdFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("department");
        assertThat(syncPort.updatedPreviousFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("department");
        assertThat(syncPort.updatedCurrentFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("region");
        assertThat(syncPort.deletedFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("region");

        assertThat(compensationPort.createdFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("department");
        assertThat(compensationPort.updatedPreviousFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("department");
        assertThat(compensationPort.updatedCurrentFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("region");
        assertThat(compensationPort.deletedFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("region");
        assertThat(updated.knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(deleted).isTrue();
    }

    @Test
    void shouldSkipSchemaCompensationForNonRetrievalField() {
        InMemorySchemaRepository repository = new InMemorySchemaRepository();
        CapturingSchemaIndexSyncPort syncPort = new CapturingSchemaIndexSyncPort();
        CapturingMetadataIndexCompensationPort compensationPort = new CapturingMetadataIndexCompensationPort();
        KernelMetadataSchemaService service = new KernelMetadataSchemaService(repository, syncPort, compensationPort);

        MetadataSchemaFieldRecord created = service.createField("kb-1", nonRetrievalPayload("raw_note", 1));
        service.updateField(created.id(), nonRetrievalPayload("raw_note_v2", 2));
        service.deleteField(created.id());

        // 索引映射同步仍然保留，由底层适配器自行决定是否 no-op；这里只验证不触发全量补偿。
        assertThat(syncPort.createdFields()).hasSize(1);
        assertThat(compensationPort.createdFields()).isEmpty();
        assertThat(compensationPort.updatedPreviousFields()).isEmpty();
        assertThat(compensationPort.updatedCurrentFields()).isEmpty();
        assertThat(compensationPort.deletedFields()).isEmpty();
    }

    @Test
    void shouldExposeSchemaFieldCapabilities() {
        InMemorySchemaRepository repository = new InMemorySchemaRepository();
        KernelMetadataSchemaService service = new KernelMetadataSchemaService(repository);

        service.createField("kb-1", searchablePayload("department", 3));

        assertThat(service.listFieldCapabilities("tenant-1", "kb-1"))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability.fieldKey()).isEqualTo("department");
                    assertThat(capability.indexed()).isTrue();
                    assertThat(capability.pushdownToKeyword()).isTrue();
                    assertThat(capability.guardOnly()).isFalse();
                    assertThat(capability.schemaVersion()).isEqualTo(3);
                });
    }

    private MetadataSchemaFieldPayload searchablePayload(String fieldKey, int schemaVersion) {
        return new MetadataSchemaFieldPayload(
                "tenant-1",
                "kb-1",
                fieldKey,
                fieldKey,
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ, MetadataOperator.IN),
                false,
                true,
                false,
                true,
                true,
                MetadataIndexPolicy.SEARCH_KEYWORD,
                0.8D,
                Set.of(),
                Map.of(),
                new BackendFieldMapping(fieldKey, "", "",
                        "metadata." + fieldKey + ".keyword", false, true, false, Map.of()),
                schemaVersion);
    }

    private MetadataSchemaFieldPayload nonRetrievalPayload(String fieldKey, int schemaVersion) {
        return new MetadataSchemaFieldPayload(
                "tenant-1",
                "kb-1",
                fieldKey,
                fieldKey,
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ),
                false,
                false,
                false,
                false,
                false,
                MetadataIndexPolicy.NONE,
                0.8D,
                Set.of(),
                Map.of(),
                new BackendFieldMapping(fieldKey, "", "", fieldKey, false, false, false, Map.of()),
                schemaVersion);
    }

    private static final class CapturingSchemaIndexSyncPort implements MetadataSchemaIndexSyncPort {

        private final List<MetadataSchemaFieldRecord> createdFields = new java.util.ArrayList<>();
        private final List<MetadataSchemaFieldRecord> updatedPreviousFields = new java.util.ArrayList<>();
        private final List<MetadataSchemaFieldRecord> updatedCurrentFields = new java.util.ArrayList<>();
        private final List<MetadataSchemaFieldRecord> deletedFields = new java.util.ArrayList<>();

        @Override
        public void syncField(MetadataSchemaFieldRecord field) {
            createdFields.add(field);
        }

        @Override
        public void syncFieldChange(MetadataSchemaFieldRecord previousField, MetadataSchemaFieldRecord currentField) {
            updatedPreviousFields.add(previousField);
            updatedCurrentFields.add(currentField);
        }

        @Override
        public void deleteField(MetadataSchemaFieldRecord field) {
            deletedFields.add(field);
        }

        List<MetadataSchemaFieldRecord> createdFields() {
            return createdFields;
        }

        List<MetadataSchemaFieldRecord> updatedPreviousFields() {
            return updatedPreviousFields;
        }

        List<MetadataSchemaFieldRecord> updatedCurrentFields() {
            return updatedCurrentFields;
        }

        List<MetadataSchemaFieldRecord> deletedFields() {
            return deletedFields;
        }
    }

    private static final class CapturingMetadataIndexCompensationPort implements MetadataIndexCompensationPort {

        private final List<MetadataSchemaFieldRecord> createdFields = new java.util.ArrayList<>();
        private final List<MetadataSchemaFieldRecord> updatedPreviousFields = new java.util.ArrayList<>();
        private final List<MetadataSchemaFieldRecord> updatedCurrentFields = new java.util.ArrayList<>();
        private final List<MetadataSchemaFieldRecord> deletedFields = new java.util.ArrayList<>();

        @Override
        public void rebuildDocument(Long documentId) {
        }

        @Override
        public void compensateSchemaChange(MetadataSchemaFieldRecord field) {
            createdFields.add(field);
        }

        @Override
        public void compensateSchemaChange(MetadataSchemaFieldRecord previousField,
                                           MetadataSchemaFieldRecord currentField) {
            if (previousField != null && currentField == null) {
                deletedFields.add(previousField);
                return;
            }
            updatedPreviousFields.add(previousField);
            updatedCurrentFields.add(currentField);
        }

        List<MetadataSchemaFieldRecord> createdFields() {
            return createdFields;
        }

        List<MetadataSchemaFieldRecord> updatedPreviousFields() {
            return updatedPreviousFields;
        }

        List<MetadataSchemaFieldRecord> updatedCurrentFields() {
            return updatedCurrentFields;
        }

        List<MetadataSchemaFieldRecord> deletedFields() {
            return deletedFields;
        }
    }

    private static final class InMemorySchemaRepository implements MetadataSchemaManagementRepositoryPort {

        private final Map<String, MetadataSchemaFieldRecord> records = new LinkedHashMap<>();
        private int sequence;

        @Override
        public List<MetadataSchemaFieldRecord> listSchemaFields(String tenantId, String knowledgeBaseId) {
            return List.copyOf(records.values());
        }

        @Override
        public List<MetadataSchemaFieldCapabilityRecord> listSchemaFieldCapabilities(String tenantId,
                                                                                     String knowledgeBaseId) {
            return records.values().stream()
                    .map(record -> new MetadataSchemaFieldCapabilityRecord(
                            record.id(),
                            record.tenantId(),
                            record.knowledgeBaseId(),
                            record.fieldKey(),
                            record.displayName(),
                            record.valueType(),
                            record.filterable(),
                            record.sortable(),
                            record.facetable(),
                            record.indexed(),
                            record.indexPolicy(),
                            record.backendMapping().pushdownToKeyword(),
                            record.backendMapping().pushdownToVector(),
                            record.backendMapping().guardOnly(),
                            record.schemaVersion(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            null,
                            record.updateTime()))
                    .toList();
        }

        @Override
        public Optional<MetadataSchemaFieldRecord> findSchemaField(String fieldId) {
            return Optional.ofNullable(records.get(fieldId));
        }

        @Override
        public String createSchemaField(MetadataSchemaFieldPayload payload) {
            String id = "field-" + (++sequence);
            records.put(id, record(id, payload));
            return id;
        }

        @Override
        public MetadataSchemaFieldRecord updateSchemaField(String fieldId, MetadataSchemaFieldPayload payload) {
            MetadataSchemaFieldRecord record = record(fieldId, payload);
            records.put(fieldId, record);
            return record;
        }

        @Override
        public boolean deleteSchemaField(String fieldId) {
            return records.remove(fieldId) != null;
        }

        private MetadataSchemaFieldRecord record(String id, MetadataSchemaFieldPayload payload) {
            Instant now = Instant.parse("2026-05-13T00:00:00Z");
            return new MetadataSchemaFieldRecord(id, payload.tenantId(), payload.knowledgeBaseId(),
                    payload.fieldKey(), payload.displayName(), payload.valueType(), payload.allowedOperators(),
                    payload.required(), payload.filterable(), payload.sortable(), payload.facetable(),
                    payload.indexed(), payload.indexPolicy(), payload.minConfidence(), payload.trustedSources(),
                    payload.extractionHints(), payload.backendMapping(), payload.schemaVersion(), now, now);
        }
    }
}
