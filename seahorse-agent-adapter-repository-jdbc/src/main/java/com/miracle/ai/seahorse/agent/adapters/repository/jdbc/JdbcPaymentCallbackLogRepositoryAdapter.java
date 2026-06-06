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

import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentCallbackLogRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * JDBC adapter for {@link PaymentCallbackLogRepositoryPort} that records and checks
 * payment callback idempotency in the {@code sa_payment_callback_log} table.
 */
public class JdbcPaymentCallbackLogRepositoryAdapter implements PaymentCallbackLogRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcPaymentCallbackLogRepositoryAdapter.class);

    private static final String SQL_EXISTS = """
            SELECT COUNT(*) FROM sa_payment_callback_log
            WHERE channel = ? AND channel_trade_no = ?
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_payment_callback_log (channel, channel_trade_no, order_no)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentCallbackLogRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public boolean exists(String channel, String channelTradeNo) {
        try {
            Long count = jdbcTemplate.queryForObject(SQL_EXISTS, Long.class, channel, channelTradeNo);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check callback log for channel={}, tradeNo={}: {}",
                    channel, channelTradeNo, e.getMessage());
            return false;
        }
    }

    @Override
    public void save(String channel, String channelTradeNo, String orderNo) {
        try {
            jdbcTemplate.update(SQL_INSERT, channel, channelTradeNo, orderNo);
        } catch (Exception e) {
            log.error("Failed to save callback log for channel={}, tradeNo={}, orderNo={}: {}",
                    channel, channelTradeNo, orderNo, e.getMessage(), e);
            throw e;
        }
    }
}
