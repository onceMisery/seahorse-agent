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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    void shouldBuildTypedExpressionIndexForNumberField() {
        JdbcMetadataSchemaIndexAdapter adapter = new JdbcMetadataSchemaIndexAdapter(dataSource("number-expression"));

        String sql = adapter.indexSql(field("amount", MetadataIndexPolicy.EXPRESSION_INDEX, MetadataValueType.NUMBER));

        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_kc_meta_amount_");
        assertThat(sql).contains("CAST(metadata_json->>'amount' AS NUMERIC)");
    }

    @Test
    void shouldKeepDateTimeExpressionIndexTextComparable() {
        JdbcMetadataSchemaIndexAdapter adapter = new JdbcMetadataSchemaIndexAdapter(dataSource("datetime-expression"));

        String sql = adapter.indexSql(field("effectiveAt", MetadataIndexPolicy.EXPRESSION_INDEX,
                MetadataValueType.DATE_TIME));

        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_kc_meta_effectiveat_");
        assertThat(sql).contains("ON t_knowledge_chunk ((metadata_json->>'effectiveAt'))");
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

    @Test
    void shouldDropOldIndexWhenFieldBecomesGuardOnly() {
        DriverManagerDataSource dataSource = dataSource("guard-only");
        createChunkTable(dataSource);
        CapturingJdbcMetadataSchemaIndexAdapter adapter = new CapturingJdbcMetadataSchemaIndexAdapter(dataSource);

        MetadataSchemaFieldRecord previous = field("department", MetadataIndexPolicy.SEARCH_KEYWORD);
        MetadataSchemaFieldRecord current = field("department", MetadataIndexPolicy.SEARCH_KEYWORD, MetadataValueType.STRING,
                true, true, true);

        adapter.syncFieldChange(previous, current);

        assertThat(adapter.executedSqls()).containsExactly(adapter.dropIndexSql(previous));
    }

    @Test
    void shouldCreateNewIndexAfterDroppingChangedExpressionIndex() {
        DriverManagerDataSource dataSource = dataSource("field-rename");
        createChunkTable(dataSource);
        CapturingJdbcMetadataSchemaIndexAdapter adapter = new CapturingJdbcMetadataSchemaIndexAdapter(dataSource);

        MetadataSchemaFieldRecord previous = field("department", MetadataIndexPolicy.SEARCH_KEYWORD);
        MetadataSchemaFieldRecord current = field("region", MetadataIndexPolicy.SEARCH_KEYWORD);

        adapter.syncFieldChange(previous, current);

        assertThat(adapter.executedSqls()).containsExactly(
                adapter.dropIndexSql(previous),
                adapter.indexSql(current));
    }

    @Test
    void shouldSkipDroppingSharedGinIndexWhenFieldDeleted() {
        DriverManagerDataSource dataSource = dataSource("json-gin-delete");
        createChunkTable(dataSource);
        CapturingJdbcMetadataSchemaIndexAdapter adapter = new CapturingJdbcMetadataSchemaIndexAdapter(dataSource);

        adapter.deleteField(field("department", MetadataIndexPolicy.JSON_GIN));

        assertThat(adapter.executedSqls()).isEmpty();
    }

    @Test
    void shouldRecordFailedObservationWhenIndexSyncSqlFails() {
        DriverManagerDataSource dataSource = dataSource("sync-failed-observation");
        createChunkTable(dataSource);
        RecordingObservationPort observationPort = new RecordingObservationPort();
        RecordingSchemaIndexStatusPort statusPort = new RecordingSchemaIndexStatusPort();
        FailingJdbcMetadataSchemaIndexAdapter adapter =
                new FailingJdbcMetadataSchemaIndexAdapter(dataSource, observationPort, statusPort);

        assertThatCode(() -> adapter.syncField(field("department", MetadataIndexPolicy.SEARCH_KEYWORD)))
                .doesNotThrowAnyException();

        assertThat(observationPort.events()).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("metadata.schema.index.sync.failed");
            assertThat(event.attributes()).containsEntry("backend", "jdbc");
            assertThat(event.attributes()).containsEntry("action", "CREATE");
            assertThat(event.attributes()).containsEntry("fieldKey", "department");
            assertThat(event.attributes()).containsEntry("errorType", "IllegalStateException");
            assertThat(event.attributes()).containsEntry("errorMessage", "ddl failed");
        });
        assertThat(statusPort.statuses()).singleElement().satisfies(status -> {
            assertThat(status.backend()).isEqualTo("jdbc");
            assertThat(status.action()).isEqualTo("CREATE");
            assertThat(status.outcome()).isEqualTo("FAILED");
        });
    }

    private void createChunkTable(DriverManagerDataSource dataSource) {
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE t_knowledge_chunk (
                    id VARCHAR(64),
                    metadata_json VARCHAR(4096)
                )
                """);
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-schema-index-" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private MetadataSchemaFieldRecord field(String fieldKey, MetadataIndexPolicy policy) {
        return field(fieldKey, policy, MetadataValueType.STRING);
    }

    private MetadataSchemaFieldRecord field(String fieldKey, MetadataIndexPolicy policy, MetadataValueType valueType) {
        return field(fieldKey, policy, valueType, true, true, false);
    }

    private MetadataSchemaFieldRecord field(String fieldKey,
                                            MetadataIndexPolicy policy,
                                            MetadataValueType valueType,
                                            boolean indexed,
                                            boolean pushdownToKeyword,
                                            boolean guardOnly) {
        Instant now = Instant.parse("2026-05-14T00:00:00Z");
        return new MetadataSchemaFieldRecord(
                "field-1",
                "tenant-1",
                "kb-1",
                fieldKey,
                fieldKey,
                valueType,
                Set.of(MetadataOperator.EQ),
                false,
                true,
                false,
                false,
                indexed && !MetadataIndexPolicy.NONE.equals(policy),
                policy,
                0.8D,
                Set.of("source"),
                Map.of(),
                new BackendFieldMapping(fieldKey, "", "", fieldKey, false, pushdownToKeyword, guardOnly, Map.of()),
                1,
                now,
                now);
    }

    private static final class CapturingJdbcMetadataSchemaIndexAdapter extends JdbcMetadataSchemaIndexAdapter {

        private final List<String> executedSqls = new ArrayList<>();

        private CapturingJdbcMetadataSchemaIndexAdapter(DriverManagerDataSource dataSource) {
            super(dataSource);
        }

        @Override
        void executeSql(String sql) {
            executedSqls.add(sql);
        }

        List<String> executedSqls() {
            return executedSqls;
        }
    }

    private static final class FailingJdbcMetadataSchemaIndexAdapter extends JdbcMetadataSchemaIndexAdapter {

        private FailingJdbcMetadataSchemaIndexAdapter(DriverManagerDataSource dataSource,
                                                      ObservationPort observationPort,
                                                      MetadataSchemaIndexStatusPort statusPort) {
            super(dataSource, observationPort, statusPort);
        }

        @Override
        void executeSql(String sql) {
            throw new IllegalStateException("ddl failed");
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new RecordingObservationScope(events);
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }

        List<ObservationEvent> events() {
            return events;
        }
    }

    private record RecordingObservationScope(List<ObservationEvent> events) implements ObservationScope {

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingSchemaIndexStatusPort implements MetadataSchemaIndexStatusPort {

        private final List<MetadataSchemaIndexSyncStatusRecord> statuses = new ArrayList<>();

        @Override
        public void recordSyncResult(MetadataSchemaIndexSyncStatusRecord status) {
            statuses.add(status);
        }

        List<MetadataSchemaIndexSyncStatusRecord> statuses() {
            return statuses;
        }
    }
}
