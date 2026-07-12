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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;

import java.util.List;
import java.util.Objects;

public record SandboxEgressPolicyUpsertCommand(String policyId,
                                               String tenantId,
                                               SandboxNetworkPolicy networkPolicy,
                                               List<String> allowlistedHosts,
                                               List<String> browserPrivateNetworkAllowedHosts) {

    public SandboxEgressPolicyUpsertCommand(String policyId,
                                            String tenantId,
                                            SandboxNetworkPolicy networkPolicy,
                                            List<String> allowlistedHosts) {
        this(policyId, tenantId, networkPolicy, allowlistedHosts, List.of());
    }

    public SandboxEgressPolicyUpsertCommand {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        policyId = policyId == null || policyId.trim().isEmpty() ? null : policyId.trim();
        networkPolicy = Objects.requireNonNullElse(networkPolicy, SandboxNetworkPolicy.DENY_ALL);
        allowlistedHosts = allowlistedHosts == null ? List.of() : List.copyOf(allowlistedHosts);
        browserPrivateNetworkAllowedHosts = browserPrivateNetworkAllowedHosts == null
                ? List.of()
                : List.copyOf(browserPrivateNetworkAllowedHosts);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
