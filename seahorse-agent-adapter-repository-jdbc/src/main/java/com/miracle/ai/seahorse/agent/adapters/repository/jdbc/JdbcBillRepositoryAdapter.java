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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.Bill;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillRepositoryPort;
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
 * JDBC adapter for {@link BillRepositoryPort} that manages monthly bills
 * in the {@code sa_bill} table.
 */
public class JdbcBillRepositoryAdapter implements BillRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcBillRepositoryAdapter.class);

    private static final String SQL_FIND_BY_TENANT_ID = """
            SELECT id, bill_no, tenant_id, bill_period, total_amount, status, generated_at, due_at
            FROM sa_bill
            WHERE tenant_id = ?
            ORDER BY bill_period DESC
            """;

    private static final String SQL_FIND_BY_BILL_NO = """
            SELECT id, bill_no, tenant_id, bill_period, total_amount, status, generated_at, due_at
            FROM sa_bill
            WHERE bill_no = ?
            """;

    private static final String SQL_EXISTS_FOR_PERIOD = """
            SELECT COUNT(*) FROM sa_bill WHERE tenant_id = ? AND bill_period = ?
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_bill
                (bill_no, tenant_id, bill_period, total_amount, status, generated_at, due_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_UPDATE = """
            UPDATE sa_bill
            SET total_amount = ?, status = ?, due_at = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcBillRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Bill save(Bill bill) {
        try {
            if (bill.id() == null) {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(SQL_INSERT,
                            Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, bill.billNo());
                    ps.setString(2, bill.tenantId());
                    ps.setString(3, bill.billPeriod());
                    ps.setBigDecimal(4, bill.totalAmount());
                    ps.setString(5, bill.status());
                    ps.setTimestamp(6, toTimestamp(bill.generatedAt()));
                    ps.setTimestamp(7, toTimestamp(bill.dueAt()));
                    return ps;
                }, keyHolder);
                Long generatedId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
                return new Bill(generatedId, bill.billNo(), bill.tenantId(), bill.billPeriod(),
                        bill.totalAmount(), bill.status(), bill.generatedAt(), bill.dueAt());
            } else {
                jdbcTemplate.update(SQL_UPDATE,
                        bill.totalAmount(),
                        bill.status(),
                        toTimestamp(bill.dueAt()),
                        bill.id());
                return bill;
            }
        } catch (Exception e) {
            log.error("Failed to save bill billNo={}: {}", bill.billNo(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<Bill> findByTenantId(String tenantId) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            return jdbcTemplate.query(SQL_FIND_BY_TENANT_ID, new BillRowMapper(), resolvedTenantId);
        } catch (Exception e) {
            log.warn("Failed to query bills for tenant={}: {}", resolvedTenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Bill> findByBillNo(String billNo) {
        try {
            List<Bill> results = jdbcTemplate.query(SQL_FIND_BY_BILL_NO, new BillRowMapper(), billNo);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to query bill by billNo={}: {}", billNo, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean existsForPeriod(String tenantId, String billPeriod) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            Long count = jdbcTemplate.queryForObject(SQL_EXISTS_FOR_PERIOD, Long.class,
                    resolvedTenantId, billPeriod);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check bill existence for tenant={}, period={}: {}",
                    resolvedTenantId, billPeriod, e.getMessage());
            return false;
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class BillRowMapper implements RowMapper<Bill> {
        @Override
        public Bill mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp generatedAt = rs.getTimestamp("generated_at");
            Timestamp dueAt = rs.getTimestamp("due_at");
            return new Bill(
                    rs.getLong("id"),
                    rs.getString("bill_no"),
                    rs.getString("tenant_id"),
                    rs.getString("bill_period"),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("status"),
                    generatedAt != null ? generatedAt.toInstant() : null,
                    dueAt != null ? dueAt.toInstant() : null
            );
        }
    }
}
