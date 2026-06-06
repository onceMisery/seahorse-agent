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

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSecretSchemaAlignmentTests {

    @Test
    void shouldKeepInitSqlAlignedWithSecretStoreSchema() throws Exception {
        String initSql = Files.readString(Path.of("..", "resources", "database", "seahorse_init.sql"));
        String registrySql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "META-INF",
                "seahorse-agent",
                "sql",
                "agent-registry-run-store-postgresql.sql"));

        assertSecretSchema(registrySql);
        assertSecretSchema(initSql);
    }

    private void assertSecretSchema(String sql) {
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sa_secret_ref");
        assertThat(sql).contains("pk_id BIGSERIAL PRIMARY KEY");
        assertThat(sql).contains("secret_ref VARCHAR(128) NOT NULL UNIQUE");
        assertThat(sql).contains("tenant_id VARCHAR(64) NOT NULL");
        assertThat(sql).contains("encrypted_value TEXT NOT NULL");
        assertThat(sql).contains("metadata_json TEXT");
        assertThat(sql).contains("created_at TIMESTAMP NOT NULL");
        assertThat(sql).contains("rotated_at TIMESTAMP");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_sa_secret_ref_tenant");
        assertThat(sql).contains("ON sa_secret_ref(tenant_id, created_at)");
    }
}
