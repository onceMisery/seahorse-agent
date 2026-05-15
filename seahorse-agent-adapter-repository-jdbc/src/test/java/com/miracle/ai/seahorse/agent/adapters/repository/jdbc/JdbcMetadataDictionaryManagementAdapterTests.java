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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataDictionaryManagementAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcMetadataGovernanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-dictionary-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcMetadataGovernanceRepositoryAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldCreateUpdateListDisableAndCanonicalizeDictionaryItems() {
        String itemId = adapter.createDictionaryItem(payload("hr", "HR"));

        List<MetadataDictionaryItemRecord> activeItems =
                adapter.listDictionaryItems("tenant-1", "department", false);

        assertThat(activeItems).hasSize(1);
        assertThat(activeItems.get(0).rawValue()).isEqualTo("hr");
        assertThat(adapter.canonicalValue("tenant-1", "department", "hr")).contains("HR");

        MetadataDictionaryItemRecord updated =
                adapter.updateDictionaryItem(itemId, payload("hr", "HUMAN_RESOURCE"));

        assertThat(updated.canonicalValue()).isEqualTo("HUMAN_RESOURCE");
        assertThat(adapter.canonicalValue("tenant-1", "department", "hr")).contains("HUMAN_RESOURCE");

        boolean disabled = adapter.disableDictionaryItem(itemId);

        assertThat(disabled).isTrue();
        assertThat(adapter.listDictionaryItems("tenant-1", "department", false)).isEmpty();
        assertThat(adapter.listDictionaryItems("tenant-1", "department", true))
                .extracting(MetadataDictionaryItemRecord::enabled)
                .containsExactly(false);
        assertThat(adapter.canonicalValue("tenant-1", "department", "hr")).isEmpty();
    }

    private MetadataDictionaryItemPayload payload(String rawValue, String canonicalValue) {
        return new MetadataDictionaryItemPayload(
                "tenant-1",
                "department",
                rawValue,
                canonicalValue,
                canonicalValue,
                true);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_dictionary_item");
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_dictionary_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    dict_code VARCHAR(128) NOT NULL,
                    raw_value VARCHAR(256) NOT NULL,
                    canonical_value VARCHAR(256) NOT NULL,
                    display_name VARCHAR(256),
                    enabled SMALLINT NOT NULL DEFAULT 1,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
