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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDockerInitScriptMountTests {

    private static final List<String> REQUIRED_SQL_SCRIPTS = List.of(
            "agent-registry-run-store-postgresql.sql",
            "eval-dataset-postgresql.sql",
            "metadata-governance-postgresql.sql",
            "retrieval-governance-postgresql.sql");

    @Test
    void shouldMountAllJdbcSqlResourcesInDockerComposeFiles() throws Exception {
        assertComposeMountsSqlResources(Path.of("..", "docker-compose.yml"));
        assertComposeMountsSqlResources(Path.of("..", "docker-compose.full.yml"));
    }

    private void assertComposeMountsSqlResources(Path composePath) throws Exception {
        String compose = Files.readString(composePath);
        assertThat(compose).contains("resources/database/seahorse_init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro");
        for (String script : REQUIRED_SQL_SCRIPTS) {
            assertThat(compose)
                    .as(composePath + " should mount " + script)
                    .contains(script + ":/docker-entrypoint-initdb.d/");
        }
    }
}
