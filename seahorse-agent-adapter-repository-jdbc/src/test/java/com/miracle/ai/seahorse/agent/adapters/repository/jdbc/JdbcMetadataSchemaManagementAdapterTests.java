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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldCapabilityRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataSchemaManagementAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcMetadataSchemaRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-schema-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcMetadataSchemaRepositoryAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldCreateUpdateListAndDeleteSchemaFields() {
        String id = adapter.createSchemaField(payload("department", "部门"));

        List<MetadataSchemaFieldRecord> fields = adapter.listSchemaFields("tenant-1", "kb-1");
        MetadataSchema schema = adapter.loadSchema("tenant-1", "kb-1");

        assertThat(fields).extracting(MetadataSchemaFieldRecord::fieldKey).containsExactly("department");
        assertThat(schema.find("department")).isPresent();

        MetadataSchemaFieldRecord updated = adapter.updateSchemaField(id, payload("department", "所属部门"));

        assertThat(updated.displayName()).isEqualTo("所属部门");
        assertThat(updated.allowedOperators()).containsExactlyInAnyOrder(MetadataOperator.EQ, MetadataOperator.IN);
        assertThat(updated.backendMapping().pushdownToKeyword()).isTrue();

        boolean deleted = adapter.deleteSchemaField(id);

        assertThat(deleted).isTrue();
        assertThat(adapter.listSchemaFields("tenant-1", "kb-1")).isEmpty();
    }

    @Test
    void shouldExposeFieldCapabilitiesWithLastSyncResult() {
        String id = adapter.createSchemaField(payload("department", "閮ㄩ棬"));
        Instant syncTime = Instant.parse("2026-05-16T00:00:00Z");
        adapter.recordSyncResult(new MetadataSchemaIndexSyncStatusRecord(
                id,
                "tenant-1",
                "kb-1",
                "department",
                2,
                "elasticsearch",
                "UPDATE",
                "FAILED",
                "IllegalStateException",
                "mapping failed",
                syncTime));

        assertThat(adapter.listSchemaFieldCapabilities("tenant-1", "kb-1"))
                .singleElement()
                .satisfies(capability -> {
                    assertThat(capability).isInstanceOf(MetadataSchemaFieldCapabilityRecord.class);
                    assertThat(capability.fieldKey()).isEqualTo("department");
                    assertThat(capability.pushdownToKeyword()).isTrue();
                    assertThat(capability.pushdownToVector()).isFalse();
                    assertThat(capability.guardOnly()).isFalse();
                    assertThat(capability.lastSyncBackend()).isEqualTo("elasticsearch");
                    assertThat(capability.lastSyncOutcome()).isEqualTo("FAILED");
                    assertThat(capability.lastSyncErrorType()).isEqualTo("IllegalStateException");
                    assertThat(capability.lastSyncTime()).isEqualTo(syncTime);
                });
    }

    private MetadataSchemaFieldPayload payload(String fieldKey, String displayName) {
        return new MetadataSchemaFieldPayload(
                "tenant-1",
                "kb-1",
                fieldKey,
                displayName,
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ, MetadataOperator.IN),
                false,
                true,
                false,
                true,
                true,
                MetadataIndexPolicy.SEARCH_KEYWORD,
                0.8D,
                Set.of("source"),
                Map.of("sourceKeys", List.of(fieldKey)),
                new BackendFieldMapping(
                        fieldKey,
                        "metadata[\"" + fieldKey + "\"]",
                        "metadata->>'" + fieldKey + "'",
                        fieldKey,
                        false,
                        true,
                        false,
                        Map.of("source", "test")),
                1);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_field_schema");
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_field_schema (
                    id VARCHAR(64) PRIMARY KEY,
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
                    min_confidence NUMERIC(5,4) NOT NULL DEFAULT 0.8000,
                    trusted_sources VARCHAR(1024),
                    extraction_hints VARCHAR(2048),
                    backend_mapping VARCHAR(2048),
                    schema_version INTEGER NOT NULL DEFAULT 1,
                    last_sync_backend VARCHAR(32),
                    last_sync_action VARCHAR(32),
                    last_sync_outcome VARCHAR(32),
                    last_sync_error_type VARCHAR(64),
                    last_sync_error_message VARCHAR(1024),
                    last_sync_time TIMESTAMP,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
    }
}
