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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxEgressPolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeProfilePolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxEgressPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeProfilePolicyRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 沙箱运行时 Profile / 出网策略管理协作者（从 {@link KernelSandboxRuntimeService} 提取）。
 * 按 §7 收敛原则外提：只负责运行时 Profile 策略与出网策略的查询、生效与落库。
 */
final class SandboxPolicySupport {

    private final SandboxPolicyPort policyPort;
    private final SandboxRuntimeProfilePolicyRepositoryPort runtimeProfilePolicyRepositoryPort;
    private final SandboxEgressPolicyRepositoryPort egressPolicyRepositoryPort;
    private final Clock clock;

    SandboxPolicySupport(SandboxPolicyPort policyPort,
                         SandboxRuntimeProfilePolicyRepositoryPort runtimeProfilePolicyRepositoryPort,
                         SandboxEgressPolicyRepositoryPort egressPolicyRepositoryPort,
                         Clock clock) {
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort must not be null");
        this.runtimeProfilePolicyRepositoryPort = Objects.requireNonNull(
                runtimeProfilePolicyRepositoryPort,
                "runtimeProfilePolicyRepositoryPort must not be null");
        this.egressPolicyRepositoryPort = Objects.requireNonNull(
                egressPolicyRepositoryPort,
                "egressPolicyRepositoryPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    List<SandboxRuntimeProfilePolicy> listRuntimeProfilePolicies(String tenantId) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        Map<SandboxRuntimeType, SandboxRuntimeProfilePolicy> configured = runtimeProfilePolicyRepositoryPort
                .listByTenant(safeTenantId)
                .stream()
                .collect(Collectors.toMap(
                        SandboxRuntimeProfilePolicy::runtimeType,
                        policy -> policy,
                        (left, right) -> left.updatedAt().isAfter(right.updatedAt()) ? left : right));
        Instant now = clock.instant();
        return List.of(SandboxRuntimeType.CODE_INTERPRETER,
                        SandboxRuntimeType.FILE_CONVERSION,
                        SandboxRuntimeType.BROWSER_AUTOMATION,
                        SandboxRuntimeType.SHELL)
                .stream()
                .map(runtimeType -> configured.getOrDefault(
                        runtimeType,
                        SandboxRuntimeProfilePolicy.defaultPolicy(safeTenantId, runtimeType, now)))
                .toList();
    }

    SandboxRuntimeProfilePolicy upsertRuntimeProfilePolicy(SandboxRuntimeProfilePolicyUpsertCommand command) {
        SandboxRuntimeProfilePolicyUpsertCommand safeCommand =
                Objects.requireNonNull(command, "command must not be null");
        String profileId = SandboxSession.profileIdOrDefault(safeCommand.profileId(), safeCommand.runtimeType());
        String supportedProfileId = SandboxSession.profileIdOrDefault(null, safeCommand.runtimeType());
        if (!supportedProfileId.equals(profileId)) {
            throw new IllegalArgumentException("profileId must match the supported sandbox runtime profile");
        }
        long ttlSeconds = safeCommand.sessionTtlSeconds() == null
                ? SandboxRuntimeProfilePolicy.DEFAULT_SESSION_TTL_SECONDS
                : safeCommand.sessionTtlSeconds();
        Instant now = clock.instant();
        Optional<SandboxRuntimeProfilePolicy> existing = existingRuntimeProfilePolicy(safeCommand);
        Instant createdAt = existing.map(SandboxRuntimeProfilePolicy::createdAt).orElse(now);
        SandboxRuntimeProfilePolicy policy = new SandboxRuntimeProfilePolicy(
                existing.map(SandboxRuntimeProfilePolicy::policyId).orElse(safeCommand.policyId()),
                safeCommand.tenantId(),
                safeCommand.runtimeType(),
                profileId,
                safeCommand.status() == null ? SandboxRuntimeProfilePolicyStatus.ACTIVE : safeCommand.status(),
                ttlSeconds,
                Boolean.TRUE.equals(safeCommand.networkAllowed()),
                createdAt,
                now);
        return runtimeProfilePolicyRepositoryPort.upsert(policy);
    }

    SandboxEgressPolicy inspectSandboxEgressPolicy(String tenantId) {
        return effectiveSandboxEgressPolicy(tenantId);
    }

    SandboxEgressPolicy upsertSandboxEgressPolicy(SandboxEgressPolicyUpsertCommand command) {
        SandboxEgressPolicyUpsertCommand safeCommand =
                Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        Optional<SandboxEgressPolicy> existing = egressPolicyRepositoryPort.findByTenant(safeCommand.tenantId());
        SandboxEgressPolicy policy = new SandboxEgressPolicy(
                existing.map(SandboxEgressPolicy::policyId).orElse(safeCommand.policyId()),
                safeCommand.tenantId(),
                safeCommand.networkPolicy(),
                safeCommand.allowlistedHosts(),
                safeCommand.browserPrivateNetworkAllowedHosts(),
                existing.map(SandboxEgressPolicy::createdAt).orElse(now),
                now);
        return egressPolicyRepositoryPort.upsert(policy);
    }

    SandboxRuntimeProfilePolicy effectiveRuntimeProfilePolicy(String tenantId,
                                                              SandboxRuntimeType runtimeType) {
        return runtimeProfilePolicyRepositoryPort.findByTenantAndRuntimeType(tenantId, runtimeType)
                .orElseGet(() -> SandboxRuntimeProfilePolicy.defaultPolicy(tenantId, runtimeType, clock.instant()));
    }

    SandboxEgressPolicy effectiveSandboxEgressPolicy(String tenantId) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        return egressPolicyRepositoryPort.findByTenant(safeTenantId)
                .orElseGet(() -> SandboxEgressPolicy.defaultPolicy(
                        safeTenantId,
                        policyPort.networkPolicy(safeTenantId),
                        policyPort.allowlistedHosts(safeTenantId),
                        policyPort.browserPrivateNetworkAllowedHosts(safeTenantId),
                        clock.instant()));
    }

    private Optional<SandboxRuntimeProfilePolicy> existingRuntimeProfilePolicy(
            SandboxRuntimeProfilePolicyUpsertCommand command) {
        if (hasText(command.policyId())) {
            Optional<SandboxRuntimeProfilePolicy> byId =
                    runtimeProfilePolicyRepositoryPort.findById(command.policyId());
            if (byId.isPresent()) {
                return byId;
            }
        }
        return runtimeProfilePolicyRepositoryPort.findByTenantAndRuntimeType(
                command.tenantId(),
                command.runtimeType());
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
