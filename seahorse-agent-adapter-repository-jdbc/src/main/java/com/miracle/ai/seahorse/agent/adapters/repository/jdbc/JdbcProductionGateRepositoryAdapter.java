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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcProductionGateRepositoryAdapter implements ProductionGateRepositoryPort {

    private static final TypeReference<List<GateItemRow>> ITEM_LIST_TYPE = new TypeReference<>() {
    };
    private static final String REPORT_COLUMNS = """
            report_id, agent_id, version_id, status, result_json, checked_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_production_gate_report
            (report_id, agent_id, version_id, status, result_json, checked_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_LATEST = """
            SELECT %s
            FROM sa_production_gate_report
            WHERE agent_id = ?
            ORDER BY checked_at DESC, report_id DESC
            LIMIT 1
            """.formatted(REPORT_COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcProductionGateRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcProductionGateRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public ProductionGateReport save(ProductionGateReport report) {
        ProductionGateReport safeReport = Objects.requireNonNull(report, "report must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeReport.reportId(),
                safeReport.agentId(),
                safeReport.versionId(),
                safeReport.status().name(),
                writeItems(safeReport.items()),
                toTimestamp(safeReport.checkedAt()));
        return safeReport;
    }

    @Override
    public Optional<ProductionGateReport> latest(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_LATEST, this::mapReport, agentId.trim()).stream().findFirst();
    }

    private ProductionGateReport mapReport(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProductionGateReport(
                resultSet.getString("report_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                ProductionGateStatus.valueOf(resultSet.getString("status")),
                readItems(resultSet.getString("result_json")),
                toInstant(resultSet.getTimestamp("checked_at")));
    }

    private String writeItems(List<ProductionGateCheckItem> items) {
        try {
            List<GateItemRow> rows = items.stream()
                    .map(GateItemRow::from)
                    .toList();
            return objectMapper.writeValueAsString(rows);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot write production gate items", ex);
        }
    }

    private List<ProductionGateCheckItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, ITEM_LIST_TYPE).stream()
                    .map(GateItemRow::toDomain)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read production gate items", ex);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record GateItemRow(String code, String status, String message) {

        private static GateItemRow from(ProductionGateCheckItem item) {
            return new GateItemRow(item.code().name(), item.status().name(), item.message());
        }

        private ProductionGateCheckItem toDomain() {
            return new ProductionGateCheckItem(
                    ProductionGateCheckCode.valueOf(code),
                    ProductionGateStatus.valueOf(status),
                    message);
        }
    }
}
