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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResult;
import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResultItem;
import com.miracle.ai.seahorse.agent.ports.outbound.gate.GateResultRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class JdbcGateResultRepositoryAdapter implements GateResultRepositoryPort {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<GateResultItem>> ITEM_LIST_TYPE = new TypeReference<>() {
    };
    private static final String COLUMNS = """
            subject_type, subject_id, status, passed, blocking_codes_json, items_json,
            checked_at, source_type, source_id
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_gate_result
            (gate_id, tenant_id, subject_type, subject_id, status, passed,
             blocking_codes_json, items_json, checked_at, source_type, source_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
    private static final String SQL_FIND_LATEST = """
            SELECT %s
            FROM sa_gate_result
            WHERE tenant_id = ? AND subject_type = ? AND subject_id = ?
            ORDER BY checked_at DESC, pk_id DESC
            LIMIT 1
            """.formatted(COLUMNS);
    private static final String SQL_FIND_HISTORY = """
            SELECT %s
            FROM sa_gate_result
            WHERE tenant_id = ? AND subject_type = ? AND subject_id = ?
            ORDER BY checked_at DESC, pk_id DESC
            LIMIT ?
            """.formatted(COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcGateResultRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcGateResultRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public GateResult save(GateResult result) {
        GateResult safeResult = Objects.requireNonNull(result, "result must not be null");
        jdbcTemplate.update(SQL_INSERT,
                "gr_" + SnowflakeIds.nextIdString(),
                JdbcTenantSupport.resolveTenantId(),
                normalizeSubjectType(safeResult.subjectType()),
                safeResult.subjectId(),
                safeResult.status(),
                safeResult.passed(),
                writeJson(safeResult.blockingCodes()),
                writeJson(safeResult.items()),
                toTimestamp(safeResult.checkedAt()),
                safeResult.sourceType(),
                safeResult.sourceId());
        return safeResult;
    }

    @Override
    public Optional<GateResult> latest(String subjectType, String subjectId) {
        String safeSubjectType = normalizeSubjectType(subjectType);
        String safeSubjectId = requireText(subjectId, "subjectId");
        return jdbcTemplate.query(SQL_FIND_LATEST,
                        this::mapResult,
                        JdbcTenantSupport.resolveTenantId(),
                        safeSubjectType,
                        safeSubjectId)
                .stream()
                .findFirst();
    }

    @Override
    public List<GateResult> history(String subjectType, String subjectId, int limit) {
        String safeSubjectType = normalizeSubjectType(subjectType);
        String safeSubjectId = requireText(subjectId, "subjectId");
        int safeLimit = limit < 1 ? 1 : limit;
        return jdbcTemplate.query(SQL_FIND_HISTORY,
                this::mapResult,
                JdbcTenantSupport.resolveTenantId(),
                safeSubjectType,
                safeSubjectId,
                safeLimit);
    }

    private GateResult mapResult(ResultSet resultSet, int rowNum) throws SQLException {
        return new GateResult(
                resultSet.getString("subject_type"),
                resultSet.getString("subject_id"),
                resultSet.getString("status"),
                resultSet.getBoolean("passed"),
                readJson(resultSet.getString("blocking_codes_json"), STRING_LIST_TYPE),
                readJson(resultSet.getString("items_json"), ITEM_LIST_TYPE),
                toInstant(resultSet.getTimestamp("checked_at")),
                resultSet.getString("source_type"),
                resultSet.getString("source_id"));
    }

    private <T> String writeJson(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot write gate result JSON", ex);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read gate result JSON", ex);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeSubjectType(String value) {
        return requireText(value, "subjectType").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
