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

class JdbcQuotaSchemaAlignmentTests {

    @Test
    void shouldKeepInitSqlAlignedWithQuotaSchema() throws Exception {
        String initSql = Files.readString(Path.of("..", "resources", "database", "seahorse_init.sql"));
        String registrySql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "META-INF",
                "seahorse-agent",
                "sql",
                "agent-registry-run-store-postgresql.sql"));

        assertQuotaSchema(registrySql);
        assertQuotaSchema(initSql);
    }

    private void assertQuotaSchema(String sql) {
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sa_quota_policy");
        assertThat(sql).contains("policy_id VARCHAR(64) PRIMARY KEY");
        assertThat(sql).contains("scope VARCHAR(32) NOT NULL");
        assertThat(sql).contains("subject_id VARCHAR(128) NOT NULL");
        assertThat(sql).contains("token_limit BIGINT");
        assertThat(sql).contains("call_limit BIGINT");
        assertThat(sql).contains("cost_limit DOUBLE PRECISION");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_sa_quota_policy_active");
        assertThat(sql).contains("ON sa_quota_policy(tenant_id, scope, subject_id, status, updated_at DESC, policy_id DESC)");

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sa_cost_usage_record");
        assertThat(sql).contains("usage_id VARCHAR(64) PRIMARY KEY");
        assertThat(sql).contains("source VARCHAR(32) NOT NULL");
        assertThat(sql).contains("tokens BIGINT NOT NULL");
        assertThat(sql).contains("calls BIGINT NOT NULL");
        assertThat(sql).contains("cost DOUBLE PRECISION NOT NULL");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_sa_cost_usage_aggregate");
        assertThat(sql).contains("ON sa_cost_usage_record(tenant_id, agent_id, run_id, created_at)");
    }
}
