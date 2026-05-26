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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.EnterprisePilotReadinessRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcEnterprisePilotReadinessRepositoryAdapter implements EnterprisePilotReadinessRepositoryPort {

    private static final TypeReference<List<CheckResultRow>> CHECK_RESULT_LIST_TYPE = new TypeReference<>() {
    };
    private static final String REPORT_COLUMNS = """
            report_id, tenant_id, agent_id, version_id, status, check_results_json, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_enterprise_pilot_readiness_report
            (report_id, tenant_id, agent_id, version_id, status, check_results_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_LATEST = """
            SELECT %s
            FROM sa_enterprise_pilot_readiness_report
            WHERE tenant_id = ?
              AND agent_id = ?
              AND version_id = ?
            ORDER BY created_at DESC, report_id DESC
            LIMIT 1
            """.formatted(REPORT_COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEnterprisePilotReadinessRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcEnterprisePilotReadinessRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public EnterprisePilotReadinessReport save(EnterprisePilotReadinessReport report) {
        EnterprisePilotReadinessReport safeReport = Objects.requireNonNull(report, "report must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeReport.reportId(),
                safeReport.tenantId(),
                safeReport.agentId(),
                safeReport.versionId(),
                safeReport.status().name(),
                writeCheckResults(safeReport.checkResults()),
                toTimestamp(safeReport.createdAt()));
        return safeReport;
    }

    @Override
    public Optional<EnterprisePilotReadinessReport> findLatest(String tenantId, String agentId, String versionId) {
        if (!hasText(tenantId) || !hasText(agentId) || !hasText(versionId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        SQL_FIND_LATEST,
                        this::mapReport,
                        tenantId.trim(),
                        agentId.trim(),
                        versionId.trim())
                .stream()
                .findFirst();
    }

    private EnterprisePilotReadinessReport mapReport(ResultSet resultSet, int rowNum) throws SQLException {
        return new EnterprisePilotReadinessReport(
                resultSet.getString("report_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                EnterprisePilotReadinessStatus.valueOf(resultSet.getString("status")),
                readCheckResults(resultSet.getString("check_results_json")),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private String writeCheckResults(List<EnterprisePilotReadinessCheckResult> checkResults) {
        try {
            List<CheckResultRow> rows = checkResults.stream()
                    .map(CheckResultRow::from)
                    .toList();
            return objectMapper.writeValueAsString(rows);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot write readiness check results", ex);
        }
    }

    private List<EnterprisePilotReadinessCheckResult> readCheckResults(String json) {
        try {
            return objectMapper.readValue(json, CHECK_RESULT_LIST_TYPE).stream()
                    .map(CheckResultRow::toDomain)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read readiness check results", ex);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CheckResultRow(String code,
                                  String status,
                                  String reasonCode,
                                  String evidenceRef,
                                  String message,
                                  String checkedAt) {

        private static CheckResultRow from(EnterprisePilotReadinessCheckResult result) {
            return new CheckResultRow(
                    result.code().name(),
                    result.status().name(),
                    result.reasonCode().name(),
                    result.evidenceRef(),
                    result.message(),
                    result.checkedAt().toString());
        }

        private EnterprisePilotReadinessCheckResult toDomain() {
            return new EnterprisePilotReadinessCheckResult(
                    EnterprisePilotReadinessCheckCode.valueOf(code),
                    EnterprisePilotReadinessStatus.valueOf(status),
                    EnterprisePilotReadinessReasonCode.valueOf(reasonCode),
                    evidenceRef,
                    message,
                    Instant.parse(checkedAt));
        }
    }
}
