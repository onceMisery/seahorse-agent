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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeProfilePolicyRepositoryPort;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版运行 Profile 策略仓库（从 {@link KernelSandboxRuntimeService} 提取的内部实现）。
 */
final class InMemorySandboxRuntimeProfilePolicyRepository implements SandboxRuntimeProfilePolicyRepositoryPort {

    private final Map<String, SandboxRuntimeProfilePolicy> store = new ConcurrentHashMap<>();

    @Override
    public SandboxRuntimeProfilePolicy upsert(SandboxRuntimeProfilePolicy policy) {
        SandboxRuntimeProfilePolicy safePolicy = Objects.requireNonNull(policy, "policy must not be null");
        store.put(safePolicy.policyId(), safePolicy);
        return safePolicy;
    }

    @Override
    public Optional<SandboxRuntimeProfilePolicy> findById(String policyId) {
        if (!hasText(policyId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(policyId.trim()));
    }

    @Override
    public Optional<SandboxRuntimeProfilePolicy> findByTenantAndRuntimeType(String tenantId,
                                                                            SandboxRuntimeType runtimeType) {
        if (!hasText(tenantId) || runtimeType == null) {
            return Optional.empty();
        }
        String safeTenantId = tenantId.trim();
        return store.values().stream()
                .filter(policy -> policy.tenantId().equals(safeTenantId))
                .filter(policy -> policy.runtimeType() == runtimeType)
                .max(Comparator.comparing(SandboxRuntimeProfilePolicy::updatedAt)
                        .thenComparing(SandboxRuntimeProfilePolicy::policyId));
    }

    @Override
    public List<SandboxRuntimeProfilePolicy> listByTenant(String tenantId) {
        if (!hasText(tenantId)) {
            return List.of();
        }
        String safeTenantId = tenantId.trim();
        return store.values().stream()
                .filter(policy -> policy.tenantId().equals(safeTenantId))
                .sorted(Comparator.comparing(SandboxRuntimeProfilePolicy::runtimeType)
                        .thenComparing(SandboxRuntimeProfilePolicy::updatedAt)
                        .thenComparing(SandboxRuntimeProfilePolicy::policyId))
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
