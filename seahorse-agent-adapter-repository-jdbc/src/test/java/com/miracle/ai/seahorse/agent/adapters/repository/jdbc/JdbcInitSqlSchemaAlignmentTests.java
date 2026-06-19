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

class JdbcInitSqlSchemaAlignmentTests {

    @Test
    void shouldIncludeAgentExtensionStatusSchemaInInitSql() throws Exception {
        String initSql = initSql();

        assertThat(initSql).contains("CREATE TABLE IF NOT EXISTS t_agent_extension_status");
        assertThat(initSql).contains("id BIGSERIAL PRIMARY KEY");
        assertThat(initSql).contains("extension_name VARCHAR(128) NOT NULL");
        assertThat(initSql).contains("port_type VARCHAR(256) NOT NULL");
        assertThat(initSql).contains("CONSTRAINT uk_agent_extension_status UNIQUE (extension_name, port_type)");
    }

    @Test
    void shouldKeepPgVectorInitTableAlignedWithAdapterSchema() throws Exception {
        String initSql = initSql();

        assertThat(initSql).contains("CREATE TABLE t_knowledge_vector");
        assertThat(initSql).contains("id          VARCHAR(128) PRIMARY KEY");
        assertThat(initSql).contains("content     TEXT NOT NULL");
        assertThat(initSql).contains("metadata    JSONB NOT NULL");
        assertThat(initSql).contains("embedding   vector(768) NOT NULL");
    }

    private String initSql() throws Exception {
        return Files.readString(Path.of("..", "resources", "database", "seahorse_init.sql"));
    }
}
