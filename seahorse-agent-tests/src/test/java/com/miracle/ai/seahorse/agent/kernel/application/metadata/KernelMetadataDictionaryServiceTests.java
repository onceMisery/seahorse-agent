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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelMetadataDictionaryServiceTests {

    @Test
    void shouldValidateAndDelegateDictionaryManagement() {
        InMemoryDictionaryRepository repository = new InMemoryDictionaryRepository();
        KernelMetadataDictionaryService service = new KernelMetadataDictionaryService(repository);

        MetadataDictionaryItemRecord created = service.createItem(payload("finance", "FIN"));
        MetadataDictionaryItemRecord updated = service.updateItem(created.id(), payload("finance", "FINANCE"));
        boolean deleted = service.deleteItem(created.id());

        assertThat(created.id()).isEqualTo("dict-1");
        assertThat(service.listItems("tenant-1", "department", true)).hasSize(1);
        assertThat(updated.canonicalValue()).isEqualTo("FINANCE");
        assertThat(deleted).isTrue();
        assertThat(repository.disabledItemId).isEqualTo("dict-1");
    }

    @Test
    void shouldRejectBlankRequiredFields() {
        KernelMetadataDictionaryService service =
                new KernelMetadataDictionaryService(MetadataDictionaryManagementRepositoryPort.empty());

        assertThatThrownBy(() -> service.createItem(new MetadataDictionaryItemPayload(
                "tenant-1", "", "raw", "canonical", "canonical", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dictionaryCode");
        assertThatThrownBy(() -> service.listItems("", "department", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    private MetadataDictionaryItemPayload payload(String rawValue, String canonicalValue) {
        return new MetadataDictionaryItemPayload(
                "tenant-1",
                "department",
                rawValue,
                canonicalValue,
                canonicalValue,
                true);
    }

    private static class InMemoryDictionaryRepository implements MetadataDictionaryManagementRepositoryPort {

        private MetadataDictionaryItemRecord record;
        private String disabledItemId;

        @Override
        public List<MetadataDictionaryItemRecord> listDictionaryItems(String tenantId,
                                                                      String dictionaryCode,
                                                                      boolean includeDisabled) {
            return record == null ? List.of() : List.of(record);
        }

        @Override
        public Optional<MetadataDictionaryItemRecord> findDictionaryItem(String itemId) {
            return record != null && record.id().equals(itemId) ? Optional.of(record) : Optional.empty();
        }

        @Override
        public String createDictionaryItem(MetadataDictionaryItemPayload payload) {
            record = record("dict-1", payload);
            return record.id();
        }

        @Override
        public MetadataDictionaryItemRecord updateDictionaryItem(String itemId,
                                                                 MetadataDictionaryItemPayload payload) {
            record = record(itemId, payload);
            return record;
        }

        @Override
        public boolean disableDictionaryItem(String itemId) {
            disabledItemId = itemId;
            return true;
        }

        private MetadataDictionaryItemRecord record(String itemId, MetadataDictionaryItemPayload payload) {
            return new MetadataDictionaryItemRecord(
                    itemId,
                    payload.tenantId(),
                    payload.dictionaryCode(),
                    payload.rawValue(),
                    payload.canonicalValue(),
                    payload.displayName(),
                    payload.enabled(),
                    Instant.EPOCH,
                    Instant.EPOCH);
        }
    }
}
