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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
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
import java.util.Set;
import java.util.UUID;

/**
 * 默认记忆引擎端口实现。
 *
 * <p>编排 {@link ShortTermMemoryPort}、{@link LongTermMemoryPort}、{@link SemanticMemoryPort}
 * 三层记忆的读取和转换，实现 {@link MemoryEnginePort} 契约。
 *
 * <p>当前阶段行为：
 * <ul>
 *   <li>{@link #loadMemory} 多层读取、配置化限量、转换、去重。</li>
 *   <li>{@link #writeMemory} 只写入显式可信用户声明，不无条件写入原始问题。</li>
 *   <li>{@link #executeMemoryDecay} 尚不实现全量扫描，委托给后续治理维护端口。</li>
 *   <li>{@link #assessMemoryQuality} 返回基础计数，不声称具备冲突检测能力。</li>
 * </ul>
 */
public class DefaultMemoryEnginePort implements MemoryEnginePort {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMemoryEnginePort.class);

    private final ShortTermMemoryPort shortTermPort;
    private final LongTermMemoryPort longTermPort;
    private final SemanticMemoryPort semanticPort;
    private final ObjectMapper objectMapper;
    private final MemoryEngineOptions options;
    private final MemoryCaptureCandidateExtractor captureCandidateExtractor;
    private final MemoryValueAssessor memoryValueAssessor;

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, MemoryEngineOptions.defaults());
    }

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper,
                                   MemoryEngineOptions options) {
        this.shortTermPort = Objects.requireNonNull(shortTermPort, "shortTermPort must not be null");
        this.longTermPort = Objects.requireNonNull(longTermPort, "longTermPort must not be null");
        this.semanticPort = Objects.requireNonNull(semanticPort, "semanticPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.options = Objects.requireNonNullElseGet(options, MemoryEngineOptions::defaults);
        this.captureCandidateExtractor = new MemoryCaptureCandidateExtractor();
        this.memoryValueAssessor = new MemoryValueAssessor();
    }

    @Override
    public MemoryContext loadMemory(MemoryLoadRequest request) {
        if (request == null || isBlank(request.userId())) {
            return emptyContext(request);
        }

        String userId = request.userId();
        List<MemoryItem> shortTerm = loadLayer(shortTermPort, userId, options.shortTermLimit(),
                MemoryLayer.SHORT_TERM);
        List<MemoryItem> longTerm = loadLayer(longTermPort, userId, options.longTermLimit(), MemoryLayer.LONG_TERM);
        List<MemoryItem> semantic = loadLayer(semanticPort, userId, options.semanticLimit(), MemoryLayer.SEMANTIC);

        return MemoryContext.builder()
                .conversationId(request.conversationId())
                .userId(userId)
                .currentQuestion(request.currentQuestion())
                .workingMemory(Collections.emptyList())
                .shortTermMemories(shortTerm)
                .longTermMemories(deduplicateProfileSlots(deduplicateById(longTerm)))
                .semanticMemories(deduplicateProfileSlots(semantic))
                .promptMessages(Collections.emptyList())
                .build();
    }

    @Override
    public void writeMemory(MemoryWriteRequest request) {
        if (!options.captureEnabled()) {
            return;
        }
        if (request == null || isBlank(request.userId()) || request.message() == null) {
            return;
        }
        ChatMessage message = request.message();
        if (message.getRole() != ChatRole.USER || isBlank(message.getContent())) {
            return;
        }
        MemoryCaptureCandidate candidate = captureCandidateExtractor.extract(message.getContent()).orElse(null);
        if (candidate == null) {
            return;
        }
        MemoryCaptureDecision decision = memoryValueAssessor.assess(candidate);
        if (!decision.accepted()) {
            return;
        }
        MemoryRecord record = new MemoryRecord(
                memoryId(request),
                MemoryLayer.SHORT_TERM.name(),
                decision.type(),
                decision.content(),
                captureMetadata(request, message, decision),
                java.time.Instant.now());
        shortTermPort.save(record);
    }

    @Override
    public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
        MemoryContext context = loadMemory(request);
        List<MemoryItem> all = new ArrayList<>();
        all.addAll(context.getShortTermMemories());
        all.addAll(context.getLongTermMemories());
        all.addAll(context.getSemanticMemories());
        return all;
    }

    @Override
    public void executeMemoryDecay() {
        // 第一阶段不实现全量扫描衰减。
        // 正确路径需要新增 ShortTermMemoryMaintenancePort.scanExpiredOrDecayed(limit)，
        // 由 KernelMemoryGovernanceService 和 SeahorseMemoryGovernanceJob 负责。
    }

    @Override
    public MemoryQualityReport assessMemoryQuality(String userId) {
        if (isBlank(userId)) {
            return MemoryQualityReport.builder().build();
        }
        int shortTermCount = safeSize(shortTermPort.listByUser(userId, Integer.MAX_VALUE));
        int longTermCount = safeSize(longTermPort.listByUser(userId, Integer.MAX_VALUE));
        int semanticCount = safeSize(semanticPort.listByUser(userId, Integer.MAX_VALUE));
        return MemoryQualityReport.builder()
                .userId(userId)
                .shortTermCount(shortTermCount)
                .longTermCount(longTermCount)
                .semanticCount(semanticCount)
                .build();
    }

    // ========== 内部方法 ==========

    private Map<String, Object> captureMetadata(MemoryWriteRequest request,
                                                ChatMessage message,
                                                MemoryCaptureDecision decision) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", request.userId());
        metadata.put("conversationId", Objects.requireNonNullElse(request.conversationId(), ""));
        metadata.put("messageId", Objects.requireNonNullElse(request.messageId(), ""));
        metadata.put("role", message.getRole().name().toLowerCase());
        metadata.put("source", "chat_memory_capture");
        metadata.put("capturePolicy", "explicit_user_memory");
        metadata.put("capturePolicyVersion", decision.policyVersion());
        metadata.put("importanceScore", decision.importanceScore());
        metadata.put("confidenceLevel", decision.confidenceLevel());
        metadata.put("valueScore", decision.valueScore());
        metadata.put("riskScore", decision.riskScore());
        metadata.put("captureSignals", decision.signals());
        metadata.put("captureReasons", decision.reasons());
        return metadata;
    }

    private String memoryId(MemoryWriteRequest request) {
        if (!isBlank(request.messageId())) {
            return "stm-" + request.messageId();
        }
        return "stm-" + UUID.randomUUID();
    }

    private List<MemoryItem> loadLayer(ShortTermMemoryPort port, String userId, int limit, MemoryLayer layer) {
        return loadLayerInternal(port, userId, limit, layer);
    }

    private List<MemoryItem> loadLayer(LongTermMemoryPort port, String userId, int limit, MemoryLayer layer) {
        return loadLayerInternal(port, userId, limit, layer);
    }

    private List<MemoryItem> loadLayer(SemanticMemoryPort port, String userId, int limit, MemoryLayer layer) {
        return loadLayerInternal(port, userId, limit, layer);
    }

    private List<MemoryItem> loadLayerInternal(com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort port,
                                               String userId, int limit, MemoryLayer layer) {
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
        List<MemoryItem> nonSlotItems = new ArrayList<>();
        for (MemoryItem item : items) {
            String slot = semanticSlot(item);
            if (slot.isBlank()) {
                nonSlotItems.add(item);
                continue;
            }
            MemoryItem current = slotWinners.get(slot);
            if (current == null || prefer(item, current) > 0) {
                slotWinners.put(slot, item);
            }
        }
        List<MemoryItem> result = new ArrayList<>(slotWinners.values());
        result.addAll(nonSlotItems);
        result.sort(Comparator.comparing(MemoryItem::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
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
        if (item == null) {
            return "";
        }
        String metadata = Objects.requireNonNullElse(item.getMetadataJson(), "");
        if (metadata.contains("\"semanticKey\":\"profile:occupation\"")
                || metadata.contains("\"semanticKey\": \"profile:occupation\"")) {
            return "profile:occupation";
        }
        String content = Objects.requireNonNullElse(item.getContent(), "");
        String normalized = content.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "occupation", "profession", "job", "student", "teacher")
                || containsAny(content, "职业", "身份", "工作", "学生", "老师", "教师")) {
            return "profile:occupation";
        }
        return "";
    }

    private boolean containsAny(String content, String... needles) {
        if (content == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && content.contains(needle)) {
                return true;
            }
        }
        return false;
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
                .shortTermMemories(Collections.emptyList())
                .longTermMemories(Collections.emptyList())
                .semanticMemories(Collections.emptyList())
                .promptMessages(Collections.emptyList())
                .build();
    }

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
