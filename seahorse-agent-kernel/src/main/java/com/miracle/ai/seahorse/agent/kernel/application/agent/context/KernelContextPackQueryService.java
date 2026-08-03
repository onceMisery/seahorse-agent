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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackDiffEntry;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackDiffResult;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackRetentionCleanupResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class KernelContextPackQueryService implements ContextPackInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String CONTEXT_PACK_FORBIDDEN_MESSAGE = "Context pack access denied";

    private final ContextPackRepositoryPort contextPackRepositoryPort;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;

    public KernelContextPackQueryService(ContextPackRepositoryPort contextPackRepositoryPort,
                                         CurrentUserPort currentUserPort) {
        this(contextPackRepositoryPort, currentUserPort, Clock.systemUTC());
    }

    public KernelContextPackQueryService(ContextPackRepositoryPort contextPackRepositoryPort,
                                         CurrentUserPort currentUserPort,
                                         Clock clock) {
        this.contextPackRepositoryPort = Objects.requireNonNull(
                contextPackRepositoryPort,
                "contextPackRepositoryPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Optional<ContextPack> findById(String contextPackId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        return contextPackRepositoryPort.findById(requireText(contextPackId, "contextPackId must not be blank"))
                .map(pack -> requireReadable(pack, currentUser));
    }

    @Override
    public List<ContextItem> listItems(String contextPackId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeContextPackId = requireText(contextPackId, "contextPackId must not be blank");
        Optional<ContextPack> pack = contextPackRepositoryPort.findById(safeContextPackId);
        if (pack.isEmpty()) {
            return List.of();
        }
        requireReadable(pack.orElseThrow(), currentUser);
        return contextPackRepositoryPort.listItems(safeContextPackId);
    }

    @Override
    public ContextPackRetentionCleanupResult cleanupExpiredItems(String contextPackId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeContextPackId = requireText(contextPackId, "contextPackId must not be blank");
        Optional<ContextPack> pack = contextPackRepositoryPort.findById(safeContextPackId);
        if (pack.isEmpty()) {
            return new ContextPackRetentionCleanupResult(safeContextPackId, clock.instant(), 0);
        }
        requireReadable(pack.orElseThrow(), currentUser);
        Instant cutoff = clock.instant();
        int deleted = contextPackRepositoryPort.deleteExpiredItems(safeContextPackId, cutoff);
        return new ContextPackRetentionCleanupResult(safeContextPackId, cutoff, deleted);
    }

    @Override
    public ContextPackDiffResult diff(String leftContextPackId, String rightContextPackId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeLeftContextPackId = requireText(leftContextPackId, "leftContextPackId must not be blank");
        String safeRightContextPackId = requireText(rightContextPackId, "rightContextPackId must not be blank");
        ContextPack leftPack = contextPackRepositoryPort.findById(safeLeftContextPackId)
                .map(pack -> requireReadable(pack, currentUser))
                .orElseThrow(() -> new IllegalArgumentException("left context pack not found"));
        ContextPack rightPack = contextPackRepositoryPort.findById(safeRightContextPackId)
                .map(pack -> requireReadable(pack, currentUser))
                .orElseThrow(() -> new IllegalArgumentException("right context pack not found"));

        Map<String, ContextItem> leftItems = byStableItemKey(contextPackRepositoryPort.listItems(leftPack.contextPackId()));
        Map<String, ContextItem> rightItems = byStableItemKey(contextPackRepositoryPort.listItems(rightPack.contextPackId()));
        List<ContextPackDiffEntry> added = new ArrayList<>();
        List<ContextPackDiffEntry> removed = new ArrayList<>();
        List<ContextPackDiffEntry> changed = new ArrayList<>();
        int unchanged = 0;

        for (Map.Entry<String, ContextItem> entry : rightItems.entrySet()) {
            ContextItem left = leftItems.get(entry.getKey());
            ContextItem right = entry.getValue();
            if (left == null) {
                added.add(diffEntry(entry.getKey(), null, right, List.of()));
                continue;
            }
            List<String> changedFields = changedFields(left, right);
            if (changedFields.isEmpty()) {
                unchanged++;
            } else {
                changed.add(diffEntry(entry.getKey(), left, right, changedFields));
            }
        }
        for (Map.Entry<String, ContextItem> entry : leftItems.entrySet()) {
            if (!rightItems.containsKey(entry.getKey())) {
                removed.add(diffEntry(entry.getKey(), entry.getValue(), null, List.of()));
            }
        }

        Comparator<ContextPackDiffEntry> byKey = Comparator.comparing(ContextPackDiffEntry::itemKey);
        added.sort(byKey);
        removed.sort(byKey);
        changed.sort(byKey);
        return new ContextPackDiffResult(
                leftPack.contextPackId(),
                rightPack.contextPackId(),
                added.size(),
                removed.size(),
                changed.size(),
                unchanged,
                added,
                removed,
                changed);
    }

    private ContextPack requireReadable(ContextPack pack, CurrentUser currentUser) {
        if (currentUser.hasRole(ADMIN_ROLE) || pack.userId().equals(currentUserId(currentUser))) {
            return pack;
        }
        throw new IllegalStateException(CONTEXT_PACK_FORBIDDEN_MESSAGE);
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String currentUserId(CurrentUser currentUser) {
        return currentUser == null ? null : currentUser.operator();
    }

    private Map<String, ContextItem> byStableItemKey(List<ContextItem> items) {
        Map<String, ContextItem> result = new LinkedHashMap<>();
        Map<String, Integer> keyCounts = new LinkedHashMap<>();
        for (ContextItem item : items == null ? List.<ContextItem>of() : items) {
            String stableKey = itemKey(item);
            int count = keyCounts.merge(stableKey, 1, Integer::sum);
            result.put(count == 1 ? stableKey : stableKey + "#" + count, item);
        }
        return result;
    }

    private ContextPackDiffEntry diffEntry(String itemKey,
                                           ContextItem leftItem,
                                           ContextItem rightItem,
                                           List<String> changedFields) {
        ContextItem item = rightItem == null ? leftItem : rightItem;
        return new ContextPackDiffEntry(
                itemKey,
                item.sourceType().name(),
                item.sourceId(),
                leftItem,
                rightItem,
                changedFields);
    }

    private String itemKey(ContextItem item) {
        return item.sourceType().name() + ":" + item.sourceId();
    }

    private List<String> changedFields(ContextItem left, ContextItem right) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "content", left.content(), right.content());
        addIfChanged(fields, "summary", left.summary(), right.summary());
        addIfChanged(fields, "score", left.score(), right.score());
        addIfChanged(fields, "confidence", left.confidence(), right.confidence());
        addIfChanged(fields, "sensitivity", left.sensitivity(), right.sensitivity());
        addIfChanged(fields, "aclDecisionId", left.aclDecisionId(), right.aclDecisionId());
        addIfChanged(fields, "citationJson", left.citationJson(), right.citationJson());
        addIfChanged(fields, "estimatedTokens", left.estimatedTokens(), right.estimatedTokens());
        addIfChanged(fields, "expiresAt", left.expiresAt(), right.expiresAt());
        return fields;
    }

    private void addIfChanged(List<String> fields, String field, Object left, Object right) {
        if (!Objects.equals(left, right)) {
            fields.add(field);
        }
    }
}
