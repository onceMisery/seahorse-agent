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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * PostgreSQL Metadata Schema 索引同步适配器。
 *
 * <p>该 adapter 只根据已注册 Schema 生成固定 DDL，避免动态 metadata 字段绕过治理后直接拼接 SQL。
 */
public class JdbcMetadataSchemaIndexAdapter implements MetadataSchemaIndexSyncPort {

    private static final String CHUNK_TABLE = "t_knowledge_chunk";
    private static final String METADATA_COLUMN = "metadata_json";

    private final JdbcTemplate jdbcTemplate;
    private Boolean metadataColumnExists;

    public JdbcMetadataSchemaIndexAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void syncField(MetadataSchemaFieldRecord field) {
        MetadataSchemaFieldRecord safeField = Objects.requireNonNull(field, "field must not be null");
        if (!shouldSync(safeField) || !metadataColumnExists()) {
            return;
        }
        jdbcTemplate.execute(indexSql(safeField));
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

    private boolean shouldSync(MetadataSchemaFieldRecord field) {
        if (!field.indexed()) {
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

    private String expressionIndexName(String key, MetadataIndexPolicy policy, MetadataValueType valueType) {
        Object hashScope = isTextLike(valueType) ? policy : Objects.hash(policy, valueType);
        String rawName = "idx_kc_meta_" + key + "_" + Integer.toHexString(Objects.hash(key, hashScope));
        return safeIdentifier(rawName);
    }

    private boolean isTextLike(MetadataValueType valueType) {
        MetadataValueType safeType = Objects.requireNonNullElse(valueType, MetadataValueType.STRING);
        return switch (safeType) {
            case STRING, STRING_ARRAY, ENUM -> true;
            default -> false;
        };
    }

    private String safeIdentifier(String value) {
        String safe = Objects.requireNonNullElse(value, "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        if (safe.length() > 63) {
            return safe.substring(0, 63);
        }
        return safe;
    }
}
