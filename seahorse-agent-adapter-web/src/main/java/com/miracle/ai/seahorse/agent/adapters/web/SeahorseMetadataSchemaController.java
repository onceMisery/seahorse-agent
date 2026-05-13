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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Metadata Schema 字段管理 Web adapter。
 */
@RestController
@ConditionalOnBean(MetadataSchemaInboundPort.class)
public class SeahorseMetadataSchemaController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final MetadataSchemaInboundPort schemaPort;

    public SeahorseMetadataSchemaController(MetadataSchemaInboundPort schemaPort) {
        this.schemaPort = Objects.requireNonNull(schemaPort, "schemaPort must not be null");
    }

    @GetMapping("/knowledge-base/{kb-id}/metadata-schema/fields")
    public Map<String, Object> listFields(@PathVariable("kb-id") String kbId,
                                          @RequestParam String tenantId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, schemaPort.listFields(tenantId, kbId));
    }

    @PostMapping("/knowledge-base/{kb-id}/metadata-schema/fields")
    public Map<String, Object> createField(@PathVariable("kb-id") String kbId,
                                           @RequestBody MetadataSchemaFieldRequest request) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, schemaPort.createField(kbId, toPayload(kbId, request)));
    }

    @PutMapping("/metadata-schema/fields/{field-id}")
    public Map<String, Object> updateField(@PathVariable("field-id") String fieldId,
                                           @RequestBody MetadataSchemaFieldRequest request) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, schemaPort.updateField(fieldId, toPayload("", request)));
    }

    @DeleteMapping("/metadata-schema/fields/{field-id}")
    public Map<String, Object> deleteField(@PathVariable("field-id") String fieldId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, Map.of("deleted", schemaPort.deleteField(fieldId)));
    }

    private MetadataSchemaFieldPayload toPayload(String kbId, MetadataSchemaFieldRequest request) {
        MetadataSchemaFieldRequest safeRequest = request == null ? new MetadataSchemaFieldRequest() : request;
        String fieldKey = Objects.requireNonNullElse(safeRequest.getFieldKey(), "");
        return new MetadataSchemaFieldPayload(
                safeRequest.getTenantId(),
                kbId,
                fieldKey,
                safeRequest.getDisplayName(),
                enumValue(MetadataValueType.class, safeRequest.getValueType(), MetadataValueType.STRING),
                operators(safeRequest),
                Boolean.TRUE.equals(safeRequest.getRequired()),
                Boolean.TRUE.equals(safeRequest.getFilterable()),
                Boolean.TRUE.equals(safeRequest.getSortable()),
                Boolean.TRUE.equals(safeRequest.getFacetable()),
                Boolean.TRUE.equals(safeRequest.getIndexed()),
                enumValue(MetadataIndexPolicy.class, safeRequest.getIndexPolicy(), MetadataIndexPolicy.NONE),
                safeRequest.getMinConfidence() == null ? 0.8D : safeRequest.getMinConfidence(),
                safeRequest.getTrustedSources() == null ? Set.of() : Set.copyOf(safeRequest.getTrustedSources()),
                Objects.requireNonNullElse(safeRequest.getExtractionHints(), Map.of()),
                Objects.requireNonNullElse(safeRequest.getBackendMapping(), BackendFieldMapping.defaults(fieldKey)),
                safeRequest.getSchemaVersion() == null ? 1 : safeRequest.getSchemaVersion());
    }

    private Set<MetadataOperator> operators(MetadataSchemaFieldRequest request) {
        if (request.getAllowedOperators() == null || request.getAllowedOperators().isEmpty()) {
            return Set.of(MetadataOperator.EQ, MetadataOperator.IN);
        }
        return request.getAllowedOperators().stream()
                .map(value -> enumValue(MetadataOperator.class, value, null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(type, value.trim().toUpperCase());
    }
}
