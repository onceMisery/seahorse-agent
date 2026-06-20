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
import io.a2a.spec.AgentSkill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class A2ATenantMetadata {

    public static final String TENANT_SKILL_ID = "seahorse.tenant-boundary";
    public static final String TENANT_TAG_PREFIX = "seahorse:tenant:";
    public static final String M3_TAG_PREFIX = "seahorse:m3:";

    private A2ATenantMetadata() {
    }

    public static AgentCard withTenant(AgentCard card, String tenantId, Map<String, String> m3Metadata) {
        AgentCard safeCard = Objects.requireNonNull(card, "card must not be null");
        String safeTenantId = requiredText(tenantId, "tenantId");
        List<AgentSkill> skills = new ArrayList<>(safeSkills(safeCard.skills()));
        skills.removeIf(skill -> TENANT_SKILL_ID.equals(skill.id()));
        skills.add(boundarySkill(safeTenantId, m3Metadata));
        return new AgentCard.Builder()
                .protocolVersion(safeCard.protocolVersion())
                .name(safeCard.name())
                .description(safeCard.description())
                .version(safeCard.version())
                .iconUrl(safeCard.iconUrl())
                .capabilities(safeCard.capabilities())
                .skills(List.copyOf(skills))
                .url(safeCard.url())
                .preferredTransport(safeCard.preferredTransport())
                .additionalInterfaces(safeCard.additionalInterfaces())
                .provider(safeCard.provider())
                .documentationUrl(safeCard.documentationUrl())
                .securitySchemes(safeCard.securitySchemes())
                .security(safeCard.security())
                .defaultInputModes(safeCard.defaultInputModes())
                .defaultOutputModes(safeCard.defaultOutputModes())
                .supportsAuthenticatedExtendedCard(safeCard.supportsAuthenticatedExtendedCard())
                .build();
    }

    public static Optional<String> tenantId(AgentCard card) {
        if (card == null) {
            return Optional.empty();
        }
        return safeSkills(card.skills()).stream()
                .filter(skill -> TENANT_SKILL_ID.equals(skill.id()))
                .flatMap(skill -> safeTags(skill.tags()).stream())
                .filter(tag -> tag.startsWith(TENANT_TAG_PREFIX))
                .map(tag -> tag.substring(TENANT_TAG_PREFIX.length()))
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    public static String tenantQualifiedAgentName(String tenantId, String agentName) {
        String safeTenantId = requiredText(tenantId, "tenantId");
        String safeAgentName = requiredText(agentName, "agentName");
        if (safeAgentName.startsWith(safeTenantId + "/")) {
            return safeAgentName;
        }
        return safeTenantId + "/" + safeAgentName;
    }

    static AgentSkill boundarySkill(String tenantId, Map<String, String> m3Metadata) {
        List<String> tags = new ArrayList<>();
        tags.add(TENANT_TAG_PREFIX + tenantId);
        Objects.requireNonNullElse(m3Metadata, Map.<String, String>of()).forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                tags.add(M3_TAG_PREFIX + key.trim() + "=" + value.trim());
            }
        });
        return new AgentSkill.Builder()
                .id(TENANT_SKILL_ID)
                .name("Seahorse tenant boundary")
                .description("Tenant isolation metadata injected by Seahorse.")
                .tags(List.copyOf(tags))
                .build();
    }

    private static List<AgentSkill> safeSkills(List<AgentSkill> skills) {
        return skills == null ? List.of() : skills.stream().filter(Objects::nonNull).toList();
    }

    private static List<String> safeTags(List<String> tags) {
        return tags == null ? List.of() : tags.stream().filter(Objects::nonNull).toList();
    }

    private static String requiredText(String value, String fieldName) {
        String trimmed = Objects.requireNonNullElse(value, "").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
