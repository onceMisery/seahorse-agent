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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存 JDBC 元数据治理链路对表结构能力的探测结果，
 * 避免主适配器在写文档/分块元数据时反复持有列探测状态。
 */
public final class JdbcMetadataColumnDetector {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    public JdbcMetadataColumnDetector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public boolean hasDocumentMetadataJsonColumn() {
        return hasColumn("t_knowledge_document", "metadata_json");
    }

    public boolean hasChunkMetadataJsonColumn() {
        return hasColumn("t_knowledge_chunk", "metadata_json");
    }

    private boolean hasColumn(String tableName, String columnName) {
        String cacheKey = tableName + "." + columnName;
        return cache.computeIfAbsent(cacheKey, ignored -> queryColumn(tableName, columnName));
    }

    private boolean queryColumn(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.columns
                    WHERE lower(table_name) = ?
                      AND lower(column_name) = ?
                    """, Integer.class, tableName.toLowerCase(), columnName.toLowerCase());
            return count != null && count > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
