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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.TreeSet;

/**
 * 候选→条目映射协作者：分层存储查找、候选物化为 MemoryItem、槽位归属与去重。
 * 纯读取/纯函数；召回编排与生命周期写回留在主流水线。
 */
final class MemoryCandidateItemSupport {

    static final Logger LOG = LoggerFactory.getLogger(MemoryCandidateItemSupport.class);

    final ShortTermMemoryPort shortTermPort;
    final LongTermMemoryPort longTermPort;
    final SemanticMemoryPort semanticPort;
    final ObjectMapper objectMapper;

    MemoryCandidateItemSupport(ShortTermMemoryPort shortTermPort,
                               LongTermMemoryPort longTermPort,
                               SemanticMemoryPort semanticPort,
                               ObjectMapper objectMapper) {
        this.shortTermPort = shortTermPort;
        this.longTermPort = longTermPort;
        this.semanticPort = semanticPort;
        this.objectMapper = objectMapper;
    }

    Optional<MemoryItem> toMemoryItem(MemoryRecallCandidate candidate) {
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

    MemoryItem toMemoryItem(MemoryRecord record, double relevanceScore) {
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

    boolean generationMatches(MemoryRecallCandidate candidate, MemoryRecord record) {
        if (candidate.generationId().isBlank()) {
            return true;
        }
        String activeGenerationId = stringField(record.metadata(), "generationId");
        return activeGenerationId.isBlank() || candidate.generationId().equals(activeGenerationId);
    }

    Optional<MemoryRecord> findMemoryById(String memoryId, String candidateLayer) {
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

    Optional<MemoryRecord> safeFindById(MemoryStorePort port, String memoryId) {
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

    Optional<MemoryLayer> parseLayer(String layer) {
        if (isBlank(layer)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemoryLayer.valueOf(layer.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    String serializeMetadata(Map<String, Object> metadata) {
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

    String metadataValue(String metadata, String key) {
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

    String stringField(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    double numberField(Map<String, Object> metadata, String key, double fallback) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    List<MemoryItem> filterByLayer(List<MemoryItem> items, MemoryLayer layer) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .filter(item -> item != null && item.getLayer() == layer)
                .toList();
    }

    Set<String> correctionProfileSlots(List<MemoryItem> corrections) {
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

    String correctionTargetSlot(MemoryItem correction) {
        String metadata = correction == null ? "" : Objects.requireNonNullElse(correction.getMetadataJson(), "");
        String targetKind = metadataValue(metadata, "targetKind");
        String targetKey = metadataValue(metadata, "targetKey");
        if ("PROFILE_SLOT".equalsIgnoreCase(targetKind) && !targetKey.isBlank()) {
            return targetKey;
        }
        return "";
    }

    Set<String> activeProfileSlots(List<MemoryItem> profile) {
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

    List<MemoryItem> removeActiveProfileSlotMemories(List<MemoryItem> items, Set<String> activeSlots) {
        if (items == null || items.isEmpty() || activeSlots == null || activeSlots.isEmpty()) {
            return items == null ? Collections.emptyList() : items;
        }
        return items.stream()
                .filter(item -> !activeSlots.contains(semanticSlot(item)))
                .toList();
    }

    String semanticSlot(MemoryItem item) {
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

    List<MemoryItem> deduplicateById(List<MemoryItem> items) {
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

    List<MemoryItem> deduplicateProfileSlots(List<MemoryItem> items) {
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

    int prefer(MemoryItem candidate, MemoryItem current) {
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

    double score(MemoryItem item) {
        return number(item.getImportanceScore()) + number(item.getConfidenceLevel());
    }

    boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
