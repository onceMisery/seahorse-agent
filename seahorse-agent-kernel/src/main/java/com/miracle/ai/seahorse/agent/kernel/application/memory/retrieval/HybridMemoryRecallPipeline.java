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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallFusionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
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

public class HybridMemoryRecallPipeline implements MemoryRetrievalPipelinePort {

    private static final Logger LOG = LoggerFactory.getLogger(HybridMemoryRecallPipeline.class);
    private static final String DEFAULT_TENANT_ID = "default";

    private final ShortTermMemoryPort shortTermPort;
    private final LongTermMemoryPort longTermPort;
    private final SemanticMemoryPort semanticPort;
    private final ObjectMapper objectMapper;
    private final ProfileMemoryPort profileMemoryPort;
    private final CorrectionLedgerPort correctionLedgerPort;
    private final MemoryRouterPort memoryRouterPort;
    private final MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort;
    private final MemoryLifecyclePort memoryLifecyclePort;
    private final List<MemoryRecallChannelPort> channels;
    private final MemoryRecallFusionPort fusionPort;
    private final MemoryFusionPolicy fusionPolicy;
    private final int channelTopK;

    public HybridMemoryRecallPipeline(ShortTermMemoryPort shortTermPort,
                                      LongTermMemoryPort longTermPort,
                                      SemanticMemoryPort semanticPort,
                                      ObjectMapper objectMapper,
                                      ProfileMemoryPort profileMemoryPort,
                                      CorrectionLedgerPort correctionLedgerPort,
                                      MemoryRouterPort memoryRouterPort,
                                      MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                                      MemoryLifecyclePort memoryLifecyclePort,
                                      List<MemoryRecallChannelPort> channels,
                                      MemoryRecallFusionPort fusionPort,
                                      MemoryFusionPolicy fusionPolicy,
                                      int channelTopK) {
        this.shortTermPort = Objects.requireNonNull(shortTermPort, "shortTermPort must not be null");
        this.longTermPort = Objects.requireNonNull(longTermPort, "longTermPort must not be null");
        this.semanticPort = Objects.requireNonNull(semanticPort, "semanticPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.profileMemoryPort = Objects.requireNonNull(profileMemoryPort, "profileMemoryPort must not be null");
        this.correctionLedgerPort = Objects.requireNonNull(correctionLedgerPort, "correctionLedgerPort must not be null");
        this.memoryRouterPort = Objects.requireNonNull(memoryRouterPort, "memoryRouterPort must not be null");
        this.businessDocumentRetrieverPort = Objects.requireNonNull(businessDocumentRetrieverPort,
                "businessDocumentRetrieverPort must not be null");
        this.memoryLifecyclePort = Objects.requireNonNull(memoryLifecyclePort, "memoryLifecyclePort must not be null");
        this.channels = sortedChannels(channels);
        this.fusionPort = Objects.requireNonNull(fusionPort, "fusionPort must not be null");
        this.fusionPolicy = Objects.requireNonNullElseGet(fusionPolicy, MemoryFusionPolicy::defaults);
        this.channelTopK = channelTopK > 0 ? channelTopK : MemoryFusionPolicy.DEFAULT_CHANNEL_TOP_K;
    }

    @Override
    public MemoryContext load(MemoryLoadRequest request) {
        if (request == null || isBlank(request.userId())) {
            return emptyContext(request);
        }
        String userId = request.userId();
        var routePlan = memoryRouterPort.route(new MemoryRouteRequest(userId, DEFAULT_TENANT_ID,
                request.currentQuestion()));
        boolean loadCorrection = routePlan.isActive(MemoryTrack.CORRECTION);
        boolean loadProfile = routePlan.isActive(MemoryTrack.PROFILE);
        boolean loadEpisodic = routePlan.isActive(MemoryTrack.EPISODIC);
        boolean loadBusinessDocument = routePlan.isActive(MemoryTrack.BUSINESS_DOCUMENT);

        List<MemoryItem> corrections = loadCorrection ? loadCorrections(userId) : Collections.emptyList();
        List<MemoryItem> profile = loadProfile ? loadProfileFacts(userId) : Collections.emptyList();
        Set<String> correctionProfileSlots = correctionProfileSlots(corrections);
        if (!correctionProfileSlots.isEmpty()) {
            profile = removeActiveProfileSlotMemories(profile, correctionProfileSlots);
        }

        List<MemoryItem> shortTerm = Collections.emptyList();
        List<MemoryItem> longTerm = Collections.emptyList();
        List<MemoryItem> semantic = Collections.emptyList();
        if (loadEpisodic || (!channels.isEmpty() && !isBlank(request.currentQuestion()))) {
            List<MemoryItem> recalled = recallUserMemories(userId, request.currentQuestion(), routePlan.activeTracks());
            shortTerm = filterByLayer(recalled, MemoryLayer.SHORT_TERM);
            longTerm = filterByLayer(recalled, MemoryLayer.LONG_TERM);
            semantic = filterByLayer(recalled, MemoryLayer.SEMANTIC);
        }
        List<MemoryItem> businessDocuments = loadBusinessDocument
                ? loadBusinessDocuments(userId, request.currentQuestion(), fusionPolicy.finalTopK())
                : Collections.emptyList();

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

    private List<MemoryRecallChannelPort> sortedChannels(List<MemoryRecallChannelPort> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(MemoryRecallChannelPort::order)
                        .thenComparing(MemoryRecallChannelPort::channelName))
                .toList();
    }

    private List<MemoryItem> recallUserMemories(String userId, String query, Set<MemoryTrack> activeTracks) {
        if (isBlank(query) || channels.isEmpty()) {
            return List.of();
        }
        MemoryRecallRequest recallRequest = new MemoryRecallRequest(
                userId,
                DEFAULT_TENANT_ID,
                query,
                activeTracks,
                channelTopK,
                Map.of());
        List<List<MemoryRecallCandidate>> channelResults = new ArrayList<>();
        for (MemoryRecallChannelPort channel : channels) {
            try {
                List<MemoryRecallCandidate> result = channel.recall(recallRequest);
                channelResults.add(result == null ? List.of() : result);
            } catch (RuntimeException ex) {
                LOG.warn("memory recall channel failed: channel={}, userId={}", channel.channelName(), userId, ex);
                channelResults.add(List.of());
            }
        }
        return fusionPort.fuse(channelResults, fusionPolicy, Instant.now()).stream()
                .map(this::toMemoryItem)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MemoryItem> toMemoryItem(MemoryRecallCandidate candidate) {
        Optional<MemoryRecord> record = findMemoryById(candidate.memoryId(), candidate.layer());
        if (record.isEmpty() && !candidate.content().isBlank()) {
            MemoryLayer layer = parseLayer(candidate.layer()).orElse(MemoryLayer.SEMANTIC);
            return Optional.of(MemoryItem.builder()
                    .id(candidate.memoryId())
                    .userId(candidate.userId())
                    .layer(layer)
                    .type(candidate.type())
                    .content(candidate.content())
                    .metadataJson(serializeMetadata(candidate.metadata()))
                    .importanceScore(number(candidate.metadata().get("importanceScore")))
                    .confidenceLevel(number(candidate.metadata().get("confidenceLevel")))
                    .relevanceScore(candidate.rawScore())
                    .build());
        }
        return record
                .filter(memoryRecord -> generationMatches(candidate, memoryRecord))
                .map(memoryRecord -> toMemoryItem(memoryRecord, candidate.rawScore()));
    }

    private boolean generationMatches(MemoryRecallCandidate candidate, MemoryRecord record) {
        if (candidate.generationId().isBlank()) {
            return true;
        }
        String activeGenerationId = stringField(record.metadata(), "generationId");
        return activeGenerationId.isBlank() || candidate.generationId().equals(activeGenerationId);
    }

    private Optional<MemoryRecord> findMemoryById(String memoryId, String candidateLayer) {
        Optional<MemoryLayer> layer = parseLayer(candidateLayer);
        if (layer.isPresent()) {
            return switch (layer.get()) {
                case SHORT_TERM -> safeFindById(shortTermPort, memoryId);
                case LONG_TERM -> safeFindById(longTermPort, memoryId);
                case SEMANTIC -> safeFindById(semanticPort, memoryId);
                case WORKING -> Optional.empty();
            };
        }
        Optional<MemoryRecord> shortTerm = safeFindById(shortTermPort, memoryId);
        if (shortTerm.isPresent()) {
            return shortTerm;
        }
        Optional<MemoryRecord> longTerm = safeFindById(longTermPort, memoryId);
        if (longTerm.isPresent()) {
            return longTerm;
        }
        return safeFindById(semanticPort, memoryId);
    }

    private Optional<MemoryRecord> safeFindById(MemoryStorePort port, String memoryId) {
        if (isBlank(memoryId)) {
            return Optional.empty();
        }
        try {
            return port.findById(memoryId);
        } catch (RuntimeException ex) {
            LOG.debug("memory find by id failed: memoryId={}", memoryId, ex);
            return Optional.empty();
        }
    }

    private Optional<MemoryLayer> parseLayer(String layer) {
        if (isBlank(layer)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemoryLayer.valueOf(layer.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private MemoryItem toMemoryItem(MemoryRecord record, double relevanceScore) {
        MemoryLayer layer = parseLayer(record.layer()).orElse(MemoryLayer.SEMANTIC);
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
                .relevanceScore(relevanceScore)
                .createTime(record.updatedAt() != null
                        ? record.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()
                        : null)
                .build();
    }

    private List<MemoryItem> loadCorrections(String userId) {
        try {
            return correctionLedgerPort.listActive(userId, DEFAULT_TENANT_ID, fusionPolicy.finalTopK()).stream()
                    .map(this::toCorrectionItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("load correction ledger failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private List<MemoryItem> loadProfileFacts(String userId) {
        try {
            return profileMemoryPort.listActive(userId, DEFAULT_TENANT_ID, fusionPolicy.finalTopK()).stream()
                    .map(this::toProfileItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("load profile facts failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
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
                        "status", fact.status())))
                .importanceScore(1D)
                .confidenceLevel(fact.confidenceLevel())
                .createTime(fact.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private List<MemoryItem> loadBusinessDocuments(String userId, String query, int limit) {
        if (isBlank(query)) {
            return Collections.emptyList();
        }
        try {
            return businessDocumentRetrieverPort.retrieve(DEFAULT_TENANT_ID, query, limit);
        } catch (RuntimeException ex) {
            LOG.warn("load business document memories failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private List<MemoryItem> filterByLayer(List<MemoryItem> items, MemoryLayer layer) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .filter(item -> item != null && item.getLayer() == layer)
                .toList();
    }

    private Set<String> correctionProfileSlots(List<MemoryItem> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return Set.of();
        }
        Set<String> slots = new LinkedHashSet<>();
        for (MemoryItem correction : corrections) {
            String slot = correctionTargetSlot(correction);
            if (!slot.isBlank()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private String correctionTargetSlot(MemoryItem correction) {
        String metadata = correction == null ? "" : Objects.requireNonNullElse(correction.getMetadataJson(), "");
        String targetKind = metadataValue(metadata, "targetKind");
        String targetKey = metadataValue(metadata, "targetKey");
        if ("PROFILE_SLOT".equalsIgnoreCase(targetKind) && !targetKey.isBlank()) {
            return targetKey;
        }
        return "";
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

    private String semanticSlot(MemoryItem item) {
        if (item == null) {
            return "";
        }
        String metadata = Objects.requireNonNullElse(item.getMetadataJson(), "");
        String profileSlot = metadataValue(metadata, "profileSlot");
        if (!profileSlot.isBlank()) {
            return profileSlot;
        }
        String semanticKey = metadataValue(metadata, "semanticKey");
        if ("profile:occupation".equals(semanticKey)) {
            return "identity.occupation";
        }
        return semanticKey.startsWith("identity.") || semanticKey.startsWith("skills.")
                || semanticKey.startsWith("preferences.")
                ? semanticKey
                : "";
    }

    private List<MemoryItem> deduplicateById(List<MemoryItem> items) {
        if (items == null || items.size() <= 1) {
            return items == null ? Collections.emptyList() : items;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryItem> result = new ArrayList<>();
        for (MemoryItem item : items) {
            if (item != null && !isBlank(item.getId()) && seen.add(item.getId())) {
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
            } else if (emittedSlots.add(slot)) {
                result.add(slotWinners.get(slot));
            }
        }
        return result;
    }

    private int prefer(MemoryItem candidate, MemoryItem current) {
        int byRelevance = Double.compare(number(candidate.getRelevanceScore()), number(current.getRelevanceScore()));
        if (byRelevance != 0) {
            return byRelevance;
        }
        int byTime = Comparator.nullsFirst(java.time.LocalDateTime::compareTo)
                .compare(candidate.getCreateTime(), current.getCreateTime());
        if (byTime != 0) {
            return byTime;
        }
        return Double.compare(score(candidate), score(current));
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
                profileMemoryPort.recordRead(item.getUserId(), DEFAULT_TENANT_ID, slot, referencedAt);
            } catch (RuntimeException ex) {
                LOG.debug("record profile read feedback failed: userId={}, slot={}", item.getUserId(), slot, ex);
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
                LOG.debug("record memory read feedback failed: layer={}, memoryId={}", layer, item.getId(), ex);
            }
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            LOG.debug("serialize memory metadata failed", ex);
            return "{}";
        }
    }

    private String metadataValue(String metadata, String key) {
        if (metadata == null || metadata.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        String compactPrefix = "\"" + key + "\":\"";
        int compactStart = metadata.indexOf(compactPrefix);
        if (compactStart >= 0) {
            int valueStart = compactStart + compactPrefix.length();
            int valueEnd = metadata.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadata.substring(valueStart, valueEnd) : "";
        }
        String spacedPrefix = "\"" + key + "\": \"";
        int spacedStart = metadata.indexOf(spacedPrefix);
        if (spacedStart >= 0) {
            int valueStart = spacedStart + spacedPrefix.length();
            int valueEnd = metadata.indexOf('"', valueStart);
            return valueEnd > valueStart ? metadata.substring(valueStart, valueEnd) : "";
        }
        return "";
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

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private double score(MemoryItem item) {
        return number(item.getImportanceScore()) + number(item.getConfidenceLevel());
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
