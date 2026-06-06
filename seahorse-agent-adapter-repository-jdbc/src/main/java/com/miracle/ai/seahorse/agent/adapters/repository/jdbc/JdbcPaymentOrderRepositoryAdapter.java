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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.PaymentOrder;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentOrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter for {@link PaymentOrderRepositoryPort} that manages payment orders
 * in the {@code sa_payment_order} table.
 */
public class JdbcPaymentOrderRepositoryAdapter implements PaymentOrderRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcPaymentOrderRepositoryAdapter.class);

    private static final String SQL_INSERT = """
            INSERT INTO sa_payment_order
                (order_no, tenant_id, plan_code, payment_channel, status, amount, channel_trade_no, created_at, paid_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_UPDATE = """
            UPDATE sa_payment_order
            SET status = ?, channel_trade_no = ?, paid_at = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    private static final String SQL_FIND_BY_ORDER_NO = """
            SELECT id, order_no, tenant_id, plan_code, payment_channel, status,
                   amount, channel_trade_no, created_at, paid_at
            FROM sa_payment_order
            WHERE order_no = ?
            """;

    private static final String SQL_LOCK_BY_ORDER_NO = """
            SELECT id, order_no, tenant_id, plan_code, payment_channel, status,
                   amount, channel_trade_no, created_at, paid_at
            FROM sa_payment_order
            WHERE order_no = ?
            FOR UPDATE
            """;

    private static final String SQL_FIND_BY_TENANT_ID = """
            SELECT id, order_no, tenant_id, plan_code, payment_channel, status,
                   amount, channel_trade_no, created_at, paid_at
            FROM sa_payment_order
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentOrderRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public PaymentOrder save(PaymentOrder order) {
        try {
            if (order.id() == null) {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(SQL_INSERT,
                            Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, order.orderNo());
                    ps.setString(2, order.tenantId());
                    ps.setString(3, order.planCode().name());
                    ps.setString(4, order.paymentChannel());
                    ps.setString(5, order.status());
                    ps.setBigDecimal(6, order.amount());
                    ps.setString(7, order.channelTradeNo());
                    ps.setTimestamp(8, toTimestamp(order.createdAt()));
                    ps.setTimestamp(9, toTimestamp(order.paidAt()));
                    return ps;
                }, keyHolder);
                Long generatedId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
                return new PaymentOrder(generatedId, order.orderNo(), order.tenantId(), order.planCode(),
                        order.paymentChannel(), order.status(), order.amount(), order.channelTradeNo(),
                        order.createdAt(), order.paidAt());
            } else {
                jdbcTemplate.update(SQL_UPDATE,
                        order.status(),
                        order.channelTradeNo(),
                        toTimestamp(order.paidAt()),
                        order.id());
                return order;
            }
        } catch (Exception e) {
            log.error("Failed to save payment order orderNo={}: {}",
                    order.orderNo(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Optional<PaymentOrder> findByOrderNo(String orderNo) {
        try {
            List<PaymentOrder> results = jdbcTemplate.query(
                    SQL_FIND_BY_ORDER_NO, new PaymentOrderRowMapper(), orderNo);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to query payment order by orderNo={}: {}", orderNo, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaymentOrder> lockByOrderNo(String orderNo) {
        try {
            List<PaymentOrder> results = jdbcTemplate.query(
                    SQL_LOCK_BY_ORDER_NO, new PaymentOrderRowMapper(), orderNo);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to lock payment order by orderNo={}: {}", orderNo, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<PaymentOrder> findByTenantId(String tenantId, int page, int size) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            int offset = page * size;
            return jdbcTemplate.query(SQL_FIND_BY_TENANT_ID,
                    new PaymentOrderRowMapper(), resolvedTenantId, size, offset);
        } catch (Exception e) {
            log.warn("Failed to query payment orders for tenant={}: {}",
                    resolvedTenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class PaymentOrderRowMapper implements RowMapper<PaymentOrder> {
        @Override
        public PaymentOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp paidAt = rs.getTimestamp("paid_at");
            return new PaymentOrder(
                    rs.getLong("id"),
                    rs.getString("order_no"),
                    rs.getString("tenant_id"),
                    PlanCode.valueOf(rs.getString("plan_code")),
                    rs.getString("payment_channel"),
                    rs.getString("status"),
                    rs.getBigDecimal("amount"),
                    rs.getString("channel_trade_no"),
                    createdAt != null ? createdAt.toInstant() : null,
                    paidAt != null ? paidAt.toInstant() : null
            );
        }
    }
}
