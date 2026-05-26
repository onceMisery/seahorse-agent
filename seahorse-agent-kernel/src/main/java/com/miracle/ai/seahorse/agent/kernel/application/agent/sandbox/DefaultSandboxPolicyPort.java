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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultSandboxPolicyPort implements SandboxPolicyPort {

    private final SandboxNetworkPolicy networkPolicy;
    private final Set<String> allowlistedHosts;

    public DefaultSandboxPolicyPort(SandboxNetworkPolicy networkPolicy, List<String> allowlistedHosts) {
        this.networkPolicy = Objects.requireNonNullElse(networkPolicy, SandboxNetworkPolicy.DENY_ALL);
        this.allowlistedHosts = normalizeHosts(allowlistedHosts);
    }

    @Override
    public SandboxPolicyDecision decide(SandboxPolicyRequest request) {
        SandboxPolicyRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (!safeRequest.networkRequested()) {
            return SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST);
        }
        if (networkPolicy == SandboxNetworkPolicy.DENY_ALL) {
            return SandboxPolicyDecision.deny(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT);
        }
        if (!allowlistedHosts.containsAll(normalizeHosts(safeRequest.requestedHosts()))) {
            return SandboxPolicyDecision.deny(SandboxPolicyReasonCode.NETWORK_HOST_NOT_ALLOWLISTED);
        }
        return SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST);
    }

    private Set<String> normalizeHosts(List<String> hosts) {
        if (hosts == null) {
            return Set.of();
        }
        return hosts.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
