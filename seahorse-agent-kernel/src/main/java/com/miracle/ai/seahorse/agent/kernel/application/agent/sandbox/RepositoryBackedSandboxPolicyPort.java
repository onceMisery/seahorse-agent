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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxEgressPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class RepositoryBackedSandboxPolicyPort implements SandboxPolicyPort {

    private static final String DEFAULT_TENANT_ID = "default";

    private final SandboxNetworkPolicy defaultNetworkPolicy;
    private final List<String> defaultAllowlistedHosts;
    private final List<String> defaultBrowserPrivateNetworkAllowedHosts;
    private final SandboxEgressPolicyRepositoryPort repositoryPort;

    public RepositoryBackedSandboxPolicyPort(SandboxNetworkPolicy defaultNetworkPolicy,
                                             List<String> defaultAllowlistedHosts,
                                             SandboxEgressPolicyRepositoryPort repositoryPort) {
        this(defaultNetworkPolicy, defaultAllowlistedHosts, List.of(), repositoryPort);
    }

    public RepositoryBackedSandboxPolicyPort(SandboxNetworkPolicy defaultNetworkPolicy,
                                             List<String> defaultAllowlistedHosts,
                                             List<String> defaultBrowserPrivateNetworkAllowedHosts,
                                             SandboxEgressPolicyRepositoryPort repositoryPort) {
        this.defaultNetworkPolicy = Objects.requireNonNullElse(defaultNetworkPolicy, SandboxNetworkPolicy.DENY_ALL);
        this.defaultAllowlistedHosts = SandboxEgressPolicy.normalizeHosts(defaultAllowlistedHosts);
        this.defaultBrowserPrivateNetworkAllowedHosts =
                SandboxEgressPolicy.normalizeHosts(defaultBrowserPrivateNetworkAllowedHosts);
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    @Override
    public SandboxPolicyDecision decide(SandboxPolicyRequest request) {
        SandboxPolicyRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (!safeRequest.networkRequested()) {
            return SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST);
        }
        SandboxEgressPolicy policy = effectivePolicy(safeRequest.tenantId());
        if (policy.networkPolicy() == SandboxNetworkPolicy.DENY_ALL) {
            return SandboxPolicyDecision.deny(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT);
        }
        if (!policy.allowsRequestedHosts(safeRequest.requestedHosts())) {
            return SandboxPolicyDecision.deny(SandboxPolicyReasonCode.NETWORK_HOST_NOT_ALLOWLISTED);
        }
        return SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST);
    }

    @Override
    public SandboxNetworkPolicy networkPolicy() {
        return networkPolicy(DEFAULT_TENANT_ID);
    }

    @Override
    public SandboxNetworkPolicy networkPolicy(String tenantId) {
        return effectivePolicy(tenantId).networkPolicy();
    }

    @Override
    public List<String> allowlistedHosts() {
        return allowlistedHosts(DEFAULT_TENANT_ID);
    }

    @Override
    public List<String> allowlistedHosts(String tenantId) {
        return effectivePolicy(tenantId).allowlistedHosts();
    }

    @Override
    public List<String> browserPrivateNetworkAllowedHosts() {
        return browserPrivateNetworkAllowedHosts(DEFAULT_TENANT_ID);
    }

    @Override
    public List<String> browserPrivateNetworkAllowedHosts(String tenantId) {
        return effectivePolicy(tenantId).browserPrivateNetworkAllowedHosts();
    }

    private SandboxEgressPolicy effectivePolicy(String tenantId) {
        String safeTenantId = tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId.trim();
        return repositoryPort.findByTenant(safeTenantId)
                .orElseGet(() -> SandboxEgressPolicy.defaultPolicy(
                        safeTenantId,
                        defaultNetworkPolicy,
                        defaultAllowlistedHosts,
                        defaultBrowserPrivateNetworkAllowedHosts,
                        Instant.EPOCH));
    }
}
