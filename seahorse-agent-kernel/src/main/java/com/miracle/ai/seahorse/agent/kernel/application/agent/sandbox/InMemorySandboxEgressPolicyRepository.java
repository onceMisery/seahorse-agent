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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxEgressPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxEgressPolicyRepositoryPort;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版出网策略仓库（从 {@link KernelSandboxRuntimeService} 提取的内部实现）。
 */
final class InMemorySandboxEgressPolicyRepository implements SandboxEgressPolicyRepositoryPort {

    private final Map<String, SandboxEgressPolicy> store = new ConcurrentHashMap<>();

    @Override
    public SandboxEgressPolicy upsert(SandboxEgressPolicy policy) {
        SandboxEgressPolicy safePolicy = Objects.requireNonNull(policy, "policy must not be null");
        store.put(safePolicy.tenantId(), safePolicy);
        return safePolicy;
    }

    @Override
    public Optional<SandboxEgressPolicy> findByTenant(String tenantId) {
        if (!hasText(tenantId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(tenantId.trim()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
