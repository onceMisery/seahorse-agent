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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;

public class JdbcMetadataSchemaIndexAdapter implements MetadataSchemaIndexSyncPort {

    private static final String CHUNK_TABLE = "t_knowledge_chunk";
    private static final String METADATA_COLUMN = "metadata_json";
    private static final String BACKEND = "jdbc";
    private static final String EVENT_SCHEMA_INDEX_SYNC_COMPLETED = "metadata.schema.index.sync.completed";
    private static final String EVENT_SCHEMA_INDEX_SYNC_FAILED = "metadata.schema.index.sync.failed";

    private final JdbcTemplate jdbcTemplate;
    private final ObservationPort observationPort;
    private final MetadataSchemaIndexStatusPort indexStatusPort;
    private Boolean metadataColumnExists;

    public JdbcMetadataSchemaIndexAdapter(DataSource dataSource) {
        this(dataSource, null);
    }

    public JdbcMetadataSchemaIndexAdapter(DataSource dataSource, ObservationPort observationPort) {
        this(dataSource, observationPort, null);
    }

    public JdbcMetadataSchemaIndexAdapter(DataSource dataSource,
                                          ObservationPort observationPort,
                                          MetadataSchemaIndexStatusPort indexStatusPort) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.observationPort = observationPort;
        this.indexStatusPort = indexStatusPort;
    }

    @Override
    public void syncField(MetadataSchemaFieldRecord field) {
        MetadataSchemaFieldRecord safeField = Objects.requireNonNull(field, "field must not be null");
        observeIndexSync("CREATE", safeField, () -> syncFieldInternal(safeField));
    }

    @Override
    public void syncFieldChange(MetadataSchemaFieldRecord previousField, MetadataSchemaFieldRecord currentField) {
        MetadataSchemaFieldRecord safePreviousField =
                Objects.requireNonNull(previousField, "previousField must not be null");
        MetadataSchemaFieldRecord safeCurrentField =
                Objects.requireNonNull(currentField, "currentField must not be null");
        observeIndexSync("UPDATE", safeCurrentField,
                () -> syncFieldChangeInternal(safePreviousField, safeCurrentField));
    }

    @Override
    public void deleteField(MetadataSchemaFieldRecord field) {
        MetadataSchemaFieldRecord safeField = Objects.requireNonNull(field, "field must not be null");
        observeIndexSync("DELETE", safeField, () -> deleteFieldInternal(safeField));
    }

    String indexSql(MetadataSchemaFieldRecord field) {
        MetadataIndexPolicy policy = Objects.requireNonNullElse(field.indexPolicy(), MetadataIndexPolicy.NONE);
        if (MetadataIndexPolicy.JSON_GIN.equals(policy)) {
            return "CREATE INDEX IF NOT EXISTS " + ginIndexName()
                    + " ON " + CHUNK_TABLE + " USING GIN (" + METADATA_COLUMN + ")";
        }
        String key = fieldKey(field);
        String valueExpression = JdbcMetadataSqlExpressions.comparableValueExpression(
                METADATA_COLUMN, key, field.valueType());
        return "CREATE INDEX IF NOT EXISTS " + expressionIndexName(key, policy, field.valueType())
                + " ON " + CHUNK_TABLE + " ((" + valueExpression + "))";
    }

    String dropIndexSql(MetadataSchemaFieldRecord field) {
        MetadataSchemaFieldRecord safeField = Objects.requireNonNull(field, "field must not be null");
        MetadataIndexPolicy policy = Objects.requireNonNullElse(safeField.indexPolicy(), MetadataIndexPolicy.NONE);
        if (MetadataIndexPolicy.JSON_GIN.equals(policy)) {
            return "";
        }
        return "DROP INDEX IF EXISTS " + expressionIndexName(fieldKey(safeField), policy, safeField.valueType());
    }

    void executeSql(String sql) {
        jdbcTemplate.execute(sql);
    }

    private String syncFieldInternal(MetadataSchemaFieldRecord field) {
        if (!shouldSync(field)) {
            return "SKIPPED";
        }
        if (!metadataColumnExists()) {
            return "UNSUPPORTED";
        }
        executeSql(indexSql(field));
        return "APPLIED";
    }

    private String syncFieldChangeInternal(MetadataSchemaFieldRecord previousField,
                                           MetadataSchemaFieldRecord currentField) {
        boolean previousSyncable = shouldSync(previousField);
        boolean currentSyncable = shouldSync(currentField);
        boolean sameDefinition = previousSyncable
                && currentSyncable
                && sameIndexDefinition(previousField, currentField);
        if (!metadataColumnExists()) {
            return previousSyncable || currentSyncable ? "UNSUPPORTED" : "SKIPPED";
        }
        boolean applied = false;
        if (previousSyncable && (!currentSyncable || !sameDefinition)) {
            applied = dropFieldIndex(previousField);
        }
        if (currentSyncable && !sameDefinition) {
            executeSql(indexSql(currentField));
            applied = true;
        }
        return applied ? "APPLIED" : "NO_CHANGE";
    }

    private String deleteFieldInternal(MetadataSchemaFieldRecord field) {
        if (!shouldSync(field)) {
            return "SKIPPED";
        }
        if (!metadataColumnExists()) {
            return "UNSUPPORTED";
        }
        return dropFieldIndex(field) ? "APPLIED" : "NO_CHANGE";
    }

    private boolean shouldSync(MetadataSchemaFieldRecord field) {
        if (!field.indexed()) {
            return false;
        }
        if (field.backendMapping().guardOnly() || !field.backendMapping().pushdownToKeyword()) {
            return false;
        }
        MetadataIndexPolicy policy = Objects.requireNonNullElse(field.indexPolicy(), MetadataIndexPolicy.NONE);
        return MetadataIndexPolicy.JSON_GIN.equals(policy)
                || MetadataIndexPolicy.EXPRESSION_INDEX.equals(policy)
                || MetadataIndexPolicy.SEARCH_KEYWORD.equals(policy);
    }

    private boolean metadataColumnExists() {
        if (metadataColumnExists != null) {
            return metadataColumnExists;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.columns
                    WHERE lower(table_name) = ?
                      AND lower(column_name) = ?
                    """, Integer.class, CHUNK_TABLE, METADATA_COLUMN);
            metadataColumnExists = count != null && count > 0;
        } catch (DataAccessException ex) {
            metadataColumnExists = false;
        }
        return metadataColumnExists;
    }

    private String fieldKey(MetadataSchemaFieldRecord field) {
        String canonicalName = field.backendMapping().canonicalName();
        String key = canonicalName == null || canonicalName.isBlank() ? field.fieldKey() : canonicalName;
        return JdbcMetadataSqlExpressions.safeFieldKey(key);
    }

    private String ginIndexName() {
        return "idx_kc_metadata_json_gin";
    }

    private boolean sameIndexDefinition(MetadataSchemaFieldRecord previousField, MetadataSchemaFieldRecord currentField) {
        MetadataIndexPolicy previousPolicy =
                Objects.requireNonNullElse(previousField.indexPolicy(), MetadataIndexPolicy.NONE);
        MetadataIndexPolicy currentPolicy =
                Objects.requireNonNullElse(currentField.indexPolicy(), MetadataIndexPolicy.NONE);
        if (MetadataIndexPolicy.JSON_GIN.equals(previousPolicy) && MetadataIndexPolicy.JSON_GIN.equals(currentPolicy)) {
            return true;
        }
        return expressionIndexName(fieldKey(previousField), previousPolicy, previousField.valueType())
                .equals(expressionIndexName(fieldKey(currentField), currentPolicy, currentField.valueType()));
    }

    private boolean isTextLike(MetadataValueType valueType) {
        MetadataValueType safeType = Objects.requireNonNullElse(valueType, MetadataValueType.STRING);
        return switch (safeType) {
            case STRING, STRING_ARRAY, ENUM -> true;
            default -> false;
        };
    }

    private String expressionIndexName(String key, MetadataIndexPolicy policy, MetadataValueType valueType) {
        Object hashScope = isTextLike(valueType) ? policy : Objects.hash(policy, valueType);
        String rawName = "idx_kc_meta_" + key + "_" + Integer.toHexString(Objects.hash(key, hashScope));
        return safeIdentifier(rawName);
    }

    private boolean dropFieldIndex(MetadataSchemaFieldRecord field) {
        String sql = dropIndexSql(field);
        if (sql.isBlank()) {
            return false;
        }
        executeSql(sql);
        return true;
    }

    private void observeIndexSync(String action,
                                  MetadataSchemaFieldRecord field,
                                  IndexSyncOperation operation) {
        try {
            String outcome = operation.run();
            recordSyncStatus(action, field, outcome, null);
            recordObservationEvent(EVENT_SCHEMA_INDEX_SYNC_COMPLETED, action, field, outcome, null);
        } catch (RuntimeException ex) {
            recordSyncStatus(action, field, "FAILED", ex);
            recordObservationEvent(EVENT_SCHEMA_INDEX_SYNC_FAILED, action, field, "FAILED", ex);
        }
    }

    private void recordSyncStatus(String action,
                                  MetadataSchemaFieldRecord field,
                                  String outcome,
                                  RuntimeException error) {
        if (indexStatusPort == null) {
            return;
        }
        try {
            indexStatusPort.recordSyncResult(new MetadataSchemaIndexSyncStatusRecord(
                    field.id(),
                    field.tenantId(),
                    field.knowledgeBaseId(),
                    field.fieldKey(),
                    field.schemaVersion(),
                    BACKEND,
                    Objects.requireNonNullElse(action, ""),
                    Objects.requireNonNullElse(outcome, ""),
                    error == null ? "" : error.getClass().getSimpleName(),
                    error == null ? "" : Objects.requireNonNullElse(error.getMessage(), ""),
                    null));
        } catch (RuntimeException ignored) {
        }
    }

    private void recordObservationEvent(String eventName,
                                        String action,
                                        MetadataSchemaFieldRecord field,
                                        String outcome,
                                        RuntimeException error) {
        if (observationPort == null) {
            return;
        }
        try {
            LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
            attributes.put("backend", BACKEND);
            attributes.put("action", Objects.requireNonNullElse(action, ""));
            attributes.put("tenantId", field.tenantId());
            attributes.put("knowledgeBaseId", field.knowledgeBaseId());
            attributes.put("fieldKey", field.fieldKey());
            attributes.put("schemaVersion", Integer.toString(field.schemaVersion()));
            attributes.put("indexed", Boolean.toString(field.indexed()));
            attributes.put("indexPolicy", Objects.requireNonNullElse(field.indexPolicy(), MetadataIndexPolicy.NONE).name());
            attributes.put("pushdownToKeyword", Boolean.toString(field.backendMapping().pushdownToKeyword()));
            attributes.put("pushdownToVector", Boolean.toString(field.backendMapping().pushdownToVector()));
            attributes.put("guardOnly", Boolean.toString(field.backendMapping().guardOnly()));
            attributes.put("outcome", Objects.requireNonNullElse(outcome, ""));
            if (error != null) {
                attributes.put("errorType", error.getClass().getSimpleName());
                attributes.put("errorMessage", Objects.requireNonNullElse(error.getMessage(), ""));
            }
            observationPort.recordEvent(new ObservationEvent(eventName, null, attributes));
        } catch (RuntimeException ignored) {
        }
    }

    private String safeIdentifier(String value) {
        String safe = Objects.requireNonNullElse(value, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        if (safe.length() > 63) {
            return safe.substring(0, 63);
        }
        return safe;
    }

    @FunctionalInterface
    private interface IndexSyncOperation {

        String run();
    }
}
