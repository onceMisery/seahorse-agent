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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDeleteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDocument;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordIndexPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Objects;

public class JdbcMemoryKeywordIndexRepositoryAdapter implements MemoryKeywordIndexPort {

    private static final String DEFAULT_TENANT_ID = "default";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryKeywordIndexRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void upsert(MemoryDerivedIndexDocument document) {
        if (document == null || !JdbcMemorySupport.hasText(document.memoryId())
                || !JdbcMemorySupport.hasText(document.userId())) {
            return;
        }
        String tenantId = safeTenantId(document.tenantId());
        long userId = JdbcMemorySupport.toLongId(document.userId());
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE t_memory_keyword_index
                SET layer_name = ?,
                    memory_type = ?,
                    content = ?,
                    metadata_json = ?,
                    source_update_time = ?,
                    status = 'ACTIVE',
                    update_time = ?,
                    deleted = 0
                WHERE memory_id = ?
                  AND user_id = ?
                  AND tenant_id = ?
                """,
                document.layer(),
                document.type(),
                document.content(),
                JdbcMemorySupport.writeJson(objectMapper, document.metadata()),
                JdbcMemorySupport.timestamp(document.updatedAt()),
                JdbcMemorySupport.timestamp(now),
                document.memoryId(),
                userId,
                tenantId);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_memory_keyword_index
                    (id, user_id, tenant_id, memory_id, layer_name, memory_type, content, metadata_json,
                     source_update_time, status, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
                """,
                JdbcMemorySupport.nextId(),
                userId,
                tenantId,
                document.memoryId(),
                document.layer(),
                document.type(),
                document.content(),
                JdbcMemorySupport.writeJson(objectMapper, document.metadata()),
                JdbcMemorySupport.timestamp(document.updatedAt()),
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now));
    }

    @Override
    public void delete(MemoryDerivedIndexDeleteCommand command) {
        if (command == null || !JdbcMemorySupport.hasText(command.memoryId())
                || !JdbcMemorySupport.hasText(command.userId())) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE t_memory_keyword_index
                SET status = 'DELETED',
                    update_time = ?,
                    deleted = 1
                WHERE memory_id = ?
                  AND user_id = ?
                  AND tenant_id = ?
                  AND deleted = 0
                """,
                JdbcMemorySupport.timestamp(Instant.now()),
                command.memoryId(),
                JdbcMemorySupport.toLongId(command.userId()),
                safeTenantId(command.tenantId()));
    }

    private String safeTenantId(String tenantId) {
        String normalized = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID).trim();
        return normalized.isBlank() ? DEFAULT_TENANT_ID : normalized;
    }
}
