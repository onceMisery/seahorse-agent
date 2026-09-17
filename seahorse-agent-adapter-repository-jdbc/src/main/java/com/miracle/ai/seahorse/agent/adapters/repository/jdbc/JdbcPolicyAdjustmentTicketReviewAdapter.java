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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PreviousProposalOutcome;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketReviewRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 工单回访记录的 JDBC 适配器（append-only）。
 *
 * <p>批次幂等不能只靠应用层的"先查再写"——两个实例同时执行同一批次的回访时，
 * 两次查询都可能看不到对方。唯一约束 {@code (ticket_id, batch_id)} 是唯一可靠的那道防线，
 * 写入语句在它之前先做一次 {@code WHERE NOT EXISTS} 以避免把冲突当成噪音异常抛出。
 */
public class JdbcPolicyAdjustmentTicketReviewAdapter implements PolicyAdjustmentTicketReviewRepositoryPort {

    private static final String COLUMNS = """
            review_id, ticket_id, tenant_id, batch_id, outcome, detail,
            baseline_pass_rate, current_pass_rate, source, create_time
            """;

    /**
     * 用 {@code INSERT ... SELECT ... WHERE NOT EXISTS} 而不是 {@code ON CONFLICT DO NOTHING}：
     * 后者是 PostgreSQL 方言，H2 不认，会让所有持久化测试在 CI 上直接红。
     *
     * <p>{@code WHERE NOT EXISTS} 只能挡住"顺序发生"的重复，并发下两个事务可能同时
     * 看到"不存在"然后都插入——真正拦住它的是 {@code (ticket_id, batch_id)} 唯一约束。
     * 真撞上时数据库会抛唯一冲突异常，由调用方（定时任务）记日志，不会静默写脏数据。
     */
    private static final String SQL_INSERT = """
            INSERT INTO sa_policy_adjustment_ticket_review
            (review_id, ticket_id, tenant_id, batch_id, outcome, detail,
             baseline_pass_rate, current_pass_rate, source, create_time)
            SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM sa_policy_adjustment_ticket_review
                WHERE ticket_id = ? AND batch_id = ?
            )
            """;

    private static final String SQL_LIST_BY_TICKET = """
            SELECT %s FROM sa_policy_adjustment_ticket_review
            WHERE ticket_id = ?
            ORDER BY create_time DESC, review_id DESC
            LIMIT ?
            """.formatted(COLUMNS);

    private static final String SQL_LATEST_BY_TICKETS = """
            SELECT %s FROM sa_policy_adjustment_ticket_review
            WHERE ticket_id = ANY (?)
            ORDER BY create_time DESC, review_id DESC
            """.formatted(COLUMNS);

    private static final String SQL_LATEST_ONE_TICKET = """
            SELECT %s FROM sa_policy_adjustment_ticket_review
            WHERE ticket_id = ?
            ORDER BY create_time DESC, review_id DESC
            LIMIT 1
            """.formatted(COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcPolicyAdjustmentTicketReviewAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void save(PolicyAdjustmentTicketReview review) {
        Objects.requireNonNull(review, "review must not be null");
        jdbcTemplate.update(SQL_INSERT,
                review.reviewId(),
                review.ticketId(),
                review.tenantId(),
                review.batchId(),
                review.outcome().name(),
                review.detail(),
                review.baselinePassRate(),
                review.currentPassRate(),
                review.source(),
                Timestamp.from(review.reviewedAt()),
                review.ticketId(),
                review.batchId());
    }

    @Override
    public List<PolicyAdjustmentTicketReview> listByTicket(String ticketId, int limit) {
        if (ticketId == null || ticketId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_BY_TICKET, this::mapReview, ticketId, Math.max(1, limit));
    }

    @Override
    public Map<String, PolicyAdjustmentTicketReview> latestByTickets(List<String> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return Map.of();
        }
        List<String> distinct = List.copyOf(new LinkedHashSet<>(ticketIds));
        List<PolicyAdjustmentTicketReview> rows;
        try {
            rows = jdbcTemplate.query(SQL_LATEST_BY_TICKETS, this::mapReview,
                    new Object[]{distinct.toArray(new String[0])});
        } catch (RuntimeException ex) {
            // 数组参数在部分驱动下不被支持；退化成逐张查询，保证冷却期判断不失效
            rows = new ArrayList<>();
            for (String ticketId : distinct) {
                rows.addAll(jdbcTemplate.query(SQL_LATEST_ONE_TICKET, this::mapReview, ticketId));
            }
        }
        // 已按时间倒序，第一次出现的即为该工单最近一次回访
        Map<String, PolicyAdjustmentTicketReview> latest = new HashMap<>();
        for (PolicyAdjustmentTicketReview row : rows) {
            latest.putIfAbsent(row.ticketId(), row);
        }
        return Map.copyOf(latest);
    }

    private PolicyAdjustmentTicketReview mapReview(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyAdjustmentTicketReview(
                text(rs.getString("review_id")),
                text(rs.getString("ticket_id")),
                text(rs.getString("tenant_id")),
                text(rs.getString("batch_id")),
                outcome(rs.getString("outcome")),
                rs.getString("detail"),
                nullableDouble(rs, "baseline_pass_rate"),
                nullableDouble(rs, "current_pass_rate"),
                text(rs.getString("source")),
                timestamp(rs.getTimestamp("create_time")));
    }

    private static PreviousProposalOutcome.Status outcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return PreviousProposalOutcome.Status.AWAITING_MEASUREMENT;
        }
        try {
            return PreviousProposalOutcome.Status.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PreviousProposalOutcome.Status.AWAITING_MEASUREMENT;
        }
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant timestamp(Timestamp value) {
        return value == null ? Instant.now() : value.toInstant();
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
