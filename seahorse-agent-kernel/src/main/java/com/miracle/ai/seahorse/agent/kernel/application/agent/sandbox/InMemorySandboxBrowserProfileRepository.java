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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxBrowserProfile;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxBrowserProfileRepositoryPort;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版浏览器 Profile 仓库（从 {@link KernelSandboxRuntimeService} 提取的内部实现）。
 */
final class InMemorySandboxBrowserProfileRepository implements SandboxBrowserProfileRepositoryPort {

    private final Map<String, SandboxBrowserProfile> store = new ConcurrentHashMap<>();

    @Override
    public SandboxBrowserProfile save(SandboxBrowserProfile profile) {
        SandboxBrowserProfile safeProfile = Objects.requireNonNull(profile, "profile must not be null");
        store.put(safeProfile.tenantId() + "|" + safeProfile.profileId(), safeProfile);
        return safeProfile;
    }

    @Override
    public Optional<SandboxBrowserProfile> findByTenantAndProfileId(String tenantId, String profileId) {
        if (!hasText(tenantId) || !hasText(profileId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(tenantId.trim() + "|" + profileId.trim()));
    }

    @Override
    public List<SandboxBrowserProfile> listByTenant(String tenantId, int limit) {
        if (!hasText(tenantId) || limit <= 0) {
            return List.of();
        }
        String safeTenantId = tenantId.trim();
        return store.values().stream()
                .filter(profile -> profile.tenantId().equals(safeTenantId))
                .sorted(Comparator.comparing(SandboxBrowserProfile::updatedAt).reversed()
                        .thenComparing(SandboxBrowserProfile::profileId))
                .limit(limit)
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
