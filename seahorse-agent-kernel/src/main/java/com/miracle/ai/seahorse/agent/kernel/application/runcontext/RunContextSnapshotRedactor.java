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

package com.miracle.ai.seahorse.agent.kernel.application.runcontext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialJsonFieldClassifier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redacts credential material from run context snapshots before persistence or projection.
 */
public final class RunContextSnapshotRedactor {

    private final ObjectMapper objectMapper;

    public RunContextSnapshotRedactor() {
        this(new ObjectMapper());
    }

    RunContextSnapshotRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RunContextSnapshotRecord redact(RunContextSnapshotRecord record) {
        if (record == null) {
            return null;
        }
        RunContextSnapshotRecord safe = new RunContextSnapshotRecord();
        safe.setId(record.getId());
        safe.setTenantId(record.getTenantId());
        safe.setRunId(record.getRunId());
        safe.setConversationId(record.getConversationId());
        safe.setBranchLeafMessageId(record.getBranchLeafMessageId());
        safe.setRoleCardId(record.getRoleCardId());
        safe.setRunProfileId(record.getRunProfileId());
        safe.setExecutorEngine(record.getExecutorEngine());
        safe.setExecutorConfigJson(safeJsonText(record.getExecutorConfigJson()));
        safe.setTraceContextJson(safeJsonText(record.getTraceContextJson()));
        safe.setSnapshotJson(safeJsonText(record.getSnapshotJson()));
        safe.setCreateTime(record.getCreateTime());
        safe.setDeleted(record.getDeleted());
        return safe;
    }

    private String safeJsonText(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return objectMapper.writeValueAsString(safeJsonValue(null, parsed));
        } catch (JsonProcessingException ignored) {
            return safeText(text);
        }
    }

    private Object safeJsonValue(String key, Object value) {
        if (key != null && CredentialJsonFieldClassifier.isSensitiveOutputField(key)) {
            return CredentialTextRedactor.REDACTED_VALUE;
        }
        if (value instanceof String text) {
            return safeText(text);
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                String safeKey = nestedKey == null ? null : String.valueOf(nestedKey);
                safe.put(safeKey, safeJsonValue(safeKey, nestedValue));
            });
            return safe;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> safeJsonValue(null, item))
                    .toList();
        }
        return value;
    }

    private String safeText(String value) {
        return CredentialTextRedactor.redact(value);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
