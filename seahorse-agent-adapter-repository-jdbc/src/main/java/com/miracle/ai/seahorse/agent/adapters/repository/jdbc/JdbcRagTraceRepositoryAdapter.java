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

import static com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemorySupport.hasText;
import static com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemorySupport.toLongId;
import static com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemorySupport.toLongIdOrNull;

import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
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

/**
 * 基于 RAG Trace 表的 JDBC adapter。
 */
public class JdbcRagTraceRepositoryAdapter implements RagTraceRepositoryPort {

    private static final String DEFAULT_STATUS_RUNNING = "RUNNING";
    private static final String SQL_RUN_SELECT = """
            SELECT r.id, r.trace_id, r.trace_name, r.entry_method, r.conversation_id, r.task_id, r.user_id,
                   u.username AS user_name,
                   r.status, r.error_message, r.duration_ms, r.start_time, r.end_time, r.extra_data
            FROM t_rag_trace_run r
            LEFT JOIN t_user u ON u.id = r.user_id AND u.deleted = 0
            """;
    private static final String SQL_NODE_SELECT = """
            SELECT id, trace_id, node_id, parent_node_id, depth, node_type, node_name,
                   class_name, method_name, status, error_message, duration_ms, start_time, end_time
            FROM t_rag_trace_node
            WHERE trace_id = ? AND deleted = 0
            ORDER BY start_time ASC, id ASC
            """;
    private static final String SQL_INSERT_RUN = """
            INSERT INTO t_rag_trace_run
            (id, trace_id, trace_name, entry_method, conversation_id, task_id, user_id, status,
             error_message, start_time, end_time, duration_ms, extra_data, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_FINISH_RUN = """
            UPDATE t_rag_trace_run
            SET status = ?, error_message = ?, end_time = ?, duration_ms = ?, update_time = ?
            WHERE trace_id = ? AND deleted = 0
            """;
    private static final String SQL_INSERT_NODE = """
            INSERT INTO t_rag_trace_node
            (id, trace_id, node_id, parent_node_id, depth, node_type, node_name, class_name,
             method_name, status, error_message, start_time, end_time, duration_ms, extra_data,
             create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_FINISH_NODE = """
            UPDATE t_rag_trace_node
            SET status = ?, error_message = ?, end_time = ?, duration_ms = ?, update_time = ?
            WHERE trace_id = ? AND node_id = ? AND deleted = 0
            """;
    private static final String SQL_SELECT_EXPIRED_RUNS = """
            SELECT trace_id
            FROM t_rag_trace_run
            WHERE deleted = 0 AND start_time < ?
            ORDER BY start_time ASC
            LIMIT ?
            """;
    private static final String SQL_DELETE_EXPIRED_RUNS = """
            UPDATE t_rag_trace_run
            SET deleted = 1, update_time = ?
            WHERE trace_id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_EXPIRED_NODES = """
            UPDATE t_rag_trace_node
            SET deleted = 1, update_time = ?
            WHERE trace_id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRagTraceRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request) {
        RagTracePageRequest safeRequest = normalize(request);
        QueryParts filter = buildRunFilter(safeRequest);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_rag_trace_run r " + filter.where(),
                Long.class, filter.args());
        List<Object> pageArgs = new ArrayList<>(filter.argList());
        pageArgs.add(safeRequest.size());
        pageArgs.add((safeRequest.current() - 1) * safeRequest.size());
        List<RagTraceRun> runs = jdbcTemplate.query(
                SQL_RUN_SELECT + filter.where() + " ORDER BY start_time DESC LIMIT ? OFFSET ?",
                this::mapRun,
                pageArgs.toArray());
        return new RagTracePage<>(safeRequest.current(), safeRequest.size(), total == null ? 0 : total, runs);
    }

    @Override
    public Optional<RagTraceRun> findRun(String traceId) {
        if (!hasText(traceId)) {
            return Optional.empty();
        }
        List<RagTraceRun> runs = jdbcTemplate.query(
                SQL_RUN_SELECT + " WHERE r.trace_id = ? AND r.deleted = 0 LIMIT 1",
                this::mapRun,
                toLongId(traceId));
        if (runs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(runs.get(0));
    }

    @Override
    public List<RagTraceNode> listNodes(String traceId) {
        if (!hasText(traceId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_NODE_SELECT, this::mapNode, toLongId(traceId));
    }

    @Override
    public void startRun(RagTraceRun run) {
        RagTraceRun safeRun = Objects.requireNonNull(run, "run must not be null");
        Instant now = Instant.now();
        jdbcTemplate.update(SQL_INSERT_RUN,
                resolveId(safeRun.getId()),
                toLongId(safeRun.getTraceId()),
                safeRun.getTraceName(),
                safeRun.getEntryMethod(),
                toLongIdOrNull(safeRun.getConversationId()),
                toLongIdOrNull(safeRun.getTaskId()),
                toLongIdOrNull(safeRun.getUserId()),
                statusOrRunning(safeRun.getStatus()),
                safeRun.getErrorMessage(),
                toTimestamp(nullToNow(safeRun.getStartTime())),
                toTimestamp(safeRun.getEndTime()),
                safeRun.getDurationMs(),
                safeRun.getExtraData(),
                toTimestamp(now),
                toTimestamp(now));
    }

    @Override
    public void finishRun(RagTraceRunFinish finish) {
        if (finish == null || !hasText(finish.traceId())) {
            return;
        }
        Instant endTime = nullToNow(finish.endTime());
        jdbcTemplate.update(SQL_FINISH_RUN,
                finish.status(),
                finish.errorMessage(),
                toTimestamp(endTime),
                finish.durationMs(),
                toTimestamp(Instant.now()),
                toLongId(finish.traceId()));
    }

    @Override
    public void startNode(RagTraceNode node) {
        RagTraceNode safeNode = Objects.requireNonNull(node, "node must not be null");
        Instant now = Instant.now();
        jdbcTemplate.update(SQL_INSERT_NODE,
                resolveId(safeNode.getId()),
                toLongId(safeNode.getTraceId()),
                toLongId(safeNode.getNodeId()),
                toLongIdOrNull(safeNode.getParentNodeId()),
                safeNode.getDepth(),
                safeNode.getNodeType(),
                safeNode.getNodeName(),
                safeNode.getClassName(),
                safeNode.getMethodName(),
                statusOrRunning(safeNode.getStatus()),
                safeNode.getErrorMessage(),
                toTimestamp(nullToNow(safeNode.getStartTime())),
                toTimestamp(safeNode.getEndTime()),
                safeNode.getDurationMs(),
                null,
                toTimestamp(now),
                toTimestamp(now));
    }

    @Override
    public void finishNode(RagTraceNodeFinish finish) {
        if (finish == null || !hasText(finish.traceId()) || !hasText(finish.nodeId())) {
            return;
        }
        Instant endTime = nullToNow(finish.endTime());
        jdbcTemplate.update(SQL_FINISH_NODE,
                finish.status(),
                finish.errorMessage(),
                toTimestamp(endTime),
                finish.durationMs(),
                toTimestamp(Instant.now()),
                toLongId(finish.traceId()),
                toLongId(finish.nodeId()));
    }

    @Override
    public int deleteRunsBefore(Instant before, int limit) {
        if (before == null || limit <= 0) {
            return 0;
        }
        List<Long> traceIds = jdbcTemplate.queryForList(SQL_SELECT_EXPIRED_RUNS,
                Long.class, toTimestamp(before), limit);
        if (traceIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        int deleted = 0;
        for (Long traceId : traceIds) {
            jdbcTemplate.update(SQL_DELETE_EXPIRED_NODES, toTimestamp(now), traceId);
            deleted += jdbcTemplate.update(SQL_DELETE_EXPIRED_RUNS, toTimestamp(now), traceId);
        }
        return deleted;
    }

    private QueryParts buildRunFilter(RagTracePageRequest request) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        clauses.add("r.deleted = 0");
        appendEqual(clauses, args, "r.trace_id", request.traceId());
        appendEqual(clauses, args, "r.conversation_id", request.conversationId());
        appendEqual(clauses, args, "r.task_id", request.taskId());
        appendEqual(clauses, args, "r.status", request.status());
        return new QueryParts(" WHERE " + String.join(" AND ", clauses), args);
    }

    private void appendEqual(List<String> clauses, List<Object> args, String column, String value) {
        if (!hasText(value)) {
            return;
        }
        clauses.add(column + " = ?");
        args.add(column.endsWith("_id") ? toLongId(value) : value);
    }

    private RagTracePageRequest normalize(RagTracePageRequest request) {
        if (request == null) {
            return new RagTracePageRequest(1, 10, null, null, null, null);
        }
        long current = request.current() <= 0 ? 1 : request.current();
        long size = request.size() <= 0 ? 10 : request.size();
        return new RagTracePageRequest(current, size, request.traceId(),
                request.conversationId(), request.taskId(), request.status());
    }

    private RagTraceRun mapRun(ResultSet resultSet, int rowNum) throws SQLException {
        RagTraceRun run = new RagTraceRun();
        run.setId(String.valueOf(resultSet.getLong("id")));
        run.setTraceId(String.valueOf(resultSet.getLong("trace_id")));
        run.setTraceName(resultSet.getString("trace_name"));
        run.setEntryMethod(resultSet.getString("entry_method"));
        Long conversationId = resultSet.getObject("conversation_id", Long.class);
        run.setConversationId(conversationId != null ? String.valueOf(conversationId) : null);
        Long taskId = resultSet.getObject("task_id", Long.class);
        run.setTaskId(taskId != null ? String.valueOf(taskId) : null);
        Long userId = resultSet.getObject("user_id", Long.class);
        run.setUserId(userId != null ? String.valueOf(userId) : null);
        run.setUsername(resultSet.getString("user_name"));
        run.setStatus(resultSet.getString("status"));
        run.setErrorMessage(resultSet.getString("error_message"));
        run.setDurationMs(resultSet.getObject("duration_ms", Long.class));
        run.setStartTime(toInstant(resultSet.getTimestamp("start_time")));
        run.setEndTime(toInstant(resultSet.getTimestamp("end_time")));
        run.setExtraData(resultSet.getString("extra_data"));
        return run;
    }

    private RagTraceNode mapNode(ResultSet resultSet, int rowNum) throws SQLException {
        RagTraceNode node = new RagTraceNode();
        node.setId(String.valueOf(resultSet.getLong("id")));
        node.setTraceId(String.valueOf(resultSet.getLong("trace_id")));
        node.setNodeId(String.valueOf(resultSet.getLong("node_id")));
        Long parentNodeId = resultSet.getObject("parent_node_id", Long.class);
        node.setParentNodeId(parentNodeId != null ? String.valueOf(parentNodeId) : null);
        node.setDepth(resultSet.getObject("depth", Integer.class));
        node.setNodeType(resultSet.getString("node_type"));
        node.setNodeName(resultSet.getString("node_name"));
        node.setClassName(resultSet.getString("class_name"));
        node.setMethodName(resultSet.getString("method_name"));
        node.setStatus(resultSet.getString("status"));
        node.setErrorMessage(resultSet.getString("error_message"));
        node.setDurationMs(resultSet.getObject("duration_ms", Long.class));
        node.setStartTime(toInstant(resultSet.getTimestamp("start_time")));
        node.setEndTime(toInstant(resultSet.getTimestamp("end_time")));
        return node;
    }

    private Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private Instant nullToNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }

    private String statusOrRunning(String status) {
        if (!hasText(status)) {
            return DEFAULT_STATUS_RUNNING;
        }
        return status;
    }

    private long resolveId(String id) {
        if (hasText(id)) {
            return toLongId(id);
        }
        return JdbcMemorySupport.nextId();
    }

    

    private record QueryParts(String where, List<Object> argList) {

        private Object[] args() {
            return argList.toArray();
        }
    }
}
