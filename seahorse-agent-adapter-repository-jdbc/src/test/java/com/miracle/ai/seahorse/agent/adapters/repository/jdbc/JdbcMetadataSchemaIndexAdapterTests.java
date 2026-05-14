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

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMetadataSchemaIndexAdapterTests {

    @Test
    void shouldBuildJsonGinIndexForJsonPolicy() {
        JdbcMetadataSchemaIndexAdapter adapter = new JdbcMetadataSchemaIndexAdapter(dataSource("json-gin"));

        String sql = adapter.indexSql(field("department", MetadataIndexPolicy.JSON_GIN));

        assertThat(sql).isEqualTo(
                "CREATE INDEX IF NOT EXISTS idx_kc_metadata_json_gin ON t_knowledge_chunk USING GIN (metadata_json)");
    }

    @Test
    void shouldBuildExpressionIndexForFilterableField() {
        JdbcMetadataSchemaIndexAdapter adapter = new JdbcMetadataSchemaIndexAdapter(dataSource("expression"));

        String sql = adapter.indexSql(field("department", MetadataIndexPolicy.SEARCH_KEYWORD));

        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_kc_meta_department_");
        assertThat(sql).contains("ON t_knowledge_chunk ((metadata_json->>'department'))");
    }

    @Test
    void shouldSkipSyncWhenMetadataColumnMissing() {
        DriverManagerDataSource dataSource = dataSource("missing-column");
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE t_knowledge_chunk (
                    id VARCHAR(64),
                    content VARCHAR(2048)
                )
                """);
        JdbcMetadataSchemaIndexAdapter adapter = new JdbcMetadataSchemaIndexAdapter(dataSource);

        assertThatCode(() -> adapter.syncField(field("department", MetadataIndexPolicy.SEARCH_KEYWORD)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsafeFieldKeyBeforeBuildingSql() {
        JdbcMetadataSchemaIndexAdapter adapter = new JdbcMetadataSchemaIndexAdapter(dataSource("unsafe-key"));

        assertThatThrownBy(() -> adapter.indexSql(field("department;drop", MetadataIndexPolicy.SEARCH_KEYWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid metadata field key");
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-schema-index-" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private MetadataSchemaFieldRecord field(String fieldKey, MetadataIndexPolicy policy) {
        Instant now = Instant.parse("2026-05-14T00:00:00Z");
        return new MetadataSchemaFieldRecord(
                "field-1",
                "tenant-1",
                "kb-1",
                fieldKey,
                fieldKey,
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ),
                false,
                true,
                false,
                false,
                !MetadataIndexPolicy.NONE.equals(policy),
                policy,
                0.8D,
                Set.of("source"),
                Map.of(),
                new BackendFieldMapping(fieldKey, "", "", fieldKey, false, true, false, Map.of()),
                1,
                now,
                now);
    }
}
