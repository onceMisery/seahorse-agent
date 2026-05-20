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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationType;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
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
public class DefaultMemoryEnginePort implements MemoryEnginePort, MemoryIngestionWorkflowPort {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMemoryEnginePort.class);
    private static final String DEFAULT_VECTOR_EMBEDDING_MODEL = "default";

    private final ShortTermMemoryPort shortTermPort;
    private final LongTermMemoryPort longTermPort;
    private final SemanticMemoryPort semanticPort;
    private final ProfileMemoryPort profileMemoryPort;
    private final CorrectionLedgerPort correctionLedgerPort;
    private final MemoryRouterPort memoryRouterPort;
    private final MemoryOperationLogPort memoryOperationLogPort;
    private final MemoryVectorPort memoryVectorPort;
    private final MemoryOutboxPort memoryOutboxPort;
    private final MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort;
    private final MemoryLifecyclePort memoryLifecyclePort;
    private final ObjectMapper objectMapper;
    private final MemoryEngineOptions options;
    private final MemoryCaptureCandidateExtractor captureCandidateExtractor;
    private final MemoryValueAssessor memoryValueAssessor;
    private final MemorySanitizer memorySanitizer;
    private final MemoryPreFilter memoryPreFilter;
    private final MemorySemanticClassifier memorySemanticClassifier;
    private final MemorySchemaValidator memorySchemaValidator;

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
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options,
                ProfileMemoryPort.noop(), CorrectionLedgerPort.noop(), new DefaultMemoryRouter());
    }

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper,
                                   MemoryEngineOptions options,
                                   ProfileMemoryPort profileMemoryPort,
                                   CorrectionLedgerPort correctionLedgerPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options,
                profileMemoryPort, correctionLedgerPort, new DefaultMemoryRouter());
    }

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper,
                                   MemoryEngineOptions options,
                                   ProfileMemoryPort profileMemoryPort,
                                   CorrectionLedgerPort correctionLedgerPort,
                                   MemoryRouterPort memoryRouterPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options,
                profileMemoryPort, correctionLedgerPort, memoryRouterPort, MemoryOperationLogPort.noop());
    }

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper,
                                   MemoryEngineOptions options,
                                   ProfileMemoryPort profileMemoryPort,
                                   CorrectionLedgerPort correctionLedgerPort,
                                   MemoryRouterPort memoryRouterPort,
                                   MemoryOperationLogPort memoryOperationLogPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options,
                profileMemoryPort, correctionLedgerPort, memoryRouterPort, memoryOperationLogPort,
                MemoryVectorPort.noop(), MemoryOutboxPort.noop(), MemoryBusinessDocumentRetrieverPort.noop(),
                MemoryLifecyclePort.noop());
    }

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper,
                                   MemoryEngineOptions options,
                                   ProfileMemoryPort profileMemoryPort,
                                   CorrectionLedgerPort correctionLedgerPort,
                                   MemoryRouterPort memoryRouterPort,
                                   MemoryOperationLogPort memoryOperationLogPort,
                                   MemoryVectorPort memoryVectorPort,
                                   MemoryOutboxPort memoryOutboxPort,
                                   MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort,
                memoryOutboxPort, businessDocumentRetrieverPort, MemoryLifecyclePort.noop(),
                MemoryPolicyConfigPort.defaults());
    }

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper,
                                   MemoryEngineOptions options,
                                   ProfileMemoryPort profileMemoryPort,
                                   CorrectionLedgerPort correctionLedgerPort,
                                   MemoryRouterPort memoryRouterPort,
                                   MemoryOperationLogPort memoryOperationLogPort,
                                   MemoryVectorPort memoryVectorPort,
                                   MemoryOutboxPort memoryOutboxPort,
                                   MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                                   MemoryLifecyclePort memoryLifecyclePort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort, memoryOutboxPort,
                businessDocumentRetrieverPort, memoryLifecyclePort, MemoryPolicyConfigPort.defaults());
    }

    public DefaultMemoryEnginePort(ShortTermMemoryPort shortTermPort,
                                   LongTermMemoryPort longTermPort,
                                   SemanticMemoryPort semanticPort,
                                   ObjectMapper objectMapper,
                                   MemoryEngineOptions options,
                                   ProfileMemoryPort profileMemoryPort,
                                   CorrectionLedgerPort correctionLedgerPort,
                                   MemoryRouterPort memoryRouterPort,
                                   MemoryOperationLogPort memoryOperationLogPort,
                                   MemoryVectorPort memoryVectorPort,
                                   MemoryOutboxPort memoryOutboxPort,
                                   MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                                   MemoryLifecyclePort memoryLifecyclePort,
                                   MemoryPolicyConfigPort memoryPolicyConfigPort) {
        this.shortTermPort = Objects.requireNonNull(shortTermPort, "shortTermPort must not be null");
        this.longTermPort = Objects.requireNonNull(longTermPort, "longTermPort must not be null");
        this.semanticPort = Objects.requireNonNull(semanticPort, "semanticPort must not be null");
        this.profileMemoryPort = Objects.requireNonNull(profileMemoryPort, "profileMemoryPort must not be null");
        this.correctionLedgerPort = Objects.requireNonNull(correctionLedgerPort, "correctionLedgerPort must not be null");
        this.memoryRouterPort = Objects.requireNonNull(memoryRouterPort, "memoryRouterPort must not be null");
        this.memoryOperationLogPort = Objects.requireNonNull(memoryOperationLogPort,
                "memoryOperationLogPort must not be null");
        this.memoryVectorPort = Objects.requireNonNull(memoryVectorPort, "memoryVectorPort must not be null");
        this.memoryOutboxPort = Objects.requireNonNull(memoryOutboxPort, "memoryOutboxPort must not be null");
        this.businessDocumentRetrieverPort = Objects.requireNonNull(businessDocumentRetrieverPort,
                "businessDocumentRetrieverPort must not be null");
        this.memoryLifecyclePort = Objects.requireNonNull(memoryLifecyclePort, "memoryLifecyclePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.options = Objects.requireNonNullElseGet(options, MemoryEngineOptions::defaults);
        this.captureCandidateExtractor = new MemoryCaptureCandidateExtractor();
        this.memoryValueAssessor = new MemoryValueAssessor(memoryPolicyConfigPort);
        this.memorySanitizer = new MemorySanitizer();
        this.memoryPreFilter = new MemoryPreFilter();
        this.memorySemanticClassifier = new MemorySemanticClassifier(captureCandidateExtractor, memoryValueAssessor);
        this.memorySchemaValidator = new MemorySchemaValidator(memorySanitizer);
    }

    @Override
    public MemoryContext loadMemory(MemoryLoadRequest request) {
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
        if (loadEpisodic) {
            List<MemoryItem> vectorHits = loadVectorHitMemories(userId, request.currentQuestion(),
                    options.semanticLimit());
            shortTerm = mergeById(shortTerm, filterByLayer(vectorHits, MemoryLayer.SHORT_TERM));
            longTerm = mergeById(longTerm, filterByLayer(vectorHits, MemoryLayer.LONG_TERM));
            semantic = mergeById(semantic, filterByLayer(vectorHits, MemoryLayer.SEMANTIC));
        }
        if (loadBusinessDocument) {
            semantic = mergeById(semantic, loadBusinessDocuments(userId, request.currentQuestion(),
                    options.semanticLimit()));
        }
        Set<String> activeProfileSlots = activeProfileSlots(profile);
        if (!activeProfileSlots.isEmpty()) {
            shortTerm = removeActiveProfileSlotMemories(shortTerm, activeProfileSlots);
            longTerm = removeActiveProfileSlotMemories(longTerm, activeProfileSlots);
            semantic = removeActiveProfileSlotMemories(semantic, activeProfileSlots);
        }
        shortTerm = deduplicateById(shortTerm);
        longTerm = deduplicateProfileSlots(deduplicateById(longTerm));
        semantic = deduplicateProfileSlots(deduplicateById(semantic));
        recordReadFeedback(shortTerm, longTerm, semantic);

        return MemoryContext.builder()
                .conversationId(request.conversationId())
                .userId(userId)
                .currentQuestion(request.currentQuestion())
                .workingMemory(Collections.emptyList())
                .correctionMemories(corrections)
                .profileMemories(profile)
                .shortTermMemories(shortTerm)
                .longTermMemories(longTerm)
                .semanticMemories(semantic)
                .promptMessages(Collections.emptyList())
                .build();
    }

    @Override
    public void writeMemory(MemoryWriteRequest request) {
        ingest(new MemoryIngestionCommand(request));
    }

    @Override
    public MemoryIngestionResult ingest(MemoryIngestionCommand command) {
        if (!options.captureEnabled()) {
            return MemoryIngestionResult.ignored("capture_disabled");
        }
        MemoryWriteRequest request = command == null ? null : command.writeRequest();
        if (request == null || isBlank(request.userId()) || request.message() == null) {
            return MemoryIngestionResult.ignored("invalid_request");
        }
        ChatMessage message = request.message();
        if (message.getRole() != ChatRole.USER || isBlank(message.getContent())) {
            return MemoryIngestionResult.ignored("non_user_or_blank_message");
        }
        String operationId = operationId(command, request);
        String tenantId = tenantId(command);
        MemoryOperation operation = buildOperation(operationId, tenantId, command, request, message.getContent());
        if (!memoryOperationLogPort.tryStart(operation)) {
            return MemoryIngestionResult.ignored("duplicate_operation");
        }
        try {
            MemoryIngestionResult result = executeIngestion(operationId, tenantId, request, message);
            markOperationCompleted(operationId, result, decisionMap(result));
            return result;
        } catch (RuntimeException ex) {
            memoryOperationLogPort.markFailed(operationId,
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            throw ex;
        }
    }

    @Override
    public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
        MemoryContext context = loadMemory(request);
        List<MemoryItem> all = new ArrayList<>();
        all.addAll(context.getShortTermMemories());
        all.addAll(context.getLongTermMemories());
        all.addAll(context.getSemanticMemories());
        all.addAll(0, context.getProfileMemories());
        all.addAll(0, context.getCorrectionMemories());
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

    private List<MemoryItem> loadCorrections(String userId) {
        try {
            return correctionLedgerPort.listActive(userId, "default", options.semanticLimit()).stream()
                    .map(this::toCorrectionItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("加载纠错本失败: userId={}", userId, ex);
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

    private Optional<MemoryRecord> safeFindById(
            com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort port, String memoryId) {
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
                        "status", fact.status())))
                .importanceScore(1D)
                .confidenceLevel(fact.confidenceLevel())
                .createTime(fact.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private MemoryIngestionResult executeIngestion(String operationId,
                                                   String tenantId,
                                                   MemoryWriteRequest request,
                                                   ChatMessage message) {
        SanitizedMemoryInput sanitized = memorySanitizer.sanitize(message.getContent());
        if (sanitized.rejected()) {
            return MemoryIngestionResult.rejected(sanitized.reason(), Map.of("signals", sanitized.signals()));
        }
        MemoryPreFilterResult preFilterResult = memoryPreFilter.filter(sanitized.content());
        if (!preFilterResult.accepted()) {
            return MemoryIngestionResult.ignored(preFilterResult.reason());
        }
        MemoryClassificationResult classification = memorySemanticClassifier.classify(sanitized.content());
        if (classification.action() == MemoryIngestionAction.IGNORE && classification.decision() == null) {
            return MemoryIngestionResult.ignored(classification.reason());
        }
        if (classification.action() == MemoryIngestionAction.IGNORE) {
            return MemoryIngestionResult.rejected(classification.reason());
        }
        MemorySchemaValidationResult validation = memorySchemaValidator.validate(classification);
        if (!validation.valid()) {
            return MemoryIngestionResult.rejected(validation.reason());
        }
        if (classification.action() == MemoryIngestionAction.UPDATE) {
            List<String> operations = captureCorrection(request, tenantId, classification.correction());
            OccupationCorrection correction = classification.correction();
            return MemoryIngestionResult.accepted(MemoryIngestionAction.UPDATE, operations, Map.of(
                    "targetKind", "PROFILE_SLOT",
                    "targetKey", "identity.occupation",
                    "incorrectValue", correction.incorrectValue(),
                    "correctValue", correction.correctValue()));
        }
        MemoryCaptureDecision decision = classification.decision();
        String profileSlot = profileSlot(decision);
        String profileGenerationId = isBlank(profileSlot) ? "" : profileSlot + ":" + UUID.randomUUID();
        Map<String, Object> metadata = captureMetadata(operationId, tenantId, request, message, decision);
        if (!isBlank(profileSlot)) {
            metadata.put("profileSlot", profileSlot);
            metadata.put("generationId", profileGenerationId);
        }
        MemoryRecord record = new MemoryRecord(
                memoryId(request),
                MemoryLayer.SHORT_TERM.name(),
                decision.type(),
                decision.content(),
                metadata,
                java.time.Instant.now());
        shortTermPort.save(record);
        List<String> operations = new ArrayList<>();
        operations.add("SHORT_TERM_SAVE");
        if (captureProfileFact(request, tenantId, decision, profileSlot, profileGenerationId)) {
            operations.add("PROFILE_UPSERT");
        }
        operations.add(indexMemoryOrEnqueueOutbox(record, request.userId(), tenantId));
        return MemoryIngestionResult.accepted(MemoryIngestionAction.ADD, operations, Map.of(
                "memoryType", decision.type(),
                "valueScore", decision.valueScore(),
                "riskScore", decision.riskScore(),
                "captureReasons", decision.reasons(),
                "captureSignals", decision.signals()));
    }

    private String indexMemoryOrEnqueueOutbox(MemoryRecord record, String userId, String tenantId) {
        try {
            memoryVectorPort.upsert(record.id(), userId, record.content(), DEFAULT_VECTOR_EMBEDDING_MODEL);
            return "VECTOR_UPSERT";
        } catch (RuntimeException ex) {
            LOG.warn("记忆向量索引失败，已转入outbox: memoryId={}, userId={}, error={}",
                    record.id(), userId, Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorUpsert(
                    record,
                    userId,
                    tenantId,
                    DEFAULT_VECTOR_EMBEDDING_MODEL,
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
            return "VECTOR_OUTBOX_ENQUEUE";
        }
    }

    private List<String> captureCorrection(MemoryWriteRequest request, String tenantId, OccupationCorrection correction) {
        String generationId = "identity.occupation:" + UUID.randomUUID();
        List<String> sourceIds = isBlank(request.messageId()) ? List.of() : List.of(request.messageId());
        try {
            correctionLedgerPort.upsert(new CorrectionCommand(
                    request.userId(),
                    tenantId,
                    "PROFILE_CORRECTION",
                    "PROFILE_SLOT",
                    "identity.occupation",
                    correction.incorrectValue(),
                    correction.correctValue(),
                    "用户纠正职业画像：" + correction.incorrectValue() + " -> " + correction.correctValue(),
                    sourceIds,
                    generationId));
            profileMemoryPort.upsert(new ProfileFactUpdate(
                    request.userId(),
                    tenantId,
                    "identity.occupation",
                    correction.correctValue(),
                    0.95D,
                    "explicit_user_correction",
                    sourceIds,
                    generationId));
            markProfileSlotFragmentsObsolete(request.userId(), tenantId, "identity.occupation", generationId);
        } catch (RuntimeException ex) {
            LOG.warn("写入Profile纠错失败: userId={}", request.userId(), ex);
        }
        return List.of("CORRECTION_UPSERT", "PROFILE_UPSERT");
    }

    private boolean captureProfileFact(MemoryWriteRequest request,
                                       String tenantId,
                                       MemoryCaptureDecision decision,
                                       String slotKey,
                                       String generationId) {
        if (isBlank(slotKey)) {
            return false;
        }
        String value = profileValue(slotKey, decision.content());
        if (isBlank(value)) {
            return false;
        }
        try {
            profileMemoryPort.upsert(new ProfileFactUpdate(
                    request.userId(),
                    tenantId,
                    slotKey,
                    value,
                    decision.confidenceLevel(),
                    "explicit_user_memory",
                    isBlank(request.messageId()) ? List.of() : List.of(request.messageId()),
                    generationId));
            markProfileSlotFragmentsObsolete(request.userId(), tenantId, slotKey, generationId);
            return true;
        } catch (RuntimeException ex) {
            LOG.warn("写入Profile KV失败: userId={}, slot={}", request.userId(), slotKey, ex);
            return false;
        }
    }

    private void markProfileSlotFragmentsObsolete(String userId,
                                                  String tenantId,
                                                  String profileSlot,
                                                  String activeGenerationId) {
        try {
            memoryLifecyclePort.markObsoleteByProfileSlot(
                    userId,
                    tenantId,
                    profileSlot,
                    activeGenerationId,
                    "profile slot updated");
        } catch (RuntimeException ex) {
            LOG.warn("Profile slot鏃х鐗囧け鏁堝け璐? userId={}, slot={}", userId, profileSlot, ex);
        }
    }

    private String profileSlot(MemoryCaptureDecision decision) {
        if (!"PROFILE".equalsIgnoreCase(decision.type()) && !"FACT".equalsIgnoreCase(decision.type())) {
            return "";
        }
        String content = Objects.requireNonNullElse(decision.content(), "");
        String normalized = content.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "occupation", "profession", "job", "student", "teacher")
                || containsAny(content, "职业", "身份", "工作", "学生", "老师", "教师")) {
            return "identity.occupation";
        }
        return "";
    }

    private String profileValue(String slotKey, String content) {
        if (!"identity.occupation".equals(slotKey)) {
            return "";
        }
        return normalizeOccupationValue(stripProfilePrefix(content));
    }

    private String stripProfilePrefix(String content) {
        return Objects.requireNonNullElse(content, "")
                .trim()
                .replaceFirst("^我的(职业|身份|工作|岗位|角色)是", "")
                .replaceFirst("^我是", "")
                .replaceFirst("^我是一名", "")
                .replaceFirst("^我是一位", "")
                .replaceFirst("^一名", "")
                .replaceFirst("^一位", "")
                .trim();
    }

    private String normalizeOccupationValue(String value) {
        return OccupationCorrection.normalizeOccupationValue(value);
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

    private void recordReadFeedback(List<MemoryItem> shortTerm,
                                    List<MemoryItem> longTerm,
                                    List<MemoryItem> semantic) {
        Instant referencedAt = Instant.now();
        recordReadFeedback(MemoryLayer.SHORT_TERM, shortTerm, referencedAt);
        recordReadFeedback(MemoryLayer.LONG_TERM, longTerm, referencedAt);
        recordReadFeedback(MemoryLayer.SEMANTIC, semantic, referencedAt);
    }

    private void recordReadFeedback(MemoryLayer layer, List<MemoryItem> items, Instant referencedAt) {
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
                LOG.debug("璁板繂璇诲彇鍙嶉澶辫触: layer={}, memoryId={}", layer, item.getId(), ex);
            }
        }
    }

    private Map<String, Object> captureMetadata(String operationId,
                                                String tenantId,
                                                MemoryWriteRequest request,
                                                ChatMessage message,
                                                MemoryCaptureDecision decision) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", request.userId());
        metadata.put("tenantId", tenantId);
        metadata.put("operationId", operationId);
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

    private MemoryOperation buildOperation(String operationId,
                                           String tenantId,
                                           MemoryIngestionCommand command,
                                           MemoryWriteRequest request,
                                           String content) {
        MemoryOperationType operationType = inferOperationType(content);
        return new MemoryOperation(
                operationId,
                request.userId(),
                tenantId,
                operationType,
                inferTargetKind(operationType, content),
                inferTargetKey(operationType, content),
                requestMap(command, request, content),
                MemoryValueAssessor.POLICY_VERSION,
                Instant.now());
    }

    private MemoryOperationType inferOperationType(String content) {
        if (OccupationCorrection.extract(content) != null) {
            return MemoryOperationType.UPDATE;
        }
        SanitizedMemoryInput sanitized = memorySanitizer.sanitize(content);
        if (sanitized.rejected()) {
            return MemoryOperationType.IGNORE;
        }
        MemoryPreFilterResult preFilterResult = memoryPreFilter.filter(sanitized.content());
        if (!preFilterResult.accepted()) {
            return MemoryOperationType.IGNORE;
        }
        MemoryClassificationResult classification = memorySemanticClassifier.classify(sanitized.content());
        if (classification.action() == MemoryIngestionAction.UPDATE) {
            return MemoryOperationType.UPDATE;
        }
        if (classification.action() == MemoryIngestionAction.ADD) {
            return MemoryOperationType.ADD;
        }
        if (classification.action() == MemoryIngestionAction.REVIEW) {
            return MemoryOperationType.REVIEW;
        }
        return MemoryOperationType.IGNORE;
    }

    private String inferTargetKind(MemoryOperationType operationType, String content) {
        if (operationType == MemoryOperationType.UPDATE || OccupationCorrection.extract(content) != null) {
            return "PROFILE_SLOT";
        }
        if (operationType == MemoryOperationType.ADD) {
            return "SHORT_TERM_MEMORY";
        }
        return "NONE";
    }

    private String inferTargetKey(MemoryOperationType operationType, String content) {
        if (operationType == MemoryOperationType.UPDATE || OccupationCorrection.extract(content) != null) {
            return "identity.occupation";
        }
        return "";
    }

    private Map<String, Object> requestMap(MemoryIngestionCommand command,
                                           MemoryWriteRequest request,
                                           String content) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source", command == null ? "" : command.source());
        values.put("conversationId", Objects.requireNonNullElse(request.conversationId(), ""));
        values.put("messageId", Objects.requireNonNullElse(request.messageId(), ""));
        values.put("role", request.message() == null ? "" : request.message().getRole().name());
        values.put("content", Objects.requireNonNullElse(content, ""));
        return values;
    }

    private String operationId(MemoryIngestionCommand command, MemoryWriteRequest request) {
        if (command != null && !isBlank(command.operationId())) {
            return command.operationId();
        }
        return new MemoryIngestionCommand(request).operationId();
    }

    private String tenantId(MemoryIngestionCommand command) {
        if (command != null && !isBlank(command.tenantId())) {
            return command.tenantId();
        }
        return "default";
    }

    private void markOperationCompleted(String operationId,
                                        MemoryIngestionResult result,
                                        Map<String, Object> decision) {
        memoryOperationLogPort.markCompleted(operationId, operationStatus(result), decision);
    }

    private MemoryOperationStatus operationStatus(MemoryIngestionResult result) {
        if (result == null) {
            return MemoryOperationStatus.FAILED;
        }
        return switch (result.status()) {
            case ACCEPTED -> MemoryOperationStatus.SUCCEEDED;
            case REJECTED -> result.action() == MemoryIngestionAction.REVIEW
                    ? MemoryOperationStatus.REVIEW
                    : MemoryOperationStatus.REJECTED;
            case IGNORED -> MemoryOperationStatus.IGNORED;
            case FAILED -> MemoryOperationStatus.FAILED;
        };
    }

    private Map<String, Object> decisionMap(MemoryIngestionResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (result == null) {
            values.put("status", MemoryIngestionStatus.FAILED.name());
            values.put("action", MemoryIngestionAction.IGNORE.name());
            values.put("reason", "empty_result");
            values.put("operations", List.of());
            return values;
        }
        values.put("status", result.status().name());
        values.put("action", result.action().name());
        values.put("reason", result.reason());
        values.put("operations", result.operations());
        values.putAll(result.details());
        return values;
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
                || metadata.contains("\"semanticKey\": \"profile:occupation\"")
                || metadata.contains("\"semanticKey\":\"identity.occupation\"")
                || metadata.contains("\"semanticKey\": \"identity.occupation\"")
                || metadata.contains("\"profileSlot\":\"identity.occupation\"")
                || metadata.contains("\"profileSlot\": \"identity.occupation\"")) {
            return "identity.occupation";
        }
        if (!isProfileLike(item)) {
            return "";
        }
        String content = Objects.requireNonNullElse(item.getContent(), "");
        String normalized = content.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "occupation", "profession", "job", "student", "teacher")
                || containsAny(content, "职业", "身份", "工作", "学生", "老师", "教师")) {
            return "identity.occupation";
        }
        return "";
    }

    private boolean isProfileLike(MemoryItem item) {
        String type = Objects.requireNonNullElse(item.getType(), "");
        return "PROFILE".equalsIgnoreCase(type) || "FACT".equalsIgnoreCase(type);
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
                .correctionMemories(Collections.emptyList())
                .profileMemories(Collections.emptyList())
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
