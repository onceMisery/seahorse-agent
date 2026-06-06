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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.BillLineItemDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.BillLineItemMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.BillLineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillLineItemRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 账单明细 MyBatis Plus 适配器。
 */
public class MybatisPlusBillLineItemRepositoryAdapter implements BillLineItemRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusBillLineItemRepositoryAdapter.class);

    private final BillLineItemMapper billLineItemMapper;

    public MybatisPlusBillLineItemRepositoryAdapter(BillLineItemMapper billLineItemMapper) {
        this.billLineItemMapper = Objects.requireNonNull(billLineItemMapper, "billLineItemMapper must not be null");
    }

    @Override
    public BillLineItem save(BillLineItem item) {
        Objects.requireNonNull(item, "item must not be null");
        try {
            BillLineItemDO entity = toDO(item);
            billLineItemMapper.insert(entity);
            return toDomain(entity);
        } catch (Exception e) {
            log.error("Failed to save bill line item for billId={}: {}",
                    item.billId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<BillLineItem> findByBillId(Long billId) {
        Objects.requireNonNull(billId, "billId must not be null");
        try {
            LambdaQueryWrapper<BillLineItemDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BillLineItemDO::getBillId, billId)
                    .orderByAsc(BillLineItemDO::getId);
            List<BillLineItemDO> entities = billLineItemMapper.selectList(wrapper);
            return entities.stream().map(this::toDomain).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to query line items for billId={}: {}", billId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private BillLineItem toDomain(BillLineItemDO entity) {
        return new BillLineItem(
                entity.getId(),
                entity.getBillId(),
                entity.getItemType(),
                entity.getDescription(),
                entity.getAmount(),
                entity.getQuantity() != null ? entity.getQuantity() : 0L
        );
    }

    private BillLineItemDO toDO(BillLineItem item) {
        BillLineItemDO entity = new BillLineItemDO();
        entity.setId(item.id());
        entity.setBillId(item.billId());
        entity.setItemType(item.itemType());
        entity.setDescription(item.description());
        entity.setAmount(item.amount());
        entity.setQuantity(item.quantity());
        return entity;
    }
}
