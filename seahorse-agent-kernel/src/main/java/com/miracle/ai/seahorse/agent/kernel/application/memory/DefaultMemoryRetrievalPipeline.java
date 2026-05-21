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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class DefaultMemoryRetrievalPipeline implements MemoryRetrievalPipelinePort {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMemoryRetrievalPipeline.class);

    private final ShortTermMemoryPort shortTermPort;
    private final LongTermMemoryPort longTermPort;
    private final SemanticMemoryPort semanticPort;
    private final ObjectMapper objectMapper;
    private final MemoryEngineOptions options;
    private final ProfileMemoryPort profileMemoryPort;
    private final CorrectionLedgerPort correctionLedgerPort;
    private final MemoryRouterPort memoryRouterPort;
    private final MemoryVectorPort memoryVectorPort;
    private final MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort;
    private final MemoryLifecyclePort memoryLifecyclePort;
    private final ProfileSlotResolver profileSlotResolver;

    public DefaultMemoryRetrievalPipeline(ShortTermMemoryPort shortTermPort,
                                          LongTermMemoryPort longTermPort,
                                          SemanticMemoryPort semanticPort,
                                          ObjectMapper objectMapper,
                                          MemoryEngineOptions options,
                                          ProfileMemoryPort profileMemoryPort,
                                          CorrectionLedgerPort correctionLedgerPort,
                                          MemoryRouterPort memoryRouterPort,
                                          MemoryVectorPort memoryVectorPort,
                                          MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                                          MemoryLifecyclePort memoryLifecyclePort) {
        this.shortTermPort = Objects.requireNonNull(shortTermPort, "shortTermPort must not be null");
        this.longTermPort = Objects.requireNonNull(longTermPort, "longTermPort must not be null");
        this.semanticPort = Objects.requireNonNull(semanticPort, "semanticPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.options = Objects.requireNonNullElseGet(options, MemoryEngineOptions::defaults);
        this.profileMemoryPort = Objects.requireNonNull(profileMemoryPort, "profileMemoryPort must not be null");
        this.correctionLedgerPort = Objects.requireNonNull(correctionLedgerPort, "correctionLedgerPort must not be null");
        this.memoryRouterPort = Objects.requireNonNull(memoryRouterPort, "memoryRouterPort must not be null");
        this.memoryVectorPort = Objects.requireNonNull(memoryVectorPort, "memoryVectorPort must not be null");
        this.businessDocumentRetrieverPort = Objects.requireNonNull(businessDocumentRetrieverPort,
                "businessDocumentRetrieverPort must not be null");
        this.memoryLifecyclePort = Objects.requireNonNull(memoryLifecyclePort, "memoryLifecyclePort must not be null");
        this.profileSlotResolver = new ProfileSlotResolver();
    }

    @Override
    public MemoryContext load(MemoryLoadRequest request) {
        if (request == null || isBlank(request.userId())) {
            return emptyContext(request);
        }

        String userId = request.userId();
        var routePlan = memoryRouterPort.route(new MemoryRouteRequest(userId, "default", request.currentQuestion()));
        boolean loadCorrection = routePlan.isActive(MemoryTrack.CORRECTION);
        boolean loadProfile = routePlan.isActive(MemoryTrack.PROFILE);
        boolean loadEpisodic = routePlan.isActive(MemoryTrack.EPISODIC);
        boolean loadBusinessDocument = routePlan.isActive(MemoryTrack.BUSINESS_DOCUMENT);
        boolean loadShortWindow = routePlan.isActive(MemoryTrack.SHORT_WINDOW);

        List<MemoryItem> shortTerm = loadShortWindow
                ? loadLayer(shortTermPort, userId, options.shortTermLimit(), MemoryLayer.SHORT_TERM)
                : Collections.emptyList();
        List<MemoryItem> longTerm = loadEpisodic
                ? loadLayer(longTermPort, userId, options.longTermLimit(), MemoryLayer.LONG_TERM)
                : Collections.emptyList();
        List<MemoryItem> semantic = loadEpisodic
                ? loadLayer(semanticPort, userId, options.semanticLimit(), MemoryLayer.SEMANTIC)
                : Collections.emptyList();
        List<MemoryItem> corrections = loadCorrection ? loadCorrections(userId) : Collections.emptyList();
        List<MemoryItem> profile = loadProfile ? loadProfileFacts(userId) : Collections.emptyList();
        Set<String> correctionProfileSlots = correctionProfileSlots(corrections);
        if (!correctionProfileSlots.isEmpty()) {
            profile = removeActiveProfileSlotMemories(profile, correctionProfileSlots);
        }
        int vectorHitCount = 0;
        if (loadEpisodic) {
            List<MemoryItem> vectorHits = loadVectorHitMemories(userId, request.currentQuestion(),
                    options.semanticLimit());
            vectorHitCount = vectorHits.size();
            shortTerm = mergeById(shortTerm, filterByLayer(vectorHits, MemoryLayer.SHORT_TERM));
            longTerm = mergeById(longTerm, filterByLayer(vectorHits, MemoryLayer.LONG_TERM));
            semantic = mergeById(semantic, filterByLayer(vectorHits, MemoryLayer.SEMANTIC));
        }
        List<MemoryItem> businessDocuments = Collections.emptyList();
        if (loadBusinessDocument) {
            businessDocuments = loadBusinessDocuments(userId, request.currentQuestion(), options.semanticLimit());
        }
        Set<String> activeProfileSlots = activeProfileSlots(profile);
        Set<String> suppressedProfileSlots = new LinkedHashSet<>();
        suppressedProfileSlots.addAll(activeProfileSlots);
        suppressedProfileSlots.addAll(correctionProfileSlots);
        if (!suppressedProfileSlots.isEmpty()) {
            shortTerm = removeActiveProfileSlotMemories(shortTerm, suppressedProfileSlots);
            longTerm = removeActiveProfileSlotMemories(longTerm, suppressedProfileSlots);
            semantic = removeActiveProfileSlotMemories(semantic, suppressedProfileSlots);
        }
        shortTerm = deduplicateById(shortTerm);
        longTerm = deduplicateProfileSlots(deduplicateById(longTerm));
        semantic = deduplicateProfileSlots(deduplicateById(semantic));
        businessDocuments = deduplicateById(businessDocuments);
        logRetrievalSummary(userId, routePlan.activeTracks(), corrections, profile, shortTerm, businessDocuments,
                longTerm, semantic, vectorHitCount, suppressedProfileSlots);
        Instant referencedAt = Instant.now();
        recordProfileReadFeedback(profile, referencedAt);
        recordLayerReadFeedback(shortTerm, longTerm, semantic, referencedAt);

        return MemoryContext.builder()
                .conversationId(request.conversationId())
                .userId(userId)
                .currentQuestion(request.currentQuestion())
                .workingMemory(Collections.emptyList())
                .correctionMemories(corrections)
                .profileMemories(profile)
                .shortTermMemories(shortTerm)
                .businessDocumentMemories(businessDocuments)
                .longTermMemories(longTerm)
                .semanticMemories(semantic)
                .promptMessages(Collections.emptyList())
                .build();
    }

    private void logRetrievalSummary(String userId,
                                     Set<MemoryTrack> activeTracks,
                                     List<MemoryItem> corrections,
                                     List<MemoryItem> profile,
                                     List<MemoryItem> shortTerm,
                                     List<MemoryItem> businessDocuments,
                                     List<MemoryItem> longTerm,
                                     List<MemoryItem> semantic,
                                     int vectorHitCount,
                                     Set<String> suppressedProfileSlots) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug("memory retrieval summary: userId={}, tracks={}, corrections={}, profile={}, shortTerm={}, "
                        + "businessDocuments={}, longTerm={}, semantic={}, vectorHits={}, suppressedProfileSlots={}",
                userId,
                activeTracks,
                safeSize(corrections),
                safeSize(profile),
                safeSize(shortTerm),
                safeSize(businessDocuments),
                safeSize(longTerm),
                safeSize(semantic),
                vectorHitCount,
                suppressedProfileSlots);
    }

    private List<MemoryItem> loadCorrections(String userId) {
        try {
            return correctionLedgerPort.listActive(userId, "default", options.semanticLimit()).stream()
                    .map(this::toCorrectionItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("加载Correction Ledger失败: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private List<MemoryItem> loadProfileFacts(String userId) {
        try {
            return profileMemoryPort.listActive(userId, "default", options.semanticLimit()).stream()
                    .map(this::toProfileItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("加载Profile KV失败: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private List<MemoryItem> loadVectorHitMemories(String userId, String question, int limit) {
        if (isBlank(question)) {
            return Collections.emptyList();
        }
        try {
            return memoryVectorPort.search(userId, question, limit).stream()
                    .map(this::findMemoryById)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("向量召回记忆失败: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private Optional<MemoryItem> findMemoryById(String memoryId) {
        if (isBlank(memoryId)) {
            return Optional.empty();
        }
        Optional<MemoryRecord> shortTerm = safeFindById(shortTermPort, memoryId);
        if (shortTerm.isPresent()) {
            return shortTerm.map(record -> toMemoryItem(record, MemoryLayer.SHORT_TERM));
        }
        Optional<MemoryRecord> longTerm = safeFindById(longTermPort, memoryId);
        if (longTerm.isPresent()) {
            return longTerm.map(record -> toMemoryItem(record, MemoryLayer.LONG_TERM));
        }
        return safeFindById(semanticPort, memoryId)
                .map(record -> toMemoryItem(record, MemoryLayer.SEMANTIC));
    }

    private Optional<MemoryRecord> safeFindById(MemoryStorePort port, String memoryId) {
        try {
            return port.findById(memoryId);
        } catch (RuntimeException ex) {
            LOG.debug("按ID读取记忆失败: memoryId={}", memoryId, ex);
            return Optional.empty();
        }
    }

    private List<MemoryItem> loadBusinessDocuments(String userId, String question, int limit) {
        if (isBlank(question)) {
            return Collections.emptyList();
        }
        try {
            return businessDocumentRetrieverPort.retrieve("default", question, limit);
        } catch (RuntimeException ex) {
            LOG.warn("加载业务文档记忆候选失败: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private Set<String> correctionProfileSlots(List<MemoryItem> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return Set.of();
        }
        Set<String> slots = new LinkedHashSet<>();
        for (MemoryItem correction : corrections) {
            String slot = profileSlotResolver.correctionTargetSlot(correction);
            if (!slot.isBlank()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private List<MemoryItem> filterByLayer(List<MemoryItem> items, MemoryLayer layer) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .filter(item -> item != null && item.getLayer() == layer)
                .toList();
    }

    private List<MemoryItem> mergeById(List<MemoryItem> first, List<MemoryItem> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Collections.emptyList();
        }
        List<MemoryItem> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return deduplicateById(merged);
    }

    private MemoryItem toCorrectionItem(CorrectionRule rule) {
        return MemoryItem.builder()
                .id(rule.id())
                .userId(rule.userId())
                .layer(MemoryLayer.SEMANTIC)
                .type("CORRECTION")
                .content(rule.ruleText())
                .metadataJson(serializeMetadata(Map.of(
                        "userId", rule.userId(),
                        "tenantId", rule.tenantId(),
                        "targetKind", rule.targetKind(),
                        "targetKey", rule.targetKey(),
                        "incorrectValue", rule.incorrectValue(),
                        "correctValue", rule.correctValue(),
                        "priority", rule.priority(),
                        "generationId", rule.generationId())))
                .importanceScore(1D)
                .confidenceLevel(1D)
                .createTime(rule.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private MemoryItem toProfileItem(ProfileFact fact) {
        return MemoryItem.builder()
                .id(fact.id())
                .userId(fact.userId())
                .layer(MemoryLayer.SEMANTIC)
                .type("PROFILE")
                .content(fact.valueText())
                .metadataJson(serializeMetadata(Map.of(
                        "userId", fact.userId(),
                        "tenantId", fact.tenantId(),
                        "profileSlot", fact.slotKey(),
                        "sourceType", fact.sourceType(),
                        "generationId", fact.generationId(),
                        "status", fact.status(),
                        "version", fact.version())))
                .importanceScore(1D)
                .confidenceLevel(fact.confidenceLevel())
                .createTime(fact.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private Set<String> activeProfileSlots(List<MemoryItem> profile) {
        if (profile == null || profile.isEmpty()) {
            return Set.of();
        }
        Set<String> slots = new LinkedHashSet<>();
        for (MemoryItem item : profile) {
            String slot = semanticSlot(item);
            if (!slot.isBlank()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private List<MemoryItem> removeActiveProfileSlotMemories(List<MemoryItem> items, Set<String> activeSlots) {
        if (items == null || items.isEmpty() || activeSlots == null || activeSlots.isEmpty()) {
            return items == null ? Collections.emptyList() : items;
        }
        return items.stream()
                .filter(item -> !activeSlots.contains(semanticSlot(item)))
                .toList();
    }

    private void recordProfileReadFeedback(List<MemoryItem> profile, Instant referencedAt) {
        if (profile == null || profile.isEmpty()) {
            return;
        }
        for (MemoryItem item : profile) {
            String slot = semanticSlot(item);
            if (slot.isBlank()) {
                continue;
            }
            try {
                profileMemoryPort.recordRead(item.getUserId(), "default", slot, referencedAt);
            } catch (RuntimeException ex) {
                LOG.debug("记录Profile读取反馈失败: userId={}, slot={}", item.getUserId(), slot, ex);
            }
        }
    }

    private void recordLayerReadFeedback(List<MemoryItem> shortTerm,
                                         List<MemoryItem> longTerm,
                                         List<MemoryItem> semantic,
                                         Instant referencedAt) {
        recordLayerReadFeedback(MemoryLayer.SHORT_TERM, shortTerm, referencedAt);
        recordLayerReadFeedback(MemoryLayer.LONG_TERM, longTerm, referencedAt);
        recordLayerReadFeedback(MemoryLayer.SEMANTIC, semantic, referencedAt);
    }

    private void recordLayerReadFeedback(MemoryLayer layer, List<MemoryItem> items, Instant referencedAt) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MemoryItem item : items) {
            if (item == null || isBlank(item.getId())) {
                continue;
            }
            try {
                memoryLifecyclePort.recordRead(layer.name().toLowerCase(Locale.ROOT), item.getId(), referencedAt);
            } catch (RuntimeException ex) {
                LOG.debug("记录记忆读取反馈失败: layer={}, memoryId={}", layer, item.getId(), ex);
            }
        }
    }

    private List<MemoryItem> loadLayer(MemoryStorePort port, String userId, int limit, MemoryLayer layer) {
        try {
            return port.listByUser(userId, limit).stream()
                    .map(record -> toMemoryItem(record, layer))
                    .toList();
        } catch (Exception ex) {
            LOG.warn("加载{}记忆失败: userId={}", layer.name(), userId, ex);
            return Collections.emptyList();
        }
    }

    private MemoryItem toMemoryItem(MemoryRecord record, MemoryLayer layer) {
        return MemoryItem.builder()
                .id(record.id())
                .userId(stringField(record.metadata(), "userId"))
                .conversationId(stringField(record.metadata(), "conversationId"))
                .layer(layer)
                .type(record.type())
                .content(record.content())
                .metadataJson(serializeMetadata(record.metadata()))
                .importanceScore(numberField(record.metadata(), "importanceScore", 0D))
                .confidenceLevel(numberField(record.metadata(), "confidenceLevel", 0D))
                .createTime(record.updatedAt() != null
                        ? record.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()
                        : null)
                .build();
    }

    private List<MemoryItem> deduplicateById(List<MemoryItem> items) {
        if (items == null || items.size() <= 1) {
            return items == null ? Collections.emptyList() : items;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryItem> result = new ArrayList<>();
        for (MemoryItem item : items) {
            String id = item.getId();
            if (id != null && seen.add(id)) {
                result.add(item);
            }
        }
        return result;
    }

    private List<MemoryItem> deduplicateProfileSlots(List<MemoryItem> items) {
        if (items == null || items.size() <= 1) {
            return items == null ? Collections.emptyList() : items;
        }
        Map<String, MemoryItem> slotWinners = new LinkedHashMap<>();
        List<String> itemSlots = new ArrayList<>();
        for (MemoryItem item : items) {
            String slot = semanticSlot(item);
            itemSlots.add(slot);
            if (slot.isBlank()) {
                continue;
            }
            MemoryItem current = slotWinners.get(slot);
            if (current == null || prefer(item, current) > 0) {
                slotWinners.put(slot, item);
            }
        }
        Set<String> emittedSlots = new LinkedHashSet<>();
        List<MemoryItem> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            MemoryItem item = items.get(index);
            String slot = itemSlots.get(index);
            if (slot.isBlank()) {
                result.add(item);
                continue;
            }
            if (emittedSlots.add(slot)) {
                result.add(slotWinners.get(slot));
            }
        }
        return result;
    }

    private int prefer(MemoryItem candidate, MemoryItem current) {
        int byTime = Comparator.nullsFirst(java.time.LocalDateTime::compareTo)
                .compare(candidate.getCreateTime(), current.getCreateTime());
        if (byTime != 0) {
            return byTime;
        }
        return Double.compare(score(candidate), score(current));
    }

    private double score(MemoryItem item) {
        return number(item.getImportanceScore()) + number(item.getConfidenceLevel());
    }

    private double number(Double value) {
        return value == null ? 0D : value;
    }

    private String semanticSlot(MemoryItem item) {
        return profileSlotResolver.resolve(item);
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            LOG.debug("序列化记忆元数据失败", ex);
            return "{}";
        }
    }

    private String stringField(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    private double numberField(Map<String, Object> metadata, String key, double fallback) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private MemoryContext emptyContext(MemoryLoadRequest request) {
        return MemoryContext.builder()
                .conversationId(request != null ? request.conversationId() : "")
                .userId(request != null ? request.userId() : "")
                .currentQuestion(request != null ? request.currentQuestion() : "")
                .workingMemory(Collections.emptyList())
                .correctionMemories(Collections.emptyList())
                .profileMemories(Collections.emptyList())
                .shortTermMemories(Collections.emptyList())
                .businessDocumentMemories(Collections.emptyList())
                .longTermMemories(Collections.emptyList())
                .semanticMemories(Collections.emptyList())
                .promptMessages(Collections.emptyList())
                .build();
    }

    private int safeSize(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
