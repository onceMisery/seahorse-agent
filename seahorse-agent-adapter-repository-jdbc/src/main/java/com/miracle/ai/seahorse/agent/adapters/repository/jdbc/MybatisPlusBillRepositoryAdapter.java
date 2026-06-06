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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.BillDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.BillMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Bill;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 账单 MyBatis Plus 适配器。
 */
public class MybatisPlusBillRepositoryAdapter implements BillRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusBillRepositoryAdapter.class);

    private final BillMapper billMapper;

    public MybatisPlusBillRepositoryAdapter(BillMapper billMapper) {
        this.billMapper = Objects.requireNonNull(billMapper, "billMapper must not be null");
    }

    @Override
    public Bill save(Bill bill) {
        Objects.requireNonNull(bill, "bill must not be null");
        try {
            if (bill.id() == null) {
                BillDO entity = toDO(bill);
                billMapper.insert(entity);
                return toDomain(entity);
            } else {
                BillDO entity = toDO(bill);
                billMapper.updateById(entity);
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
            LambdaQueryWrapper<BillDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BillDO::getTenantId, resolvedTenantId)
                    .orderByDesc(BillDO::getBillPeriod);
            List<BillDO> entities = billMapper.selectList(wrapper);
            return entities.stream().map(this::toDomain).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to query bills for tenant={}: {}", resolvedTenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Bill> findByBillNo(String billNo) {
        Objects.requireNonNull(billNo, "billNo must not be null");
        try {
            LambdaQueryWrapper<BillDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BillDO::getBillNo, billNo);
            BillDO entity = billMapper.selectOne(wrapper);
            return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to query bill by billNo={}: {}", billNo, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean existsForPeriod(String tenantId, String billPeriod) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        Objects.requireNonNull(billPeriod, "billPeriod must not be null");
        try {
            LambdaQueryWrapper<BillDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BillDO::getTenantId, resolvedTenantId)
                    .eq(BillDO::getBillPeriod, billPeriod);
            Long count = billMapper.selectCount(wrapper);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check bill existence for tenant={}, period={}: {}",
                    resolvedTenantId, billPeriod, e.getMessage());
            return false;
        }
    }

    private Bill toDomain(BillDO entity) {
        return new Bill(
                entity.getId(),
                entity.getBillNo(),
                entity.getTenantId(),
                entity.getBillPeriod(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.getGeneratedAt() != null ? entity.getGeneratedAt().toInstant() : null,
                entity.getDueAt() != null ? entity.getDueAt().toInstant() : null
        );
    }

    private BillDO toDO(Bill bill) {
        BillDO entity = new BillDO();
        entity.setId(bill.id());
        entity.setBillNo(bill.billNo());
        entity.setTenantId(bill.tenantId());
        entity.setBillPeriod(bill.billPeriod());
        entity.setTotalAmount(bill.totalAmount());
        entity.setStatus(bill.status());
        entity.setGeneratedAt(bill.generatedAt() != null ? Timestamp.from(bill.generatedAt()) : null);
        entity.setDueAt(bill.dueAt() != null ? Timestamp.from(bill.dueAt()) : null);
        return entity;
    }
}
