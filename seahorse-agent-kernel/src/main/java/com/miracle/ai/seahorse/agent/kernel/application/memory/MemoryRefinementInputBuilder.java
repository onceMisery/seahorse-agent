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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Slice 3 续 cut 6：refiner 输入侧——汇集三层 store 中已存在的 memory，并从中筛出
 * "sticky anchors"（refiner 不会自动 obsolete 的高重要度/高置信 LTM/SEM 锚点）。
 *
 * <p>原 facade 私有方法 {@code currentExistingMemories} / {@code collectExistingMemories} /
 * {@code toRefinementMemory} / {@code stickyAnchors} / {@code isStickyAnchor} /
 * {@code memoryMetadataScore} 全部迁入，对外只暴露两个入口：
 * <ul>
 *     <li>{@link #existingMemories(String)} — 三层逐一 listByUser，单层失败 swallow 不阻塞。</li>
 *     <li>{@link #stickyAnchors(List)} — 从既有 memory 中过滤 LTM/SEM ACTIVE 的高分锚点。</li>
 * </ul>
 *
 * <p>所有阈值（per-layer read mask、sticky 数量上限、importance/confidence 阈值）由构造时
 * 传入，与原 {@code MemoryEngineOptions} 字段保持一一对应。
 */
public final class MemoryRefinementInputBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryRefinementInputBuilder.class);

    static final String METADATA_IMPORTANCE = "importance";
    static final String METADATA_CONFIDENCE = "confidence";
    static final String METADATA_IMPORTANCE_SCORE = "importanceScore";
    static final String METADATA_CONFIDENCE_LEVEL = "confidenceLevel";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ShortTermMemoryPort shortTermPort;
    private final LongTermMemoryPort longTermPort;
    private final SemanticMemoryPort semanticPort;
    private final int readMaskPerLayerLimit;
    private final int stickyAnchorLimit;
    private final double stickyAnchorImportanceThreshold;
    private final double stickyAnchorConfidenceThreshold;

    public MemoryRefinementInputBuilder(ShortTermMemoryPort shortTermPort,
                                        LongTermMemoryPort longTermPort,
                                        SemanticMemoryPort semanticPort,
                                        int readMaskPerLayerLimit,
                                        int stickyAnchorLimit,
                                        double stickyAnchorImportanceThreshold,
                                        double stickyAnchorConfidenceThreshold) {
        this.shortTermPort = Objects.requireNonNull(shortTermPort, "shortTermPort must not be null");
        this.longTermPort = Objects.requireNonNull(longTermPort, "longTermPort must not be null");
        this.semanticPort = Objects.requireNonNull(semanticPort, "semanticPort must not be null");
        this.readMaskPerLayerLimit = readMaskPerLayerLimit;
        this.stickyAnchorLimit = stickyAnchorLimit;
        this.stickyAnchorImportanceThreshold = stickyAnchorImportanceThreshold;
        this.stickyAnchorConfidenceThreshold = stickyAnchorConfidenceThreshold;
    }

    /**
     * 取出该用户在三层中已存在的 memory（每层最多 {@code readMaskPerLayerLimit} 条）。
     * 单层 listByUser 失败被 swallow 并打 debug 日志，不影响其他层。
     */
    public List<MemoryRefinementMemory> existingMemories(String userId) {
        if (isBlank(userId)) {
            return List.of();
        }
        List<MemoryRefinementMemory> memories = new ArrayList<>();
        collect(memories, MemoryLayer.SHORT_TERM, shortTermPort, userId);
        collect(memories, MemoryLayer.LONG_TERM, longTermPort, userId);
        collect(memories, MemoryLayer.SEMANTIC, semanticPort, userId);
        return List.copyOf(memories);
    }

    /**
     * 从已收集的 memory 中筛出 sticky anchors：LTM/SEM 层、status==ACTIVE，且
     * importance 或 confidence 任一超过阈值的条目，最多 {@code stickyAnchorLimit} 条。
     */
    public List<MemoryRefinementMemory> stickyAnchors(List<MemoryRefinementMemory> existingMemories) {
        if (existingMemories == null || existingMemories.isEmpty()) {
            return List.of();
        }
        return existingMemories.stream()
                .filter(this::isSticky)
                .limit(stickyAnchorLimit)
                .toList();
    }

    private void collect(List<MemoryRefinementMemory> memories,
                         MemoryLayer layer,
                         MemoryStorePort store,
                         String userId) {
        try {
            for (MemoryRecord record : store.listByUser(userId, readMaskPerLayerLimit)) {
                if (record == null || isBlank(record.id())) {
                    continue;
                }
                memories.add(toMemory(layer, record));
            }
        } catch (RuntimeException ex) {
            LOG.debug("load refiner read-mask memories failed: layer={}, userId={}", layer, userId, ex);
        }
    }

    private static MemoryRefinementMemory toMemory(MemoryLayer layer, MemoryRecord record) {
        Map<String, Object> metadata = record.metadata();
        return new MemoryRefinementMemory(
                record.id(),
                layer.name(),
                record.type(),
                record.content(),
                stringMetadata(metadata, "targetKind", record.type()),
                stringMetadata(metadata, "targetKey", stringMetadata(metadata, "profileSlot", "")),
                stringMetadata(metadata, "generationId", ""),
                stringMetadata(metadata, "status", STATUS_ACTIVE),
                metadata);
    }

    private boolean isSticky(MemoryRefinementMemory memory) {
        if (memory == null || !STATUS_ACTIVE.equalsIgnoreCase(memory.status())) {
            return false;
        }
        String layer = memory.layer().toUpperCase(Locale.ROOT);
        if (!MemoryLayer.LONG_TERM.name().equals(layer) && !MemoryLayer.SEMANTIC.name().equals(layer)) {
            return false;
        }
        return metadataScore(memory, METADATA_IMPORTANCE_SCORE, METADATA_IMPORTANCE)
                >= stickyAnchorImportanceThreshold
                || metadataScore(memory, METADATA_CONFIDENCE_LEVEL, METADATA_CONFIDENCE)
                >= stickyAnchorConfidenceThreshold;
    }

    private static double metadataScore(MemoryRefinementMemory memory, String primaryKey, String fallbackKey) {
        double primary = doubleMetadata(memory.metadata(), primaryKey);
        return primary > 0D ? primary : doubleMetadata(memory.metadata(), fallbackKey);
    }

    private static String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return value.toString().trim();
    }

    private static double doubleMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
