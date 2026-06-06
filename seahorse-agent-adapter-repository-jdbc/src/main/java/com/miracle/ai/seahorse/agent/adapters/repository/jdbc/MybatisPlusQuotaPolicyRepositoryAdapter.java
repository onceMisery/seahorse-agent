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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.QuotaPolicyDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.QuotaPolicyMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis Plus 配额策略仓储适配器。
 */
public class MybatisPlusQuotaPolicyRepositoryAdapter implements QuotaPolicyRepositoryPort {

    private final QuotaPolicyMapper mapper;

    public MybatisPlusQuotaPolicyRepositoryAdapter(QuotaPolicyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public QuotaPolicy upsert(QuotaPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        QuotaPolicyDO existing = mapper.selectById(policy.policyId());
        if (existing != null) {
            QuotaPolicyDO entity = toDO(policy);
            mapper.updateById(entity);
        } else {
            QuotaPolicyDO entity = toDO(policy);
            mapper.insert(entity);
        }
        return policy;
    }

    @Override
    public Optional<QuotaPolicy> findActive(String tenantId, QuotaScope scope, String subjectId) {
        if (tenantId == null || scope == null || subjectId == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<QuotaPolicyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuotaPolicyDO::getTenantId, tenantId.trim())
                .eq(QuotaPolicyDO::getScope, scope.name())
                .eq(QuotaPolicyDO::getSubjectId, subjectId.trim())
                .eq(QuotaPolicyDO::getStatus, QuotaPolicyStatus.ACTIVE.name())
                .orderByDesc(QuotaPolicyDO::getUpdatedAt)
                .last("LIMIT 1");
        QuotaPolicyDO entity = mapper.selectOne(wrapper);
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public void disable(String policyId, Instant updatedAt) {
        if (policyId == null || policyId.isBlank()) {
            return;
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        LambdaUpdateWrapper<QuotaPolicyDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QuotaPolicyDO::getPolicyId, policyId.trim())
                .set(QuotaPolicyDO::getStatus, QuotaPolicyStatus.DISABLED.name())
                .set(QuotaPolicyDO::getUpdatedAt, toLocalDateTime(updatedAt));
        mapper.update(null, wrapper);
    }

    private QuotaPolicy toDomain(QuotaPolicyDO entity) {
        return new QuotaPolicy(
                entity.getPolicyId(),
                entity.getTenantId(),
                entity.getScope() != null ? QuotaScope.valueOf(entity.getScope()) : null,
                entity.getSubjectId(),
                entity.getStatus() != null ? QuotaPolicyStatus.valueOf(entity.getStatus()) : QuotaPolicyStatus.ACTIVE,
                entity.getTokenLimit(),
                entity.getCallLimit(),
                entity.getCostLimit(),
                entity.getWarnRatio() != null ? entity.getWarnRatio() : 0.8,
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt()));
    }

    private QuotaPolicyDO toDO(QuotaPolicy policy) {
        QuotaPolicyDO entity = new QuotaPolicyDO();
        entity.setPolicyId(policy.policyId());
        entity.setTenantId(policy.tenantId());
        entity.setScope(policy.scope() != null ? policy.scope().name() : null);
        entity.setSubjectId(policy.subjectId());
        entity.setStatus(policy.status() != null ? policy.status().name() : QuotaPolicyStatus.ACTIVE.name());
        entity.setTokenLimit(policy.tokenLimit());
        entity.setCallLimit(policy.callLimit());
        entity.setCostLimit(policy.costLimit());
        entity.setWarnRatio(policy.warnRatio());
        entity.setCreatedAt(toLocalDateTime(policy.createdAt()));
        entity.setUpdatedAt(toLocalDateTime(policy.updatedAt()));
        return entity;
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }
}
