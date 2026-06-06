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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.SubscriptionPlan;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter for {@link SubscriptionPlanRepositoryPort} that queries
 * subscription plan definitions from the {@code sa_subscription_plan} table.
 */
public class JdbcSubscriptionPlanRepositoryAdapter implements SubscriptionPlanRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcSubscriptionPlanRepositoryAdapter.class);

    private static final String SQL_FIND_ALL = """
            SELECT id, code, name, description, monthly_price, yearly_price,
                   token_limit, storage_limit_bytes, concurrency_limit, active
            FROM sa_subscription_plan
            ORDER BY id
            """;

    private static final String SQL_FIND_BY_CODE = """
            SELECT id, code, name, description, monthly_price, yearly_price,
                   token_limit, storage_limit_bytes, concurrency_limit, active
            FROM sa_subscription_plan
            WHERE code = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSubscriptionPlanRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<SubscriptionPlan> findAll() {
        try {
            return jdbcTemplate.query(SQL_FIND_ALL, new SubscriptionPlanRowMapper());
        } catch (Exception e) {
            log.warn("Failed to query subscription plans: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<SubscriptionPlan> findByCode(PlanCode code) {
        try {
            List<SubscriptionPlan> results = jdbcTemplate.query(
                    SQL_FIND_BY_CODE, new SubscriptionPlanRowMapper(), code.name());
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to query subscription plan by code={}: {}", code, e.getMessage());
            return Optional.empty();
        }
    }

    private static class SubscriptionPlanRowMapper implements RowMapper<SubscriptionPlan> {
        @Override
        public SubscriptionPlan mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SubscriptionPlan(
                    rs.getLong("id"),
                    PlanCode.valueOf(rs.getString("code")),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getBigDecimal("monthly_price"),
                    rs.getBigDecimal("yearly_price"),
                    rs.getLong("token_limit"),
                    rs.getLong("storage_limit_bytes"),
                    rs.getInt("concurrency_limit"),
                    rs.getBoolean("active")
            );
        }
    }
}
