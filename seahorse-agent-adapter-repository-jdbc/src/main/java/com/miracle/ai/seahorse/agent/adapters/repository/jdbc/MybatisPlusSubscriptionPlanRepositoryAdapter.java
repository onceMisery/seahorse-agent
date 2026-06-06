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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.SubscriptionPlanDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.SubscriptionPlanMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.SubscriptionPlan;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MyBatis Plus adapter for {@link SubscriptionPlanRepositoryPort} that queries
 * subscription plan definitions from the {@code sa_subscription_plan} table.
 */
public class MybatisPlusSubscriptionPlanRepositoryAdapter implements SubscriptionPlanRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusSubscriptionPlanRepositoryAdapter.class);

    private final SubscriptionPlanMapper subscriptionPlanMapper;

    public MybatisPlusSubscriptionPlanRepositoryAdapter(SubscriptionPlanMapper subscriptionPlanMapper) {
        this.subscriptionPlanMapper = Objects.requireNonNull(subscriptionPlanMapper,
                "subscriptionPlanMapper must not be null");
    }

    @Override
    public List<SubscriptionPlan> findAll() {
        try {
            LambdaQueryWrapper<SubscriptionPlanDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByAsc(SubscriptionPlanDO::getId);
            List<SubscriptionPlanDO> entities = subscriptionPlanMapper.selectList(wrapper);
            return entities.stream().map(this::toDomain).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to query subscription plans: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<SubscriptionPlan> findByCode(PlanCode code) {
        Objects.requireNonNull(code, "code must not be null");
        try {
            LambdaQueryWrapper<SubscriptionPlanDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SubscriptionPlanDO::getCode, code.name());
            SubscriptionPlanDO entity = subscriptionPlanMapper.selectOne(wrapper);
            return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to query subscription plan by code={}: {}", code, e.getMessage());
            return Optional.empty();
        }
    }

    private SubscriptionPlan toDomain(SubscriptionPlanDO entity) {
        return new SubscriptionPlan(
                entity.getId(),
                PlanCode.valueOf(entity.getCode()),
                entity.getName(),
                entity.getDescription(),
                entity.getMonthlyPrice(),
                entity.getYearlyPrice(),
                entity.getTokenLimit() != null ? entity.getTokenLimit() : 0L,
                entity.getStorageLimitBytes() != null ? entity.getStorageLimitBytes() : 0L,
                entity.getConcurrencyLimit() != null ? entity.getConcurrencyLimit() : 0,
                entity.getActive() != null ? entity.getActive() : false
        );
    }

    private SubscriptionPlanDO toDO(SubscriptionPlan plan) {
        SubscriptionPlanDO entity = new SubscriptionPlanDO();
        entity.setId(plan.id());
        entity.setCode(plan.code() != null ? plan.code().name() : null);
        entity.setName(plan.name());
        entity.setDescription(plan.description());
        entity.setMonthlyPrice(plan.monthlyPrice());
        entity.setYearlyPrice(plan.yearlyPrice());
        entity.setTokenLimit(plan.tokenLimit());
        entity.setStorageLimitBytes(plan.storageLimitBytes());
        entity.setConcurrencyLimit(plan.concurrencyLimit());
        entity.setActive(plan.active());
        return entity;
    }
}
