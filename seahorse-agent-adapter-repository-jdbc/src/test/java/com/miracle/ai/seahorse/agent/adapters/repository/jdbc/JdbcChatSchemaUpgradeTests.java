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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcChatSchemaUpgradeTests {

    @Test
    void shouldUpgradeEmptyDatabaseWithoutLayeredMemoryTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:chat-schema-upgrade-empty;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        new JdbcChatSchemaUpgrade(dataSource).upgrade();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(tableExists(jdbcTemplate, "t_user_profile_fact")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_correction_ledger")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_operation_log")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_outbox")).isTrue();
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        return !jdbcTemplate.query(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE lower(table_name) = lower(?)
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        tableName)
                .isEmpty();
    }
}
