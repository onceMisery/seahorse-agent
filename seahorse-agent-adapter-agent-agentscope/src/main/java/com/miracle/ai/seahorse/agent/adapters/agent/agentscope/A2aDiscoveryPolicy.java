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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class A2aDiscoveryPolicy {

    private static final String JSONRPC_TRANSPORT = "JSONRPC";

    private final Map<String, String> preferredM3;
    private final A2aEndpointHealthProbe healthProbe;
    private final Clock clock;

    private A2aDiscoveryPolicy(Map<String, String> preferredM3, A2aEndpointHealthProbe healthProbe, Clock clock) {
        this.preferredM3 = Map.copyOf(Objects.requireNonNullElse(preferredM3, Map.of()));
        this.healthProbe = Objects.requireNonNullElseGet(healthProbe, A2aEndpointHealthProbe::unknown);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    public static A2aDiscoveryPolicy none() {
        return new A2aDiscoveryPolicy(Map.of(), A2aEndpointHealthProbe.unknown(), Clock.systemUTC());
    }

    public static A2aDiscoveryPolicy preferM3(Map<String, String> preferredM3) {
        return new A2aDiscoveryPolicy(preferredM3, A2aEndpointHealthProbe.unknown(), Clock.systemUTC());
    }

    public static A2aDiscoveryPolicy preferM3(
            Map<String, String> preferredM3,
            A2aEndpointHealthProbe healthProbe) {
        return new A2aDiscoveryPolicy(preferredM3, healthProbe, Clock.systemUTC());
    }

    static A2aDiscoveryPolicy withClock(
            Map<String, String> preferredM3,
            A2aEndpointHealthProbe healthProbe,
            Clock clock) {
        return new A2aDiscoveryPolicy(preferredM3, healthProbe, clock);
    }

    public static A2aDiscoveryPolicy fromProperties(AgentScopeProperties properties) {
        if (properties == null) {
            return none();
        }
        Map<String, String> preferred = new LinkedHashMap<>();
        if (properties.getNacos() == null || properties.getNacos().getM3() == null) {
            return new A2aDiscoveryPolicy(preferred, A2aEndpointHealthProbe.http(java.time.Duration.ofMillis(800)),
                    Clock.systemUTC());
        }
        AgentScopeProperties.M3 m3 = properties.getNacos().getM3();
        if (m3.isEnabled()) {
            putIfText(preferred, "mode", m3.getMode());
            putIfText(preferred, "namespace", m3.getNamespace());
            putIfText(preferred, "group", m3.getGroup());
            putIfText(preferred, "clusterName", m3.getClusterName());
            m3.getMetadata().forEach((key, value) -> putIfText(preferred, key, value));
        }
        return new A2aDiscoveryPolicy(preferred, A2aEndpointHealthProbe.http(java.time.Duration.ofMillis(800)),
                Clock.systemUTC());
    }

    public AgentCard select(List<AgentCard> candidates) {
        List<AgentCard> safeCandidates = Objects.requireNonNullElse(candidates, List.<AgentCard>of()).stream()
                .filter(Objects::nonNull)
                .toList();
        if (safeCandidates.isEmpty()) {
            return null;
        }
        if (safeCandidates.size() == 1) {
            return safeCandidates.get(0);
        }
        return safeCandidates.stream()
                .max(Comparator.comparingInt(this::freshnessScore)
                        .thenComparingInt(this::healthScore)
                        .thenComparingInt(this::m3Score))
                .orElse(safeCandidates.get(0));
    }

    private int freshnessScore(AgentCard card) {
        String expiresAt = a2aMetadata(card).get("expiresAt");
        if (expiresAt == null || expiresAt.isBlank()) {
            return 1;
        }
        try {
            return Instant.parse(expiresAt.trim()).isAfter(clock.instant()) ? 2 : 0;
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    private int healthScore(AgentCard card) {
        return switch (healthProbe.check(healthUrl(card))) {
            case UP -> 2;
            case UNKNOWN -> 1;
            case DOWN -> 0;
        };
    }

    private int m3Score(AgentCard card) {
        if (preferredM3.isEmpty()) {
            return 0;
        }
        Map<String, String> metadata = m3Metadata(card);
        int score = 0;
        for (Map.Entry<String, String> entry : preferredM3.entrySet()) {
            String actual = metadata.get(entry.getKey());
            if (actual != null && actual.equalsIgnoreCase(entry.getValue())) {
                score++;
            }
        }
        return score;
    }

    private String healthUrl(AgentCard card) {
        String tagged = a2aMetadata(card).get("healthUrl");
        if (tagged != null && !tagged.isBlank()) {
            return tagged;
        }
        String effectiveUrl = effectiveUrl(card);
        if (effectiveUrl == null || effectiveUrl.isBlank()) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(effectiveUrl.trim());
            StringBuilder builder = new StringBuilder();
            builder.append(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() > 0) {
                builder.append(':').append(uri.getPort());
            }
            return builder.append("/actuator/health").toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String effectiveUrl(AgentCard card) {
        if (card == null) {
            return "";
        }
        if (card.additionalInterfaces() != null) {
            for (AgentInterface agentInterface : card.additionalInterfaces()) {
                if (agentInterface != null
                        && JSONRPC_TRANSPORT.equalsIgnoreCase(agentInterface.transport())
                        && agentInterface.url() != null
                        && !agentInterface.url().trim().isEmpty()) {
                    return agentInterface.url().trim();
                }
            }
        }
        return Objects.requireNonNullElse(card.url(), "");
    }

    private Map<String, String> a2aMetadata(AgentCard card) {
        Map<String, String> result = new LinkedHashMap<>();
        if (card == null || card.skills() == null) {
            return result;
        }
        for (AgentSkill skill : card.skills()) {
            if (skill == null || skill.tags() == null) {
                continue;
            }
            for (String tag : skill.tags()) {
                if (tag == null || !tag.startsWith(A2ATenantMetadata.A2A_TAG_PREFIX)) {
                    continue;
                }
                String pair = tag.substring(A2ATenantMetadata.A2A_TAG_PREFIX.length());
                int separator = pair.indexOf('=');
                if (separator <= 0 || separator == pair.length() - 1) {
                    continue;
                }
                result.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return result;
    }

    private Map<String, String> m3Metadata(AgentCard card) {
        Map<String, String> result = new LinkedHashMap<>();
        if (card == null || card.skills() == null) {
            return result;
        }
        for (AgentSkill skill : card.skills()) {
            if (skill == null || !A2ATenantMetadata.TENANT_SKILL_ID.equals(skill.id()) || skill.tags() == null) {
                continue;
            }
            for (String tag : skill.tags()) {
                if (tag == null || !tag.startsWith(A2ATenantMetadata.M3_TAG_PREFIX)) {
                    continue;
                }
                String pair = tag.substring(A2ATenantMetadata.M3_TAG_PREFIX.length());
                int separator = pair.indexOf('=');
                if (separator <= 0 || separator == pair.length() - 1) {
                    continue;
                }
                result.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return result;
    }

    private static void putIfText(Map<String, String> target, String key, String value) {
        if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
            target.put(key.trim(), value.trim());
        }
    }
}
