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

package com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolutionRunResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MemoryAliasResolutionService {

    private static final String SOURCE_TYPE = "alias_maintenance";

    private final MemoryAliasPort aliasPort;
    private final MemoryAliasResolutionOptions options;

    public MemoryAliasResolutionService() {
        this(MemoryAliasPort.noop(), MemoryAliasResolutionOptions.defaults());
    }

    public MemoryAliasResolutionService(MemoryAliasPort aliasPort, MemoryAliasResolutionOptions options) {
        this.aliasPort = Objects.requireNonNullElseGet(aliasPort, MemoryAliasPort::noop);
        this.options = Objects.requireNonNullElseGet(options, MemoryAliasResolutionOptions::defaults);
    }

    public MemoryAliasResolutionRunResult run(String reason) {
        Instant now = Instant.now();
        List<String> errors = new ArrayList<>();
        List<MemoryAliasCandidate> candidates = scan(errors);
        int normalized = 0;
        int dictionaryMatches = 0;
        int skipped = 0;
        for (MemoryAliasCandidate candidate : candidates) {
            ApplyResult result = applyCandidate(candidate, errors);
            normalized += result.applied() ? 1 : 0;
            skipped += result.skipped() ? 1 : 0;
        }
        for (Map.Entry<String, MemoryAliasCandidate> entry : options.dictionary().entrySet()) {
            ApplyResult result = applyDictionaryEntry(entry.getKey(), entry.getValue(), errors);
            dictionaryMatches += result.applied() ? 1 : 0;
            skipped += result.skipped() ? 1 : 0;
        }
        return new MemoryAliasResolutionRunResult(
                Objects.requireNonNullElse(reason, "manual-alias-resolution"),
                candidates.size(),
                normalized,
                dictionaryMatches,
                skipped,
                errors,
                now);
    }

    private List<MemoryAliasCandidate> scan(List<String> errors) {
        try {
            if (hasText(options.userId())) {
                return aliasPort.findMergeCandidates(options.userId(), options.tenantId(), options.scanLimit());
            }
            return aliasPort.findMergeCandidates(options.scanLimit());
        } catch (RuntimeException ex) {
            errors.add("scan:" + errorMessage(ex));
            return List.of();
        }
    }

    private ApplyResult applyCandidate(MemoryAliasCandidate candidate, List<String> errors) {
        if (!autoResolvable(candidate)) {
            return ApplyResult.skip();
        }
        String normalizedAlias = normalizeAlias(candidate.aliasText());
        if (normalizedAlias.isBlank() || normalizedAlias.equals(candidate.aliasText())) {
            return ApplyResult.ignored();
        }
        return upsertAlias(normalizedAlias, candidate,
                metadata(candidate, "trim_case_whitespace", candidate.aliasText()), errors);
    }

    private ApplyResult applyDictionaryEntry(String aliasText,
                                             MemoryAliasCandidate candidate,
                                             List<String> errors) {
        String normalizedAlias = normalizeAlias(aliasText);
        if (normalizedAlias.isBlank() || !autoResolvable(candidate)) {
            return ApplyResult.skip();
        }
        return upsertAlias(normalizedAlias, candidate, metadata(candidate, "dictionary", aliasText), errors);
    }

    private boolean autoResolvable(MemoryAliasCandidate candidate) {
        return candidate != null
                && hasText(candidate.canonicalEntityId())
                && candidate.confidenceLevel() >= options.autoResolveConfidenceThreshold();
    }

    private ApplyResult upsertAlias(String aliasText,
                                    MemoryAliasCandidate candidate,
                                    Map<String, Object> metadata,
                                    List<String> errors) {
        try {
            String targetUserId = hasText(options.userId()) ? options.userId() : candidate.userId();
            String targetTenantId = hasText(options.userId()) ? options.tenantId() : candidate.tenantId();
            if (!hasText(targetUserId)) {
                errors.add(aliasText + ":missing-user-scope");
                return ApplyResult.skip();
            }
            aliasPort.upsertAlias(new MemoryAliasCommand(
                    targetUserId,
                    targetTenantId,
                    aliasText,
                    candidate.canonicalEntityId(),
                    candidate.canonicalName(),
                    candidate.entityType(),
                    candidate.confidenceLevel(),
                    SOURCE_TYPE,
                    candidate.sourceMemoryIds(),
                    metadata));
            return ApplyResult.apply();
        } catch (RuntimeException ex) {
            errors.add(aliasText + ":" + errorMessage(ex));
            return ApplyResult.skip();
        }
    }

    private String normalizeAlias(String aliasText) {
        return Objects.requireNonNullElse(aliasText, "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> metadata(MemoryAliasCandidate candidate,
                                         String normalizationStrategy,
                                         String originalAlias) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalAlias", Objects.requireNonNullElse(originalAlias, ""));
        metadata.put("normalizationStrategy", normalizationStrategy);
        metadata.put("canonicalEntityId", candidate.canonicalEntityId());
        metadata.put("canonicalName", candidate.canonicalName());
        metadata.put("entityType", candidate.entityType());
        metadata.put("confidenceLevel", candidate.confidenceLevel());
        metadata.put("sourceMemoryIds", candidate.sourceMemoryIds());
        metadata.put("sourceType", SOURCE_TYPE);
        return metadata;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
    }

    private record ApplyResult(boolean applied, boolean skipped) {

        static ApplyResult apply() {
            return new ApplyResult(true, false);
        }

        static ApplyResult skip() {
            return new ApplyResult(false, true);
        }

        static ApplyResult ignored() {
            return new ApplyResult(false, false);
        }
    }
}
