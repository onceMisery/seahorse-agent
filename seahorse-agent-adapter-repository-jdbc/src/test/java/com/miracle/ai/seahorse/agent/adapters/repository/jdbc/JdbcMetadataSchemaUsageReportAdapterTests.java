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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataSchemaUsageReportAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbcTemplate;
    private MetadataSchemaUsageReportRepositoryPort usageReportRepositoryPort;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-schema-usage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        usageReportRepositoryPort = new JdbcMetadataSchemaUsageReportRepositoryAdapter(
                dataSource, new JdbcMetadataGovernanceRepositoryAdapter(dataSource, objectMapper));
    }

    @Test
    void shouldRecordSchemaUsageAndBuildAggregatedReport() {
        insertField("field-1", "department", "部门");
        insertField("field-2", "owner", "负责人");
        insertField("field-3", "securityLevel", "密级");

        usageReportRepositoryPort.recordCompiled(
                "tenant-1", "kb-1", 2, List.of("department", "owner"), List.of("owner"));
        usageReportRepositoryPort.recordCompiled(
                "tenant-1", "kb-1", 2, List.of("department"), List.of());
        usageReportRepositoryPort.recordRejected(
                "tenant-1", "kb-1", 2, List.of("owner"), "UNREGISTERED_FIELD");
        usageReportRepositoryPort.recordCompiled(
                "tenant-2", "kb-2", 2, List.of("department"), List.of("department"));

        MetadataSchemaUsageReport report = usageReportRepositoryPort.report("tenant-1", "kb-1", 2);

        assertThat(report.schemaVersion()).isEqualTo(2);
        assertThat(report.totalCompiledRequests()).isEqualTo(2L);
        assertThat(report.totalRejectedRequests()).isEqualTo(1L);
        assertThat(report.guardOnlyRequestCount()).isEqualTo(1L);
        assertThat(report.guardOnlyRate()).isEqualTo(0.5D);
        assertThat(report.rejectedRate()).isEqualTo(1D / 3D);
        assertThat(field(report, "department")).extracting(
                MetadataSchemaUsageFieldRecord::usageCount,
                MetadataSchemaUsageFieldRecord::guardOnlyCount,
                MetadataSchemaUsageFieldRecord::rejectedCount)
                .containsExactly(2L, 0L, 0L);
        assertThat(field(report, "owner")).extracting(
                MetadataSchemaUsageFieldRecord::usageCount,
                MetadataSchemaUsageFieldRecord::guardOnlyCount,
                MetadataSchemaUsageFieldRecord::rejectedCount,
                MetadataSchemaUsageFieldRecord::guardOnlyRate,
                MetadataSchemaUsageFieldRecord::rejectedRate)
                .containsExactly(1L, 1L, 1L, 1D, 0.5D);
        assertThat(field(report, "securityLevel")).extracting(
                MetadataSchemaUsageFieldRecord::usageCount,
                MetadataSchemaUsageFieldRecord::guardOnlyCount,
                MetadataSchemaUsageFieldRecord::rejectedCount)
                .containsExactly(0L, 0L, 0L);
    }

    @Test
    void shouldFilterUsageReportBySchemaVersion() {
        insertField("field-1", "department", "部门");
        insertField("field-2", "owner", "负责人");

        usageReportRepositoryPort.recordCompiled(
                "tenant-1", "kb-1", 1, List.of("department"), List.of());
        usageReportRepositoryPort.recordCompiled(
                "tenant-1", "kb-1", 2, List.of("department", "owner"), List.of("owner"));
        usageReportRepositoryPort.recordRejected(
                "tenant-1", "kb-1", 2, List.of("owner"), "NOT_FILTERABLE");

        MetadataSchemaUsageReport reportV1 = usageReportRepositoryPort.report("tenant-1", "kb-1", 1);
        MetadataSchemaUsageReport reportV2 = usageReportRepositoryPort.report("tenant-1", "kb-1", 2);

        assertThat(reportV1.totalCompiledRequests()).isEqualTo(1L);
        assertThat(reportV1.totalRejectedRequests()).isZero();
        assertThat(field(reportV1, "department").usageCount()).isEqualTo(1L);
        assertThat(field(reportV1, "owner").usageCount()).isZero();

        assertThat(reportV2.totalCompiledRequests()).isEqualTo(1L);
        assertThat(reportV2.totalRejectedRequests()).isEqualTo(1L);
        assertThat(reportV2.guardOnlyRequestCount()).isEqualTo(1L);
        assertThat(field(reportV2, "department").usageCount()).isEqualTo(1L);
        assertThat(field(reportV2, "owner").guardOnlyCount()).isEqualTo(1L);
        assertThat(field(reportV2, "owner").rejectedCount()).isEqualTo(1L);
    }

    private MetadataSchemaUsageFieldRecord field(MetadataSchemaUsageReport report, String fieldKey) {
        return report.fields().stream()
                .filter(item -> fieldKey.equals(item.fieldKey()))
                .findFirst()
                .orElseThrow();
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_schema_usage_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_field_schema");
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_field_schema (
                    id VARCHAR(32) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    field_key VARCHAR(128) NOT NULL,
                    display_name VARCHAR(128),
                    value_type VARCHAR(32) NOT NULL,
                    allowed_ops VARCHAR(512) NOT NULL,
                    required SMALLINT NOT NULL DEFAULT 0,
                    filterable SMALLINT NOT NULL DEFAULT 0,
                    sortable SMALLINT NOT NULL DEFAULT 0,
                    facetable SMALLINT NOT NULL DEFAULT 0,
                    indexed SMALLINT NOT NULL DEFAULT 0,
                    index_policy VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    min_confidence DOUBLE NOT NULL DEFAULT 0.8,
                    trusted_sources VARCHAR(512),
                    extraction_hints VARCHAR(512),
                    backend_mapping VARCHAR(512),
                    schema_version INTEGER NOT NULL DEFAULT 1,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_schema_usage_log (
                    id VARCHAR(64) PRIMARY KEY,
                    request_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64) NOT NULL,
                    schema_version INTEGER NOT NULL DEFAULT 1,
                    field_key VARCHAR(128) NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    guard_only SMALLINT NOT NULL DEFAULT 0,
                    reject_reason VARCHAR(64),
                    create_time TIMESTAMP NOT NULL
                )
                """);
    }

    private void insertField(String id, String fieldKey, String displayName) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_field_schema(
                    id, tenant_id, kb_id, field_key, display_name, value_type, allowed_ops,
                    required, filterable, sortable, facetable, indexed, index_policy,
                    min_confidence, trusted_sources, extraction_hints, backend_mapping,
                    schema_version, create_time, update_time, deleted
                ) VALUES (?, 'tenant-1', 'kb-1', ?, ?, 'STRING', '[\"EQ\"]',
                          0, 1, 0, 0, 1, 'JSON_GIN',
                          0.8, '[]', '{}', '{}',
                          2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, fieldKey, displayName);
    }
}
