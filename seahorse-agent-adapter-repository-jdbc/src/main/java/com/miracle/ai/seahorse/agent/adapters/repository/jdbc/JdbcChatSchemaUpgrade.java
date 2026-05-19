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

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * Upgrades legacy chat tables so existing Docker volumes can store modern IDs.
 */
public class JdbcChatSchemaUpgrade {

    private static final int TARGET_LENGTH = 64;

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatSchemaUpgrade(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    public void upgrade() {
        widenColumns("t_conversation", List.of("id", "conversation_id", "user_id"));
        widenColumns("t_conversation_summary", List.of("id", "conversation_id", "user_id", "last_message_id"));
        widenColumns("t_message", List.of("id", "conversation_id", "user_id"));
        widenColumns("t_message_feedback", List.of("id", "message_id", "conversation_id", "user_id"));
        widenColumns("t_rag_trace_run", List.of("id", "trace_id", "conversation_id", "task_id", "user_id"));
        widenColumns("t_rag_trace_node", List.of("id", "trace_id", "node_id", "parent_node_id"));
        widenColumns("t_short_term_memory", List.of("id", "user_id", "conversation_id"));
    }

    private void widenColumns(String tableName, List<String> columns) {
        for (String column : columns) {
            Integer currentLength = jdbcTemplate.query(
                            """
                            SELECT character_maximum_length
                            FROM information_schema.columns
                            WHERE table_name = ? AND column_name = ?
                            """,
                            (rs, rowNum) -> rs.getObject(1, Integer.class),
                            tableName,
                            column)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (currentLength == null || currentLength >= TARGET_LENGTH) {
                continue;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE " + tableName + " ALTER COLUMN " + column + " TYPE VARCHAR(" + TARGET_LENGTH + ")");
        }
    }
}
