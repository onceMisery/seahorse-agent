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
    void shouldSyncIndexMappingAfterCreateAndUpdateField() {
        InMemorySchemaRepository repository = new InMemorySchemaRepository();
        CapturingSchemaIndexSyncPort syncPort = new CapturingSchemaIndexSyncPort();
        KernelMetadataSchemaService service = new KernelMetadataSchemaService(repository, syncPort);

        MetadataSchemaFieldRecord created = service.createField("kb-1", payload("department", 3));
        MetadataSchemaFieldRecord updated = service.updateField(created.id(), payload("region", 4));

        assertThat(syncPort.syncedFields()).extracting(MetadataSchemaFieldRecord::fieldKey)
                .containsExactly("department", "region");
        assertThat(syncPort.syncedFields()).extracting(MetadataSchemaFieldRecord::schemaVersion)
                .containsExactly(3, 4);
        assertThat(updated.knowledgeBaseId()).isEqualTo("kb-1");
    }

    private MetadataSchemaFieldPayload payload(String fieldKey, int schemaVersion) {
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

    private static final class CapturingSchemaIndexSyncPort implements MetadataSchemaIndexSyncPort {

        private final List<MetadataSchemaFieldRecord> syncedFields = new java.util.ArrayList<>();

        @Override
        public void syncField(MetadataSchemaFieldRecord field) {
            syncedFields.add(field);
        }

        List<MetadataSchemaFieldRecord> syncedFields() {
            return syncedFields;
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
