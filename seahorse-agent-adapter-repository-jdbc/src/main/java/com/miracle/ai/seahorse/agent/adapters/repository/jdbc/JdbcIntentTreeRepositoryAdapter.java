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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodeTree;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于旧 {@code t_intent_node} 表的 JDBC 意图树仓储 adapter。
 */
public class JdbcIntentTreeRepositoryAdapter implements IntentTreeRepositoryPort {

    private static final String SQL_COLUMNS = """
            id, intent_code, name, level, parent_code, description, examples, collection_name, top_k, kind,
            sort_order, enabled, mcp_tool_id, prompt_snippet, prompt_template, param_prompt_template
            """;
    private static final String SQL_LIST = "SELECT " + SQL_COLUMNS + """
            FROM t_intent_node
            WHERE deleted = 0
            ORDER BY sort_order ASC, id ASC
            """;
    private static final String SQL_FIND = "SELECT " + SQL_COLUMNS + """
            FROM t_intent_node
            WHERE id = ? AND deleted = 0
            LIMIT 1
            """;
    private static final String SQL_EXISTS = """
            SELECT COUNT(1)
            FROM t_intent_node
            WHERE intent_code = ? AND deleted = 0
            """;
    private static final String SQL_INSERT = """
            INSERT INTO t_intent_node
            (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, top_k,
             mcp_tool_id, kind, prompt_snippet, prompt_template, param_prompt_template, sort_order, enabled,
             create_by, update_by, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcIntentTreeRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<IntentNodeTree> listActiveNodes() {
        return jdbcTemplate.query(SQL_LIST, this::mapNode);
    }

    @Override
    public Optional<IntentNodeTree> findById(String id) {
        if (!hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND, this::mapNode, toLongId(id)).stream().findFirst();
    }

    @Override
    public boolean existsByIntentCode(String intentCode) {
        if (!hasText(intentCode)) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(SQL_EXISTS, Long.class, intentCode.trim());
        return count != null && count > 0;
    }

    @Override
    public String create(IntentNodePayload payload, String operator) {
        IntentNodePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String id = nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT,
                toLongId(id),
                toNullableLong(safePayload.getKbId()),
                safePayload.getIntentCode(),
                safePayload.getName(),
                safePayload.getLevel(),
                trimToNull(safePayload.getParentCode()),
                safePayload.getDescription(),
                toExamplesJson(safePayload),
                resolveCollectionName(safePayload),
                normalizeTopK(safePayload.getTopK()),
                safePayload.getMcpToolId(),
                safePayload.getKind() == null ? 0 : safePayload.getKind(),
                safePayload.getPromptSnippet(),
                safePayload.getPromptTemplate(),
                safePayload.getParamPromptTemplate(),
                safePayload.getSortOrder() == null ? 0 : safePayload.getSortOrder(),
                safePayload.getEnabled() == null ? 1 : safePayload.getEnabled(),
                toNullableLong(operator),
                toNullableLong(operator),
                now,
                now);
        return id;
    }

    @Override
    public boolean update(String id, IntentNodePayload payload, String operator) {
        IntentNodePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        if (!hasText(id)) {
            return false;
        }
        List<Object> args = new ArrayList<>();
        String setClause = buildUpdateSetClause(safePayload, operator, args);
        args.add(toLongId(id));
        int updated = jdbcTemplate.update("""
                UPDATE t_intent_node
                SET %s
                WHERE id = ? AND deleted = 0
                """.formatted(setClause), args.toArray());
        return updated > 0;
    }

    @Override
    public boolean deleteByIds(List<String> ids) {
        List<Long> safeIds = normalizeIds(ids);
        if (safeIds.isEmpty()) {
            return false;
        }
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(Instant.now()));
        args.addAll(safeIds);
        int updated = jdbcTemplate.update("""
                UPDATE t_intent_node
                SET deleted = 1, update_time = ?
                WHERE deleted = 0 AND id IN (%s)
                """.formatted(placeholders(safeIds.size())), args.toArray());
        return updated > 0;
    }

    @Override
    public boolean updateEnabled(List<String> ids, int enabled, String operator) {
        List<Long> safeIds = normalizeIds(ids);
        if (safeIds.isEmpty()) {
            return false;
        }
        List<Object> args = new ArrayList<>();
        args.add(enabled);
        args.add(toNullableLong(operator));
        args.add(Timestamp.from(Instant.now()));
        args.addAll(safeIds);
        int updated = jdbcTemplate.update("""
                UPDATE t_intent_node
                SET enabled = ?, update_by = ?, update_time = ?
                WHERE deleted = 0 AND id IN (%s)
                """.formatted(placeholders(safeIds.size())), args.toArray());
        return updated > 0;
    }

    private String buildUpdateSetClause(IntentNodePayload payload, String operator, List<Object> args) {
        List<String> fragments = new ArrayList<>();
        addIfPresent(fragments, args, "name", payload.getName());
        addIfPresent(fragments, args, "level", payload.getLevel());
        addIfPresent(fragments, args, "parent_code", payload.getParentCode());
        addIfPresent(fragments, args, "description", payload.getDescription());
        addIfPresent(fragments, args, "collection_name", payload.getCollectionName());
        addIfPresent(fragments, args, "top_k", normalizeTopK(payload.getTopK()));
        addIfPresent(fragments, args, "kind", payload.getKind());
        addIfPresent(fragments, args, "sort_order", payload.getSortOrder());
        addIfPresent(fragments, args, "enabled", payload.getEnabled());
        addIfPresent(fragments, args, "prompt_snippet", payload.getPromptSnippet());
        addIfPresent(fragments, args, "prompt_template", payload.getPromptTemplate());
        addIfPresent(fragments, args, "param_prompt_template", payload.getParamPromptTemplate());
        addIfPresent(fragments, args, "mcp_tool_id", payload.getMcpToolId());
        if (payload.getExamples() != null) {
            fragments.add("examples = ?");
            args.add(toExamplesJson(payload));
        }
        fragments.add("update_by = ?");
        args.add(toNullableLong(operator));
        fragments.add("update_time = ?");
        args.add(Timestamp.from(Instant.now()));
        return String.join(", ", fragments);
    }

    private void addIfPresent(List<String> fragments, List<Object> args, String column, Object value) {
        if (value == null) {
            return;
        }
        fragments.add(column + " = ?");
        args.add(value);
    }

    private String resolveCollectionName(IntentNodePayload payload) {
        if (hasText(payload.getCollectionName())) {
            return payload.getCollectionName();
        }
        if (!hasText(payload.getKbId())) {
            return null;
        }
        List<String> names = jdbcTemplate.query("""
                SELECT collection_name
                FROM t_knowledge_base
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, (resultSet, rowNum) -> resultSet.getString("collection_name"),
                toNullableLong(payload.getKbId()));
        return names.isEmpty() ? null : names.get(0);
    }

    private String toExamplesJson(IntentNodePayload payload) {
        if (payload.getExamples() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload.getExamples());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("examples must be serializable", ex);
        }
    }

    private IntentNodeTree mapNode(ResultSet resultSet, int rowNum) throws SQLException {
        IntentNodeTree node = new IntentNodeTree();
        node.setId(resultSet.getString("id"));
        node.setIntentCode(resultSet.getString("intent_code"));
        node.setName(resultSet.getString("name"));
        node.setLevel(resultSet.getObject("level", Integer.class));
        node.setParentCode(resultSet.getString("parent_code"));
        node.setDescription(resultSet.getString("description"));
        node.setExamples(resultSet.getString("examples"));
        node.setCollectionName(resultSet.getString("collection_name"));
        node.setTopK(resultSet.getObject("top_k", Integer.class));
        node.setKind(resultSet.getObject("kind", Integer.class));
        node.setSortOrder(resultSet.getObject("sort_order", Integer.class));
        node.setEnabled(resultSet.getObject("enabled", Integer.class));
        node.setMcpToolId(resultSet.getString("mcp_tool_id"));
        node.setPromptSnippet(resultSet.getString("prompt_snippet"));
        node.setPromptTemplate(resultSet.getString("prompt_template"));
        node.setParamPromptTemplate(resultSet.getString("param_prompt_template"));
        return node;
    }

    private Integer normalizeTopK(Integer topK) {
        if (topK == null) {
            return null;
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
        return topK;
    }

    private List<Long> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(this::hasText).map(this::toLongId).distinct().toList();
    }

    private String placeholders(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> "?")
                .collect(Collectors.joining(","));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nextId() {
        return SnowflakeIds.nextIdString();
    }

    private Long toNullableLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        return toLongId(value);
    }

    private long toLongId(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("intent node id must be numeric: " + value, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
