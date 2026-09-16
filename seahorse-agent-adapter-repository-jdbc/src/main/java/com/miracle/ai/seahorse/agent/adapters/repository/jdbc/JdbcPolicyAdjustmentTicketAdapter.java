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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 落地工单的 JDBC 适配器，同时承担写入与只读查询（与反馈适配器同一手法）。
 *
 * <p>状态迁移走 {@code UPDATE ... WHERE status = ?}，用影响行数判断是否命中——
 * 工单可被多人审批，先读后写会让后一个人的决策静默覆盖前一个人的。
 */
public class JdbcPolicyAdjustmentTicketAdapter
        implements PolicyAdjustmentTicketRepositoryPort, PolicyAdjustmentTicketQueryPort {

    private static final String COLUMNS = """
            ticket_id, tenant_id, target_key, policy_key, direction, suggested_delta,
            disapproval_rate, sample_count, rationale, evidence_ids, fingerprint, status,
            created_by, created_at, updated_at, decided_by, decided_at, decision_comment,
            applied_by, applied_at, rollback_comment
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_policy_adjustment_ticket
            (ticket_id, tenant_id, target_key, policy_key, direction, suggested_delta,
             disapproval_rate, sample_count, rationale, evidence_ids, fingerprint, status,
             created_by, created_at, updated_at, decided_by, decided_at, decision_comment,
             applied_by, applied_at, rollback_comment)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_UPDATE_BY_STATUS = """
            UPDATE sa_policy_adjustment_ticket
            SET tenant_id = ?, target_key = ?, policy_key = ?, direction = ?, suggested_delta = ?,
                disapproval_rate = ?, sample_count = ?, rationale = ?, evidence_ids = ?, fingerprint = ?,
                status = ?, created_by = ?, created_at = ?, updated_at = ?, decided_by = ?, decided_at = ?,
                decision_comment = ?, applied_by = ?, applied_at = ?, rollback_comment = ?
            WHERE ticket_id = ? AND status = ?
            """;

    private static final String SQL_FIND_BY_ID = """
            SELECT %s FROM sa_policy_adjustment_ticket WHERE ticket_id = ?
            """.formatted(COLUMNS);

    private static final String SQL_PAGE = """
            SELECT %s FROM sa_policy_adjustment_ticket
            WHERE tenant_id = ? %s
            ORDER BY created_at DESC, ticket_id DESC
            LIMIT ? OFFSET ?
            """.formatted(COLUMNS, "%s");

    private static final String SQL_COUNT = """
            SELECT COUNT(*) FROM sa_policy_adjustment_ticket WHERE tenant_id = ? %s
            """.formatted("%s");

    private static final String SQL_EXISTING_FINGERPRINTS = """
            SELECT fingerprint FROM sa_policy_adjustment_ticket
            WHERE tenant_id = ? AND fingerprint = ANY (?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPolicyAdjustmentTicketAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcPolicyAdjustmentTicketAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public void save(PolicyAdjustmentTicket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        jdbcTemplate.update(SQL_INSERT, args(ticket));
    }

    @Override
    public boolean updateIfStatus(String ticketId,
                                  PolicyAdjustmentTicketStatus expectedStatus,
                                  PolicyAdjustmentTicket ticket) {
        if (ticketId == null || ticket == null || expectedStatus == null) {
            return false;
        }
        Object[] args = args(ticket);
        // args[0] 是 ticket_id，UPDATE 把它放进了 WHERE；SET 子句从 tenant_id（args[1]）开始，
        // 末尾再补上 WHERE 需要的 ticket_id 与期望状态。
        Object[] updateArgs = new Object[args.length + 1];
        System.arraycopy(args, 1, updateArgs, 0, args.length - 1);
        updateArgs[updateArgs.length - 2] = ticketId;
        updateArgs[updateArgs.length - 1] = expectedStatus.name();
        return jdbcTemplate.update(SQL_UPDATE_BY_STATUS, updateArgs) > 0;
    }

    @Override
    public Optional<PolicyAdjustmentTicket> findById(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return Optional.empty();
        }
        List<PolicyAdjustmentTicket> found = jdbcTemplate.query(SQL_FIND_BY_ID, this::mapTicket, ticketId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public PolicyAdjustmentTicketPage page(PolicyAdjustmentTicketQuery query) {
        PolicyAdjustmentTicketQuery safe = query == null
                ? new PolicyAdjustmentTicketQuery(null, null,
                        PolicyAdjustmentTicketQuery.DEFAULT_CURRENT, PolicyAdjustmentTicketQuery.DEFAULT_PAGE_SIZE)
                : query;
        long current = Math.max(1L, safe.current());
        long size = Math.min(Math.max(1L, safe.size()), 200L);
        String statusFilter = safe.status() == null ? "" : " AND status = ?";
        long total = count(safe, statusFilter);
        List<PolicyAdjustmentTicket> records;
        if (safe.status() == null) {
            records = jdbcTemplate.query(String.format(SQL_PAGE, ""), this::mapTicket,
                    safe.tenantId(), size, (current - 1) * size);
        } else {
            records = jdbcTemplate.query(String.format(SQL_PAGE, " AND status = ?"), this::mapTicket,
                    safe.tenantId(), safe.status().name(), size, (current - 1) * size);
        }
        long pages = (total + size - 1) / size;
        return new PolicyAdjustmentTicketPage(records, total, size, current, pages);
    }

    @Override
    public Set<String> existingFingerprints(String tenantId, List<String> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            return Set.of();
        }
        List<String> distinct = List.copyOf(new LinkedHashSet<>(fingerprints));
        String[] array = distinct.toArray(new String[0]);
        List<String> found;
        try {
            found = jdbcTemplate.queryForList(SQL_EXISTING_FINGERPRINTS, String.class,
                    Objects.requireNonNullElse(tenantId, ""), array);
        } catch (RuntimeException ex) {
            // 数组参数在部分驱动下不被支持；退化成逐条查询，保证去重语义不丢
            found = new ArrayList<>();
            for (String fingerprint : distinct) {
                List<String> hit = jdbcTemplate.queryForList(
                        "SELECT fingerprint FROM sa_policy_adjustment_ticket"
                                + " WHERE tenant_id = ? AND fingerprint = ? LIMIT 1",
                        String.class, Objects.requireNonNullElse(tenantId, ""), fingerprint);
                found.addAll(hit);
            }
        }
        return Set.copyOf(found);
    }

    private long count(PolicyAdjustmentTicketQuery query, String statusFilter) {
        if (query.status() == null) {
            Long value = jdbcTemplate.queryForObject(
                    String.format(SQL_COUNT, ""), Long.class, query.tenantId());
            return value == null ? 0L : value;
        }
        Long value = jdbcTemplate.queryForObject(
                String.format(SQL_COUNT, statusFilter), Long.class, query.tenantId(), query.status().name());
        return value == null ? 0L : value;
    }

    private Object[] args(PolicyAdjustmentTicket ticket) {
        return new Object[]{
                ticket.ticketId(),
                ticket.tenantId(),
                ticket.targetKey(),
                ticket.policyKey(),
                ticket.direction().name(),
                ticket.suggestedDelta(),
                ticket.disapprovalRate(),
                ticket.sampleCount(),
                ticket.rationale(),
                writeJson(ticket.evidenceIds()),
                ticket.fingerprint(),
                ticket.status().name(),
                ticket.createdBy(),
                timestamp(ticket.createdAt()),
                timestamp(ticket.updatedAt()),
                ticket.decidedBy(),
                timestamp(ticket.decidedAt()),
                ticket.decisionComment(),
                ticket.appliedBy(),
                timestamp(ticket.appliedAt()),
                ticket.rollbackComment()
        };
    }

    private PolicyAdjustmentTicket mapTicket(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyAdjustmentTicket(
                rs.getString("ticket_id"),
                rs.getString("tenant_id"),
                rs.getString("target_key"),
                rs.getString("policy_key"),
                parseDirection(rs.getString("direction")),
                rs.getDouble("suggested_delta"),
                rs.getDouble("disapproval_rate"),
                rs.getInt("sample_count"),
                rs.getString("rationale"),
                readJson(rs.getString("evidence_ids")),
                rs.getString("fingerprint"),
                parseStatus(rs.getString("status")),
                rs.getString("created_by"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                rs.getString("decided_by"),
                instant(rs.getTimestamp("decided_at")),
                rs.getString("decision_comment"),
                rs.getString("applied_by"),
                instant(rs.getTimestamp("applied_at")),
                rs.getString("rollback_comment"));
    }

    private static PolicyAdjustmentProposal.Direction parseDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return PolicyAdjustmentProposal.Direction.TIGHTEN;
        }
        try {
            return PolicyAdjustmentProposal.Direction.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return PolicyAdjustmentProposal.Direction.TIGHTEN;
        }
    }

    private static PolicyAdjustmentTicketStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return PolicyAdjustmentTicketStatus.PENDING;
        }
        try {
            return PolicyAdjustmentTicketStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return PolicyAdjustmentTicketStatus.PENDING;
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
