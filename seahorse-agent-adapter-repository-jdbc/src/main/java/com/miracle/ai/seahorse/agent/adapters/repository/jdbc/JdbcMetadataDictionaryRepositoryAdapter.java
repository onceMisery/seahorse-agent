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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcMetadataDictionaryRepositoryAdapter implements MetadataDictionaryPort,
        MetadataDictionaryManagementRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMetadataDictionaryRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Optional<String> canonicalValue(String tenantId, String dictionaryCode, String rawValue) {
        if (blank(dictionaryCode) || rawValue == null) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT canonical_value
                    FROM t_metadata_dictionary_item
                    WHERE tenant_id = ? AND dict_code = ? AND raw_value = ? AND enabled = 1
                    ORDER BY update_time DESC
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getString("canonical_value"),
                    Objects.requireNonNullElse(tenantId, ""), dictionaryCode, rawValue)
                    .stream()
                    .findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<MetadataDictionaryItemRecord> listDictionaryItems(String tenantId,
                                                                  String dictionaryCode,
                                                                  boolean includeDisabled) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeDictionaryCode = Objects.requireNonNullElse(dictionaryCode, "");
        if (blank(safeTenantId)) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, dict_code, raw_value, canonical_value, display_name,
                           enabled, create_time, update_time
                    FROM t_metadata_dictionary_item
                    WHERE tenant_id = ?
                      AND (? = '' OR dict_code = ?)
                      AND (? = 1 OR enabled = 1)
                    ORDER BY dict_code, raw_value, update_time DESC
                    """, this::toDictionaryItemRecord,
                    safeTenantId, safeDictionaryCode, safeDictionaryCode, flag(includeDisabled));
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public Optional<MetadataDictionaryItemRecord> findDictionaryItem(String itemId) {
        if (blank(itemId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, dict_code, raw_value, canonical_value, display_name,
                           enabled, create_time, update_time
                    FROM t_metadata_dictionary_item
                    WHERE id = ?
                    """, this::toDictionaryItemRecord, itemId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public String createDictionaryItem(MetadataDictionaryItemPayload payload) {
        MetadataDictionaryItemPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String itemId = SnowflakeIds.nextIdString();
        jdbcTemplate.update("""
                INSERT INTO t_metadata_dictionary_item(
                    id, tenant_id, dict_code, raw_value, canonical_value, display_name,
                    enabled, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, itemId, safePayload.tenantId(), safePayload.dictionaryCode(), safePayload.rawValue(),
                safePayload.canonicalValue(), safePayload.displayName(), flag(safePayload.enabled()));
        return itemId;
    }

    @Override
    public MetadataDictionaryItemRecord updateDictionaryItem(String itemId, MetadataDictionaryItemPayload payload) {
        MetadataDictionaryItemPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_dictionary_item
                SET tenant_id = ?,
                    dict_code = ?,
                    raw_value = ?,
                    canonical_value = ?,
                    display_name = ?,
                    enabled = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, safePayload.tenantId(), safePayload.dictionaryCode(), safePayload.rawValue(),
                safePayload.canonicalValue(), safePayload.displayName(), flag(safePayload.enabled()), itemId);
        if (updated <= 0) {
            throw new IllegalArgumentException("Metadata Dictionary item not found: " + itemId);
        }
        return findDictionaryItem(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Metadata Dictionary item not found: " + itemId));
    }

    @Override
    public boolean disableDictionaryItem(String itemId) {
        if (blank(itemId)) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE t_metadata_dictionary_item
                SET enabled = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND enabled = 1
                """, itemId) > 0;
    }

    private MetadataDictionaryItemRecord toDictionaryItemRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataDictionaryItemRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("dict_code"),
                rs.getString("raw_value"),
                rs.getString("canonical_value"),
                rs.getString("display_name"),
                bool(rs, "enabled"),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column) == 1;
    }

    private int flag(boolean value) {
        return value ? 1 : 0;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
