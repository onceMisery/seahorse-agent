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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 负责 canonical metadata 写入，避免治理主适配器直接承担文档/切片双写细节。
 */
final class JdbcMetadataCanonicalWriteSupport {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;
    private final JdbcMetadataColumnDetector columnDetector;

    JdbcMetadataCanonicalWriteSupport(JdbcTemplate jdbcTemplate, JdbcMetadataJsonSupport jsonSupport) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
        this.columnDetector = new JdbcMetadataColumnDetector(jdbcTemplate);
    }

    void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        if (blank(documentId) || acceptedMetadata == null || acceptedMetadata.isEmpty()) {
            return;
        }
        if (!columnDetector.hasDocumentMetadataJsonColumn()) {
            return;
        }
        jdbcTemplate.update("UPDATE t_knowledge_document SET metadata_json = ?, update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND deleted = 0", jsonSupport.json(acceptedMetadata), documentId);
        writeChunkMetadata(documentId, acceptedMetadata);
    }

    private void writeChunkMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        if (!columnDetector.hasChunkMetadataJsonColumn()) {
            return;
        }
        List<ChunkMetadataRow> rows = jdbcTemplate.query("""
                SELECT id, metadata_json
                FROM t_knowledge_chunk
                WHERE doc_id = ? AND deleted = 0
                """, (rs, rowNum) -> new ChunkMetadataRow(rs.getString("id"), rs.getString("metadata_json")),
                documentId);
        for (ChunkMetadataRow row : rows) {
            Map<String, Object> merged = new LinkedHashMap<>(jsonSupport.readMap(row.metadataJson()));
            // acceptedMetadata 是治理后的标准值，写入 chunk 时覆盖旧字段。
            merged.putAll(acceptedMetadata);
            jdbcTemplate.update("""
                    UPDATE t_knowledge_chunk
                    SET metadata_json = ?,
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, jsonSupport.json(merged), row.id());
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ChunkMetadataRow(String id, String metadataJson) {
    }
}
