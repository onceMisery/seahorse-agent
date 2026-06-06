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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.PaymentOrderDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.PaymentOrderMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PaymentOrder;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentOrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 支付订单 MyBatis Plus 适配器。
 *
 * <p>对于 SELECT FOR UPDATE 等 MyBatis Plus 不支持的复杂查询，
 * 保留 JdbcTemplate 作为降级方案。
 */
public class MybatisPlusPaymentOrderRepositoryAdapter implements PaymentOrderRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusPaymentOrderRepositoryAdapter.class);

    private static final String SQL_LOCK_BY_ORDER_NO = """
            SELECT id, order_no, tenant_id, plan_code, payment_channel, status,
                   amount, channel_trade_no, created_at, paid_at
            FROM sa_payment_order
            WHERE order_no = ?
            FOR UPDATE
            """;

    private final PaymentOrderMapper paymentOrderMapper;
    private final JdbcTemplate jdbcTemplate;

    public MybatisPlusPaymentOrderRepositoryAdapter(PaymentOrderMapper paymentOrderMapper,
                                                     JdbcTemplate jdbcTemplate) {
        this.paymentOrderMapper = Objects.requireNonNull(paymentOrderMapper, "paymentOrderMapper must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public PaymentOrder save(PaymentOrder order) {
        Objects.requireNonNull(order, "order must not be null");
        try {
            if (order.id() == null) {
                PaymentOrderDO entity = toDO(order);
                paymentOrderMapper.insert(entity);
                return toDomain(entity);
            } else {
                PaymentOrderDO entity = toDO(order);
                paymentOrderMapper.updateById(entity);
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
        Objects.requireNonNull(orderNo, "orderNo must not be null");
        try {
            LambdaQueryWrapper<PaymentOrderDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaymentOrderDO::getOrderNo, orderNo);
            PaymentOrderDO entity = paymentOrderMapper.selectOne(wrapper);
            return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to query payment order by orderNo={}: {}", orderNo, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaymentOrder> lockByOrderNo(String orderNo) {
        Objects.requireNonNull(orderNo, "orderNo must not be null");
        try {
            List<PaymentOrder> results = jdbcTemplate.query(
                    SQL_LOCK_BY_ORDER_NO,
                    (rs, rowNum) -> {
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
                    },
                    orderNo);
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
            Page<PaymentOrderDO> pageParam = new Page<>(page + 1, size);
            LambdaQueryWrapper<PaymentOrderDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaymentOrderDO::getTenantId, resolvedTenantId)
                    .orderByDesc(PaymentOrderDO::getCreatedAt);
            Page<PaymentOrderDO> result = paymentOrderMapper.selectPage(pageParam, wrapper);
            return result.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to query payment orders for tenant={}: {}",
                    resolvedTenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private PaymentOrder toDomain(PaymentOrderDO entity) {
        return new PaymentOrder(
                entity.getId(),
                entity.getOrderNo(),
                entity.getTenantId(),
                entity.getPlanCode() != null ? PlanCode.valueOf(entity.getPlanCode()) : null,
                entity.getPaymentChannel(),
                entity.getStatus(),
                entity.getAmount(),
                entity.getChannelTradeNo(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant() : null,
                entity.getPaidAt() != null ? entity.getPaidAt().toInstant() : null
        );
    }

    private PaymentOrderDO toDO(PaymentOrder order) {
        PaymentOrderDO entity = new PaymentOrderDO();
        entity.setId(order.id());
        entity.setOrderNo(order.orderNo());
        entity.setTenantId(order.tenantId());
        entity.setPlanCode(order.planCode() != null ? order.planCode().name() : null);
        entity.setPaymentChannel(order.paymentChannel());
        entity.setStatus(order.status());
        entity.setAmount(order.amount());
        entity.setChannelTradeNo(order.channelTradeNo());
        entity.setCreatedAt(order.createdAt() != null ? Timestamp.from(order.createdAt()) : null);
        entity.setPaidAt(order.paidAt() != null ? Timestamp.from(order.paidAt()) : null);
        return entity;
    }
}
