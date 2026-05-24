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

import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretMetadata;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWriteCommand;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSecretStoreAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");
    private static final String AES_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void shouldStoreEncryptedSecretAndResolveBySecretRef() {
        DriverManagerDataSource dataSource = dataSource("secret-store");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSecretSchema(jdbcTemplate);
        JdbcSecretStoreAdapter adapter = new JdbcSecretStoreAdapter(
                dataSource,
                AesGcmSecretValueCipher.fromBase64Key(AES_KEY_BASE64));

        SecretMetadata metadata = adapter.putSecret(new SecretWriteCommand(
                "secret_1",
                "tenant-1",
                SecretValue.of("super-secret-token"),
                "{\"purpose\":\"mcp\"}",
                NOW));
        Optional<SecretValue> resolved = adapter.getSecret("secret_1");
        String storedValue = jdbcTemplate.queryForObject(
                "SELECT encrypted_value FROM sa_secret_ref WHERE secret_ref = ?",
                String.class,
                "secret_1");

        assertThat(metadata.secretRef()).isEqualTo("secret_1");
        assertThat(metadata.tenantId()).isEqualTo("tenant-1");
        assertThat(metadata.metadataJson()).isEqualTo("{\"purpose\":\"mcp\"}");
        assertThat(metadata.createdAt()).isEqualTo(NOW);
        assertThat(metadata.rotatedAt()).isNull();
        assertThat(storedValue).isNotEqualTo("super-secret-token");
        assertThat(storedValue).doesNotContain("super-secret-token");
        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().reveal()).isEqualTo("super-secret-token");
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    static void createSecretSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_secret_ref (
                    secret_ref VARCHAR(128) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    encrypted_value CLOB NOT NULL,
                    metadata_json CLOB,
                    created_at TIMESTAMP NOT NULL,
                    rotated_at TIMESTAMP
                )
                """);
    }
}
