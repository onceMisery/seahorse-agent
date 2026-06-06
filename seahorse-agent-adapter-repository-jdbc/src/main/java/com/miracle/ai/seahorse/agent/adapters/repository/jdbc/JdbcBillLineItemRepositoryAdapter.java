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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.BillLineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillLineItemRepositoryPort;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDBC adapter for {@link BillLineItemRepositoryPort} that manages bill line items
 * in the {@code sa_bill_line_item} table.
 */
public class JdbcBillLineItemRepositoryAdapter implements BillLineItemRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcBillLineItemRepositoryAdapter.class);

    private static final String SQL_INSERT = """
            INSERT INTO sa_bill_line_item
                (bill_id, item_type, description, amount, quantity)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_BILL_ID = """
            SELECT id, bill_id, item_type, description, amount, quantity
            FROM sa_bill_line_item
            WHERE bill_id = ?
            ORDER BY id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcBillLineItemRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public BillLineItem save(BillLineItem item) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(SQL_INSERT,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, item.billId());
                ps.setString(2, item.itemType());
                ps.setString(3, item.description());
                ps.setBigDecimal(4, item.amount());
                ps.setLong(5, item.quantity());
                return ps;
            }, keyHolder);
            Long generatedId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
            return new BillLineItem(generatedId, item.billId(), item.itemType(),
                    item.description(), item.amount(), item.quantity());
        } catch (Exception e) {
            log.error("Failed to save bill line item for billId={}: {}",
                    item.billId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<BillLineItem> findByBillId(Long billId) {
        try {
            return jdbcTemplate.query(SQL_FIND_BY_BILL_ID, new BillLineItemRowMapper(), billId);
        } catch (Exception e) {
            log.warn("Failed to query line items for billId={}: {}", billId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static class BillLineItemRowMapper implements RowMapper<BillLineItem> {
        @Override
        public BillLineItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new BillLineItem(
                    rs.getLong("id"),
                    rs.getLong("bill_id"),
                    rs.getString("item_type"),
                    rs.getString("description"),
                    rs.getBigDecimal("amount"),
                    rs.getLong("quantity")
            );
        }
    }
}
