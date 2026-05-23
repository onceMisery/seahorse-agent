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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcContextPackRepositoryAdapter implements ContextPackRepositoryPort {

    private static final String PACK_COLUMNS = """
            context_pack_id, run_id, agent_id, version_id, tenant_id, user_id, task_goal, budget_tokens,
            item_count, created_at
            """;
    private static final String ITEM_COLUMNS = """
            item_id, context_pack_id, source_type, source_id, content, summary, score, confidence, sensitivity,
            acl_decision_id, citation_json, estimated_tokens, expires_at, created_at
            """;
    private static final String SQL_INSERT_PACK = """
            INSERT INTO sa_context_pack
            (context_pack_id, run_id, agent_id, version_id, tenant_id, user_id, task_goal, budget_tokens,
             item_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_PACK = """
            UPDATE sa_context_pack
            SET run_id = ?,
                agent_id = ?,
                version_id = ?,
                tenant_id = ?,
                user_id = ?,
                task_goal = ?,
                budget_tokens = ?,
                item_count = ?,
                created_at = ?
            WHERE context_pack_id = ?
            """;
    private static final String SQL_INSERT_ITEM = """
            INSERT INTO sa_context_item
            (item_id, context_pack_id, source_type, source_id, content, summary, score, confidence, sensitivity,
             acl_decision_id, citation_json, estimated_tokens, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_PACK = """
            SELECT %s
            FROM sa_context_pack
            WHERE context_pack_id = ?
            """.formatted(PACK_COLUMNS);
    private static final String SQL_LIST_ITEMS = """
            SELECT %s
            FROM sa_context_item
            WHERE context_pack_id = ?
            ORDER BY created_at ASC, item_id ASC
            """.formatted(ITEM_COLUMNS);
    private static final String SQL_DELETE_ITEMS = """
            DELETE FROM sa_context_item
            WHERE context_pack_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcContextPackRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void save(ContextPack pack) {
        ContextPack safePack = Objects.requireNonNull(pack, "pack must not be null");
        if (packExists(safePack.contextPackId())) {
            updatePack(safePack);
            jdbcTemplate.update(SQL_DELETE_ITEMS, safePack.contextPackId());
        } else {
            insertPack(safePack);
        }
        for (ContextItem item : safePack.items()) {
            insertItem(item);
        }
    }

    @Override
    public Optional<ContextPack> findById(String contextPackId) {
        if (!hasText(contextPackId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_PACK, this::mapPackRow, contextPackId.trim()).stream()
                .findFirst()
                .map(row -> row.toContextPack(listItems(row.contextPackId())));
    }

    @Override
    public List<ContextItem> listItems(String contextPackId) {
        if (!hasText(contextPackId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_ITEMS, this::mapItem, contextPackId.trim());
    }

    private boolean packExists(String contextPackId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sa_context_pack WHERE context_pack_id = ?",
                Integer.class,
                contextPackId);
        return count != null && count > 0;
    }

    private void insertPack(ContextPack pack) {
        jdbcTemplate.update(SQL_INSERT_PACK,
                pack.contextPackId(),
                pack.runId(),
                pack.agentId(),
                pack.versionId(),
                pack.tenantId(),
                pack.userId(),
                pack.taskGoal(),
                pack.budgetTokens(),
                pack.itemCount(),
                toTimestamp(pack.createdAt()));
    }

    private void updatePack(ContextPack pack) {
        jdbcTemplate.update(SQL_UPDATE_PACK,
                pack.runId(),
                pack.agentId(),
                pack.versionId(),
                pack.tenantId(),
                pack.userId(),
                pack.taskGoal(),
                pack.budgetTokens(),
                pack.itemCount(),
                toTimestamp(pack.createdAt()),
                pack.contextPackId());
    }

    private void insertItem(ContextItem item) {
        jdbcTemplate.update(SQL_INSERT_ITEM,
                item.itemId(),
                item.contextPackId(),
                item.sourceType().name(),
                item.sourceId(),
                item.content(),
                item.summary(),
                item.score(),
                item.confidence(),
                item.sensitivity().name(),
                item.aclDecisionId(),
                item.citationJson(),
                item.estimatedTokens(),
                toTimestamp(item.expiresAt()),
                toTimestamp(item.createdAt()));
    }

    private ContextPackRow mapPackRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ContextPackRow(
                resultSet.getString("context_pack_id"),
                resultSet.getString("run_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("user_id"),
                resultSet.getString("task_goal"),
                resultSet.getInt("budget_tokens"),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private ContextItem mapItem(ResultSet resultSet, int rowNum) throws SQLException {
        return new ContextItem(
                resultSet.getString("item_id"),
                resultSet.getString("context_pack_id"),
                ContextItemSourceType.valueOf(resultSet.getString("source_type")),
                resultSet.getString("source_id"),
                resultSet.getString("content"),
                resultSet.getString("summary"),
                resultSet.getDouble("score"),
                resultSet.getDouble("confidence"),
                ContextSensitivity.valueOf(resultSet.getString("sensitivity")),
                resultSet.getString("acl_decision_id"),
                resultSet.getString("citation_json"),
                resultSet.getInt("estimated_tokens"),
                toInstant(resultSet.getTimestamp("expires_at")),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ContextPackRow(String contextPackId,
                                  String runId,
                                  String agentId,
                                  String versionId,
                                  String tenantId,
                                  String userId,
                                  String taskGoal,
                                  int budgetTokens,
                                  Instant createdAt) {

        private ContextPack toContextPack(List<ContextItem> items) {
            return new ContextPack(contextPackId, runId, agentId, versionId, tenantId, userId, taskGoal,
                    budgetTokens, items, createdAt);
        }
    }
}
