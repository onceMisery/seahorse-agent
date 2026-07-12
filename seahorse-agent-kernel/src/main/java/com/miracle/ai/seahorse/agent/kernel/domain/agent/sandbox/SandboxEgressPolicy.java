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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record SandboxEgressPolicy(String policyId,
                                  String tenantId,
                                  SandboxNetworkPolicy networkPolicy,
                                  List<String> allowlistedHosts,
                                  List<String> browserPrivateNetworkAllowedHosts,
                                  Instant createdAt,
                                  Instant updatedAt) {

    public static final int MAX_ALLOWLISTED_HOSTS = 64;
    public static final int MAX_HOST_LENGTH = 253;
    private static final Pattern HOST_PATTERN = Pattern.compile("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?");

    public SandboxEgressPolicy {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        policyId = hasText(policyId) ? policyId.trim() : defaultPolicyId(tenantId);
        networkPolicy = Objects.requireNonNullElse(networkPolicy, SandboxNetworkPolicy.DENY_ALL);
        allowlistedHosts = normalizeHosts(allowlistedHosts);
        browserPrivateNetworkAllowedHosts = normalizeHosts(browserPrivateNetworkAllowedHosts);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNullElse(updatedAt, createdAt);
    }

    public static SandboxEgressPolicy defaultPolicy(String tenantId,
                                                    SandboxNetworkPolicy networkPolicy,
                                                    List<String> allowlistedHosts,
                                                    List<String> browserPrivateNetworkAllowedHosts,
                                                    Instant now) {
        return new SandboxEgressPolicy(
                defaultPolicyId(tenantId),
                tenantId,
                networkPolicy,
                allowlistedHosts,
                browserPrivateNetworkAllowedHosts,
                now,
                now);
    }

    public static String defaultPolicyId(String tenantId) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return "sandbox-egress-policy-" + safeTenantId;
    }

    public boolean allowsRequestedHosts(List<String> requestedHosts) {
        if (networkPolicy == SandboxNetworkPolicy.DENY_ALL) {
            return requestedHosts == null || requestedHosts.isEmpty();
        }
        Set<String> allowed = Set.copyOf(allowlistedHosts);
        return allowed.containsAll(normalizeHosts(requestedHosts));
    }

    public static List<String> normalizeHosts(List<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return List.of();
        }
        if (hosts.size() > MAX_ALLOWLISTED_HOSTS) {
            throw new IllegalArgumentException("allowlistedHosts must contain at most 64 hosts");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String host : hosts) {
            String value = normalizeHost(host);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized.stream().sorted().toList();
    }

    private static String normalizeHost(String host) {
        if (!hasText(host)) {
            return null;
        }
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_HOST_LENGTH || value.contains("://") || value.contains("/")
                || value.contains("\\") || value.contains("@") || value.contains(":")
                || value.startsWith(".") || value.endsWith(".") || value.contains("..")
                || !HOST_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("allowlistedHosts must contain host names only");
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
