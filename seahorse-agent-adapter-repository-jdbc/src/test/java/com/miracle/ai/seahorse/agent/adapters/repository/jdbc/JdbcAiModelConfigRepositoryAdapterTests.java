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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAiModelConfigRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcAiModelConfigRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ai-model-config-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcAiModelConfigRepositoryAdapter(dataSource);
    }

    @Test
    void shouldReadLegacyPlaintextWhenConfigIsMarkedEncrypted() {
        insertConfig("ai-config-1", "ai.api.key", "sk-legacy-plaintext", 1);

        assertThat(adapter.findAll())
                .singleElement()
                .satisfies(config -> {
                    assertThat(config.getConfigKey()).isEqualTo("ai.api.key");
                    assertThat(config.getConfigValue()).isEqualTo("sk-legacy-plaintext");
                    assertThat(config.isEncrypted()).isTrue();
                });
    }

    @Test
    void shouldEncryptUpdatedValueAfterReadingLegacyPlaintext() {
        insertConfig("ai-config-1", "ai.api.key", "sk-legacy-plaintext", 1);

        adapter.update("ai.api.key", "sk-next-value", "admin");

        String storedValue = jdbcTemplate.queryForObject(
                "SELECT config_value FROM sa_ai_model_config WHERE config_key = 'ai.api.key'",
                String.class);
        assertThat(storedValue).isNotEqualTo("sk-next-value");
        assertThat(adapter.findByKey("ai.api.key"))
                .isPresent()
                .get()
                .extracting("configValue")
                .isEqualTo("sk-next-value");
    }

    private void insertConfig(String id, String key, String value, int encrypted) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO sa_ai_model_config
                    (id, config_key, config_value, config_type, is_encrypted, description,
                     created_by, updated_by, created_at, updated_at, deleted)
                VALUES (?, ?, ?, 'STRING', ?, '', 'system', 'system', ?, ?, 0)
                """, id, key, value, encrypted, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE sa_ai_model_config (
                    id VARCHAR(64) PRIMARY KEY,
                    config_key VARCHAR(128) NOT NULL UNIQUE,
                    config_value TEXT NOT NULL,
                    config_type VARCHAR(32) NOT NULL,
                    is_encrypted SMALLINT NOT NULL DEFAULT 0,
                    description VARCHAR(512),
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
    }
}
