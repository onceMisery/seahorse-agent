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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.RefinedMemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String REVIEW_CANDIDATE_PREFIX = "review-";
    private static final String REVIEW_DEFAULT_LAYER = "SHORT_TERM";
    private static final String METADATA_CONTENT = "content";
    private static final String METADATA_CONFIDENCE = "confidence";
    private static final String METADATA_IMPORTANCE = "importance";
    private static final String METADATA_VALUE_SCORE = "valueScore";
    private static final String METADATA_RISK_SCORE = "riskScore";
    private static final String METADATA_SOURCE_MESSAGE_IDS = "sourceMessageIds";
    private static final String METADATA_TARGET_LAYER = "targetLayer";
    private static final String METADATA_REVIEW_REQUESTED_ACTION = "reviewRequestedAction";
    private static final String METADATA_TARGET_MEMORY_ID = "targetMemoryId";
    private static final String METADATA_REFINER_OPERATION_INDEX = "refinerOperationIndex";
    private static final String METADATA_REFINER_OPERATION_COUNT = "refinerOperationCount";
    private static final String METADATA_REFINER_BATCH = "refinerBatch";
    private static final String METADATA_REFINER_BATCH_OPERATION_COUNT = "refinerBatchOperationCount";
    private static final String METADATA_REFINER_BATCH_DELETE_RATIO = "refinerBatchDeleteRatio";
    private static final String METADATA_REFINER_BATCH_CIRCUIT_REASON = "refinerBatchCircuitReason";
    private static final String METADATA_REFINER_BATCH_CIRCUIT_TYPE = "refinerBatchCircuitType";
    private static final String METADATA_REFINER_BATCH_OPERATIONS = "refinerBatchOperations";
    private static final String METADATA_IMPORTANCE_SCORE = "importanceScore";
    private static final String METADATA_CONFIDENCE_LEVEL = "confidenceLevel";
    private static final String REFINER_BATCH_CIRCUIT_BREAKER_REASON = "refiner_batch_circuit_breaker";
    private static final String REFINER_STATUS_CIRCUIT_BREAKER = "circuit_breaker";
    private static final String REFINER_BATCH_TARGET_KIND = "REFINER_BATCH";
    private static final String REFINER_BATCH_CIRCUIT_OPERATION_COUNT = "OPERATION_COUNT";
    private static final String REFINER_BATCH_CIRCUIT_DELETE_RATIO = "DELETE_RATIO";
    private static final String REFINER_BATCH_REASON_OPERATION_COUNT_EXCEEDED = "operation_count_exceeded";
    private static final String REFINER_BATCH_REASON_DELETE_RATIO_EXCEEDED = "delete_ratio_exceeded";
    private static final int REFINER_READ_MASK_PER_LAYER_LIMIT = 3;
    private static final int REFINER_TARGET_ZONE_TURN_COUNT = 3;
    private static final int REFINER_STICKY_ANCHOR_LIMIT = 5;
    private static final double REFINER_STICKY_ANCHOR_IMPORTANCE_THRESHOLD = 0.85D;
    private static final double REFINER_STICKY_ANCHOR_CONFIDENCE_THRESHOLD = 0.90D;
    private static final Pattern CONTEXT_TURN_HEADER_PATTERN = Pattern.compile("\\bturn_\\d+:");
    private static final Pattern CONTEXT_TURN_INDEX_PATTERN = Pattern.compile("\\bturn_(\\d+):");
    private static final Pattern CONTEXT_SOURCE_SPAN_PATTERN = Pattern.compile(
            "\\bspan_(\\d+):\\s*(.*?)(?=\\s+span_\\d+:\\s*|\\s*$)", Pattern.DOTALL);
    private static final String OPERATION_VECTOR_DELETE = "VECTOR_DELETE";
    private static final String OPERATION_VECTOR_DELETE_OUTBOX_ENQUEUE = "VECTOR_DELETE_OUTBOX_ENQUEUE";
    private static final String OPERATION_KEYWORD_DELETE_OUTBOX_ENQUEUE = "KEYWORD_DELETE_OUTBOX_ENQUEUE";
    private static final String OPERATION_GRAPH_DELETE_OUTBOX_ENQUEUE = "GRAPH_DELETE_OUTBOX_ENQUEUE";

    private record IngestionExecution(MemoryIngestionResult result, MemoryClassificationResult classification) {

        private IngestionExecution {
            Objects.requireNonNull(result, "result must not be null");
        }
    }

    private record MemoryRefinementContextZones(String referenceZone,
                                                String targetZone,
                                                List<String> targetSourceMessageIds) {

        private MemoryRefinementContextZones(String referenceZone, String targetZone) {
            this(referenceZone, targetZone, List.of());
        }

        private MemoryRefinementContextZones {
            referenceZone = Objects.requireNonNullElse(referenceZone, "");
            targetZone = Objects.requireNonNullElse(targetZone, "");
            targetSourceMessageIds = targetSourceMessageIds == null
                    ? List.of()
                    : List.copyOf(targetSourceMessageIds.stream()
                    .filter(messageId -> messageId != null && !messageId.isBlank())
                    .distinct()
                    .toList());
        }
    }

    private record ContextBlockTurn(int turnIndex, String turnBlock, String sourceSpan) {

        private ContextBlockTurn {
            turnBlock = Objects.requireNonNullElse(turnBlock, "");
            sourceSpan = Objects.requireNonNullElse(sourceSpan, "");
        }
    }

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
    private final MemoryRetrievalPipelinePort memoryRetrievalPipelinePort;
    private final ObjectMapper objectMapper;
    private final MemoryEngineOptions options;
    private final MemoryPolicyConfigPort memoryPolicyConfigPort;
    private final MemoryCaptureCandidateExtractor captureCandidateExtractor;
    private final MemoryValueAssessor memoryValueAssessor;
    private final MemoryRefinerPort memoryRefinerPort;
    private final MemoryReviewCandidatePort memoryReviewCandidatePort;
    private final MemorySanitizer memorySanitizer;
    private final MemoryPreFilter memoryPreFilter;
    private final MemorySemanticClassifier memorySemanticClassifier;
    private final MemorySchemaValidator memorySchemaValidator;
    private final ProfileSlotResolver profileSlotResolver;

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
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort,
                memoryOutboxPort, businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort,
                (MemoryRetrievalPipelinePort) null);
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
                                   MemoryPolicyConfigPort memoryPolicyConfigPort,
                                   MemoryRefinerPort memoryRefinerPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort,
                memoryOutboxPort, businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort,
                null, memoryRefinerPort);
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
                                   MemoryPolicyConfigPort memoryPolicyConfigPort,
                                   MemoryRetrievalPipelinePort memoryRetrievalPipelinePort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort, memoryOutboxPort,
                businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort, memoryRetrievalPipelinePort,
                MemoryRefinerPort.noop());
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
                                   MemoryPolicyConfigPort memoryPolicyConfigPort,
                                   MemoryRetrievalPipelinePort memoryRetrievalPipelinePort,
                                   MemoryRefinerPort memoryRefinerPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort, memoryOutboxPort,
                businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort,
                memoryRetrievalPipelinePort, memoryRefinerPort, MemoryReviewCandidatePort.noop());
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
                                   MemoryPolicyConfigPort memoryPolicyConfigPort,
                                   MemoryRetrievalPipelinePort memoryRetrievalPipelinePort,
                                   MemoryRefinerPort memoryRefinerPort,
                                   MemoryReviewCandidatePort memoryReviewCandidatePort) {
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
        this.memoryPolicyConfigPort = Objects.requireNonNullElseGet(memoryPolicyConfigPort,
                MemoryPolicyConfigPort::defaults);
        this.memoryRetrievalPipelinePort = memoryRetrievalPipelinePort == null
                ? new DefaultMemoryRetrievalPipeline(
                this.shortTermPort,
                this.longTermPort,
                this.semanticPort,
                this.objectMapper,
                this.options,
                this.profileMemoryPort,
                this.correctionLedgerPort,
                this.memoryRouterPort,
                this.memoryVectorPort,
                this.businessDocumentRetrieverPort,
                this.memoryLifecyclePort)
                : memoryRetrievalPipelinePort;
        this.captureCandidateExtractor = new MemoryCaptureCandidateExtractor();
        this.memoryValueAssessor = new MemoryValueAssessor(this.memoryPolicyConfigPort);
        this.memoryRefinerPort = Objects.requireNonNullElseGet(memoryRefinerPort, MemoryRefinerPort::noop);
        this.memoryReviewCandidatePort = Objects.requireNonNullElseGet(memoryReviewCandidatePort,
                MemoryReviewCandidatePort::noop);
        this.memorySanitizer = new MemorySanitizer();
        this.memoryPreFilter = new MemoryPreFilter();
        this.memorySemanticClassifier = new MemorySemanticClassifier(captureCandidateExtractor, memoryValueAssessor);
        this.memorySchemaValidator = new MemorySchemaValidator(memorySanitizer);
        this.profileSlotResolver = new ProfileSlotResolver();
    }

    @Override
    public MemoryContext loadMemory(MemoryLoadRequest request) {
        return memoryRetrievalPipelinePort.load(request);
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
        boolean reviewDeleteApply = isReviewDeleteApply(command);
        if (message.getRole() != ChatRole.USER || (isBlank(message.getContent()) && !reviewDeleteApply)) {
            return MemoryIngestionResult.ignored("non_user_or_blank_message");
        }
        String operationId = operationId(command, request);
        String tenantId = tenantId(command);
        MemoryOperation operation = buildOperation(operationId, tenantId, command, request, message.getContent());
        if (!memoryOperationLogPort.tryStart(operation)) {
            return MemoryIngestionResult.ignored("duplicate_operation");
        }
        try {
            IngestionExecution execution = executeIngestion(operationId, tenantId, command, request, message);
            markOperationCompleted(operationId, execution.result(), decisionMap(execution.result(), execution.classification()));
            return execution.result();
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

    private IngestionExecution executeIngestion(String operationId,
                                                String tenantId,
                                                MemoryIngestionCommand command,
                                                MemoryWriteRequest request,
                                                ChatMessage message) {
        MemoryReviewApplyDirective directive = command == null ? null : command.reviewApplyDirective();
        if (directive != null && directive.requestedAction() == MemoryIngestionAction.DELETE) {
            return executeReviewDeleteApply(tenantId, request, directive);
        }
        SanitizedMemoryInput sanitized = memorySanitizer.sanitize(message.getContent());
        if (sanitized.rejected()) {
            return new IngestionExecution(
                    MemoryIngestionResult.rejected(sanitized.reason(), Map.of("signals", sanitized.signals())),
                    null);
        }
        MemoryClassificationResult reviewClassification = reviewApplyClassification(directive, sanitized.content());
        if (reviewClassification != null) {
            if (reviewClassification.action() == MemoryIngestionAction.IGNORE) {
                return new IngestionExecution(MemoryIngestionResult.rejected(reviewClassification.reason()),
                        reviewClassification);
            }
            MemorySchemaValidationResult validation = memorySchemaValidator.validate(reviewClassification);
            if (!validation.valid()) {
                return new IngestionExecution(MemoryIngestionResult.rejected(validation.reason()),
                        reviewClassification);
            }
            return executeAcceptedClassification(operationId, tenantId, request, message, reviewClassification);
        }
        MemoryPreFilterResult preFilterResult = memoryPreFilter.filter(sanitized.content());
        if (!preFilterResult.accepted()) {
            return new IngestionExecution(MemoryIngestionResult.ignored(preFilterResult.reason()), null);
        }
        MemoryClassificationResult classification = memorySemanticClassifier.classify(sanitized.content());
        classification = refineClassification(operationId, tenantId, command, request, sanitized.content(), classification);
        if (classification.refinedDelta() != null && classification.refinedDelta().metadata().containsKey("refinerBatch")) {
            return executeRefinerBatch(operationId, tenantId, request, message, classification);
        }
        if (classification.action() == MemoryIngestionAction.IGNORE && classification.decision() == null) {
            return new IngestionExecution(MemoryIngestionResult.ignored(classification.reason()), classification);
        }
        if (classification.action() == MemoryIngestionAction.IGNORE) {
            return new IngestionExecution(MemoryIngestionResult.rejected(classification.reason()), classification);
        }
        if (classification.action() == MemoryIngestionAction.REVIEW) {
            return executeReviewStaging(operationId, tenantId, request, classification);
        }
        MemorySchemaValidationResult validation = memorySchemaValidator.validate(classification);
        if (!validation.valid()) {
            return new IngestionExecution(MemoryIngestionResult.rejected(validation.reason()), classification);
        }
        if (classification.action() == MemoryIngestionAction.UPDATE) {
            List<String> operations = captureCorrection(request, tenantId, classification.correction());
            OccupationCorrection correction = classification.correction();
            return new IngestionExecution(MemoryIngestionResult.accepted(MemoryIngestionAction.UPDATE, operations, Map.of(
                    "targetKind", "PROFILE_SLOT",
                    "targetKey", "identity.occupation",
                    "incorrectValue", correction.incorrectValue(),
                    "correctValue", correction.correctValue())), classification);
        }
        return executeAcceptedClassification(operationId, tenantId, request, message, classification);
    }

    private IngestionExecution executeReviewDeleteApply(String tenantId,
                                                        MemoryWriteRequest request,
                                                        MemoryReviewApplyDirective directive) {
        String targetMemoryId = targetMemoryId(directive);
        MemoryLayer layer = targetLayer(directive.targetLayer());
        if (isBlank(targetMemoryId)) {
            return new IngestionExecution(MemoryIngestionResult.rejected(
                    "review_delete_target_key_required",
                    Map.of(
                            METADATA_REVIEW_REQUESTED_ACTION, MemoryIngestionAction.DELETE.name(),
                            METADATA_TARGET_LAYER, layer.name())),
                    null);
        }
        boolean deleted = memoryStoreFor(layer).deleteById(targetMemoryId);
        if (!deleted) {
            return new IngestionExecution(MemoryIngestionResult.rejected(
                    "review_delete_target_not_found",
                    Map.of(
                            METADATA_REVIEW_REQUESTED_ACTION, MemoryIngestionAction.DELETE.name(),
                            METADATA_TARGET_LAYER, layer.name(),
                            METADATA_TARGET_MEMORY_ID, targetMemoryId)),
                    null);
        }
        List<String> operations = new ArrayList<>();
        operations.add(layer.name() + "_DELETE");
        operations.addAll(deleteIndexesOrEnqueueOutbox(targetMemoryId, request.userId(), tenantId));
        return new IngestionExecution(MemoryIngestionResult.accepted(
                MemoryIngestionAction.DELETE,
                operations,
                Map.of(
                        METADATA_REVIEW_REQUESTED_ACTION, MemoryIngestionAction.DELETE.name(),
                        METADATA_TARGET_LAYER, layer.name(),
                        METADATA_TARGET_MEMORY_ID, targetMemoryId)),
                null);
    }

    private IngestionExecution executeAcceptedClassification(String operationId,
                                                             String tenantId,
                                                             MemoryWriteRequest request,
                                                             ChatMessage message,
                                                             MemoryClassificationResult classification) {
        MemoryCaptureDecision decision = classification.decision();
        String profileSlot = profileSlot(decision, classification);
        String profileGenerationId = isBlank(profileSlot) ? "" : profileSlot + ":" + UUID.randomUUID();
        Map<String, Object> metadata = captureMetadata(operationId, tenantId, request, message, decision);
        addRefinedMetadata(metadata, classification);
        if (!isBlank(profileSlot)) {
            metadata.put("profileSlot", profileSlot);
            metadata.put("generationId", profileGenerationId);
        }
        MemoryLayer targetLayer = targetLayer(classification);
        MemoryRecord record = new MemoryRecord(
                memoryId(request, classification),
                targetLayer.name(),
                decision.type(),
                decision.content(),
                metadata,
                java.time.Instant.now());
        List<String> operations = new ArrayList<>();
        operations.add(saveMemory(record, targetLayer));
        if (captureProfileFact(request, tenantId, decision, profileSlot, profileGenerationId)) {
            operations.add("PROFILE_UPSERT");
        }
        operations.addAll(indexMemoryOrEnqueueOutbox(record, request.userId(), tenantId));
        return new IngestionExecution(MemoryIngestionResult.accepted(resultAction(classification), operations, Map.of(
                "memoryType", decision.type(),
                "valueScore", decision.valueScore(),
                "riskScore", decision.riskScore(),
                "captureReasons", decision.reasons(),
                "captureSignals", decision.signals())), classification);
    }

    private IngestionExecution executeRefinerBatch(String operationId,
                                                   String tenantId,
                                                   MemoryWriteRequest request,
                                                   ChatMessage message,
                                                   MemoryClassificationResult batchClassification) {
        Object batch = batchClassification.refinedDelta().metadata().get("refinerBatch");
        if (!(batch instanceof List<?> rawClassifications) || rawClassifications.isEmpty()) {
            return new IngestionExecution(MemoryIngestionResult.ignored("empty_refiner_batch"), batchClassification);
        }
        List<MemoryClassificationResult> classifications = rawClassifications.stream()
                .filter(MemoryClassificationResult.class::isInstance)
                .map(MemoryClassificationResult.class::cast)
                .toList();
        if (classifications.isEmpty()) {
            return new IngestionExecution(MemoryIngestionResult.ignored("empty_refiner_batch"), batchClassification);
        }
        for (MemoryClassificationResult classification : classifications) {
            MemorySchemaValidationResult validation = memorySchemaValidator.validate(classification);
            if (!validation.valid()) {
                return new IngestionExecution(MemoryIngestionResult.rejected(validation.reason()), classification);
            }
        }

        List<String> operations = new ArrayList<>();
        int acceptedCount = 0;
        int reviewCount = 0;
        int ignoredCount = 0;
        for (MemoryClassificationResult classification : classifications) {
            IngestionExecution execution = classification.action() == MemoryIngestionAction.REVIEW
                    ? executeReviewStaging(operationId, tenantId, request, classification)
                    : executeAcceptedClassification(operationId, tenantId, request, message, classification);
            operations.addAll(execution.result().operations());
            if (execution.result().status() == MemoryIngestionStatus.ACCEPTED) {
                acceptedCount++;
            }
            if (execution.result().action() == MemoryIngestionAction.REVIEW) {
                reviewCount++;
            }
            if (execution.result().status() == MemoryIngestionStatus.IGNORED) {
                ignoredCount++;
            }
        }
        if (acceptedCount == 0 && reviewCount > 0) {
            return new IngestionExecution(MemoryIngestionResult.review(batchClassification.reason()), batchClassification);
        }
        if (acceptedCount == 0) {
            return new IngestionExecution(MemoryIngestionResult.ignored(
                    "refiner_batch_no_effect",
                    Map.of(
                            "ignoredRefinerOperations", ignoredCount,
                            METADATA_REFINER_OPERATION_COUNT, classifications.size())),
                    batchClassification);
        }
        return new IngestionExecution(MemoryIngestionResult.accepted(
                MemoryIngestionAction.ADD,
                operations,
                Map.of(
                        "acceptedRefinerOperations", acceptedCount,
                        "reviewRefinerOperations", reviewCount,
                        "ignoredRefinerOperations", ignoredCount,
                        METADATA_REFINER_OPERATION_COUNT, classifications.size())),
                batchClassification);
    }

    private List<String> indexMemoryOrEnqueueOutbox(MemoryRecord record, String userId, String tenantId) {
        List<String> operations = new ArrayList<>();
        try {
            memoryVectorPort.upsert(record.id(), userId, record.content(), DEFAULT_VECTOR_EMBEDDING_MODEL);
            operations.add("VECTOR_UPSERT");
        } catch (RuntimeException ex) {
            LOG.warn("记忆向量索引失败，已转入outbox: memoryId={}, userId={}, error={}",
                    record.id(), userId, Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorUpsert(
                    record,
                    userId,
                    tenantId,
                    DEFAULT_VECTOR_EMBEDDING_MODEL,
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
            operations.add("VECTOR_OUTBOX_ENQUEUE");
        }
        enqueueOptionalDerivedIndex(record, userId, tenantId, operations);
        return operations;
    }

    private List<String> deleteIndexesOrEnqueueOutbox(String memoryId, String userId, String tenantId) {
        List<String> operations = new ArrayList<>();
        try {
            memoryVectorPort.delete(memoryId, userId, tenantId);
            operations.add(OPERATION_VECTOR_DELETE);
        } catch (RuntimeException ex) {
            LOG.warn("记忆向量删除失败，已转入outbox: memoryId={}, userId={}, error={}",
                    memoryId, userId, Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorDelete(memoryId, userId, tenantId));
            operations.add(OPERATION_VECTOR_DELETE_OUTBOX_ENQUEUE);
        }
        enqueueOptionalDerivedDelete(memoryId, userId, tenantId, operations);
        return operations;
    }

    private void enqueueOptionalDerivedIndex(MemoryRecord record,
                                             String userId,
                                             String tenantId,
                                             List<String> operations) {
        if (options.keywordIndexOutboxEnabled()) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.keywordUpsert(record, userId, tenantId));
            operations.add("KEYWORD_OUTBOX_ENQUEUE");
        }
        if (options.graphIndexOutboxEnabled()) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.graphUpsert(record, userId, tenantId));
            operations.add("GRAPH_OUTBOX_ENQUEUE");
        }
    }

    private void enqueueOptionalDerivedDelete(String memoryId,
                                              String userId,
                                              String tenantId,
                                              List<String> operations) {
        if (options.keywordIndexOutboxEnabled()) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.keywordDelete(memoryId, userId, tenantId));
            operations.add(OPERATION_KEYWORD_DELETE_OUTBOX_ENQUEUE);
        }
        if (options.graphIndexOutboxEnabled()) {
            memoryOutboxPort.enqueue(MemoryOutboxPort.MemoryOutboxTask.graphDelete(memoryId, userId, tenantId));
            operations.add(OPERATION_GRAPH_DELETE_OUTBOX_ENQUEUE);
        }
    }

    private MemoryClassificationResult reviewApplyClassification(MemoryReviewApplyDirective directive,
                                                                 String content) {
        if (directive == null) {
            return null;
        }
        String targetKind = isBlank(directive.targetKind()) ? "FACT" : directive.targetKind();
        Map<String, Object> metadata = new LinkedHashMap<>(directive.metadata());
        metadata.put("status", "review_applied");
        metadata.put(METADATA_REVIEW_REQUESTED_ACTION, directive.requestedAction().name());
        metadata.put(METADATA_TARGET_LAYER, targetLayer(directive.targetLayer()).name());
        metadata.put(METADATA_CONFIDENCE, directive.confidence());
        metadata.put(METADATA_IMPORTANCE, directive.importance());
        metadata.put(METADATA_VALUE_SCORE, directive.valueScore());
        metadata.put(METADATA_RISK_SCORE, directive.riskScore());
        metadata.put(METADATA_SOURCE_MESSAGE_IDS, directive.sourceMessageIds());
        MemoryCaptureDecision decision = MemoryCaptureDecision.refinedAdd(
                content,
                targetKind,
                directive.importance(),
                directive.confidence(),
                directive.valueScore(),
                directive.riskScore(),
                List.of("memory_review_applied"),
                List.of("human_review"));
        return MemoryClassificationResult.refinedAdd(decision, new RefinedMemoryDelta(
                directive.requestedAction(),
                targetKind,
                directive.targetKey(),
                "memory_review_applied",
                metadata));
    }

    private String saveMemory(MemoryRecord record, MemoryLayer targetLayer) {
        MemoryLayer safeLayer = targetLayer == null ? MemoryLayer.SHORT_TERM : targetLayer;
        if (safeLayer == MemoryLayer.LONG_TERM) {
            longTermPort.save(record);
            return "LONG_TERM_SAVE";
        }
        if (safeLayer == MemoryLayer.SEMANTIC) {
            semanticPort.save(record);
            return "SEMANTIC_SAVE";
        }
        shortTermPort.save(record);
        return "SHORT_TERM_SAVE";
    }

    private MemoryStorePort memoryStoreFor(MemoryLayer targetLayer) {
        MemoryLayer safeLayer = targetLayer == null ? MemoryLayer.SHORT_TERM : targetLayer;
        if (safeLayer == MemoryLayer.LONG_TERM) {
            return longTermPort;
        }
        if (safeLayer == MemoryLayer.SEMANTIC) {
            return semanticPort;
        }
        return shortTermPort;
    }

    private MemoryLayer targetLayer(MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta != null) {
            Object value = delta.metadata().get(METADATA_TARGET_LAYER);
            if (value != null && !value.toString().isBlank()) {
                return targetLayer(value.toString());
            }
        }
        return MemoryLayer.SHORT_TERM;
    }

    private MemoryLayer targetLayer(String layer) {
        if (isBlank(layer)) {
            return MemoryLayer.SHORT_TERM;
        }
        try {
            MemoryLayer parsed = MemoryLayer.valueOf(layer.trim().toUpperCase(Locale.ROOT));
            return parsed == MemoryLayer.WORKING ? MemoryLayer.SHORT_TERM : parsed;
        } catch (IllegalArgumentException ex) {
            return MemoryLayer.SHORT_TERM;
        }
    }

    private MemoryClassificationResult refineClassification(String operationId,
                                                            String tenantId,
                                                            MemoryIngestionCommand command,
                                                            MemoryWriteRequest request,
                                                            String sanitizedContent,
                                                            MemoryClassificationResult baseline) {
        if (!options.refinerEnabled()) {
            return baseline;
        }
        try {
            List<MemoryRefinementMemory> existingMemories = currentExistingMemories(request.userId());
            MemoryRefinementContextZones contextZones = refinementContextZones(sanitizedContent);
            MemoryRefinementResult result = memoryRefinerPort.refine(new MemoryRefinementRequest(
                    operationId,
                    tenantId,
                    command == null ? "" : command.source(),
                    request.userId(),
                    request.conversationId(),
                    request.messageId(),
                    sanitizedContent,
                    baseline == null ? MemoryIngestionAction.IGNORE : baseline.action(),
                    baselineMemoryType(baseline),
                    baseline == null ? "" : baseline.reason(),
                    baselineDetails(baseline),
                    existingMemories,
                    contextZones.referenceZone(),
                    contextZones.targetZone(),
                    stickyAnchors(existingMemories)));
            return applyRefinementResult(result, baseline, contextZones);
        } catch (RuntimeException ex) {
            if (!options.refinerFailOpen()) {
                return new MemoryClassificationResult(
                        MemoryIngestionAction.IGNORE,
                        null,
                        null,
                        new RefinedMemoryDelta(
                                MemoryIngestionAction.IGNORE,
                                "",
                                "",
                                "refiner_failed:" + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()),
                                Map.of("status", "failed_closed")),
                        "refiner_failed");
            }
            return withRefinerDelta(
                    baseline,
                    MemoryIngestionAction.IGNORE,
                    "",
                    "",
                    "failed_open:" + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()),
                    Map.of("status", "failed_open"));
        }
    }

    private List<MemoryRefinementMemory> currentExistingMemories(String userId) {
        if (isBlank(userId)) {
            return List.of();
        }
        List<MemoryRefinementMemory> memories = new ArrayList<>();
        collectExistingMemories(memories, MemoryLayer.SHORT_TERM, shortTermPort, userId);
        collectExistingMemories(memories, MemoryLayer.LONG_TERM, longTermPort, userId);
        collectExistingMemories(memories, MemoryLayer.SEMANTIC, semanticPort, userId);
        return List.copyOf(memories);
    }

    private void collectExistingMemories(List<MemoryRefinementMemory> memories,
                                         MemoryLayer layer,
                                         MemoryStorePort store,
                                         String userId) {
        try {
            for (MemoryRecord record : store.listByUser(userId, REFINER_READ_MASK_PER_LAYER_LIMIT)) {
                if (record == null || isBlank(record.id())) {
                    continue;
                }
                memories.add(toRefinementMemory(layer, record));
            }
        } catch (RuntimeException ex) {
            LOG.debug("load refiner read-mask memories failed: layer={}, userId={}", layer, userId, ex);
        }
    }

    private MemoryRefinementMemory toRefinementMemory(MemoryLayer layer, MemoryRecord record) {
        Map<String, Object> metadata = record.metadata();
        return new MemoryRefinementMemory(
                record.id(),
                layer.name(),
                record.type(),
                record.content(),
                stringMetadata(metadata, "targetKind", record.type()),
                stringMetadata(metadata, "targetKey", stringMetadata(metadata, "profileSlot", "")),
                stringMetadata(metadata, "generationId", ""),
                stringMetadata(metadata, "status", "ACTIVE"),
                metadata);
    }

    private List<MemoryRefinementMemory> stickyAnchors(List<MemoryRefinementMemory> existingMemories) {
        if (existingMemories == null || existingMemories.isEmpty()) {
            return List.of();
        }
        return existingMemories.stream()
                .filter(this::isStickyAnchor)
                .limit(REFINER_STICKY_ANCHOR_LIMIT)
                .toList();
    }

    private boolean isStickyAnchor(MemoryRefinementMemory memory) {
        if (memory == null || !"ACTIVE".equalsIgnoreCase(memory.status())) {
            return false;
        }
        String layer = memory.layer().toUpperCase(Locale.ROOT);
        if (!MemoryLayer.LONG_TERM.name().equals(layer) && !MemoryLayer.SEMANTIC.name().equals(layer)) {
            return false;
        }
        return memoryMetadataScore(memory, METADATA_IMPORTANCE_SCORE, METADATA_IMPORTANCE)
                >= REFINER_STICKY_ANCHOR_IMPORTANCE_THRESHOLD
                || memoryMetadataScore(memory, METADATA_CONFIDENCE_LEVEL, METADATA_CONFIDENCE)
                >= REFINER_STICKY_ANCHOR_CONFIDENCE_THRESHOLD;
    }

    private double memoryMetadataScore(MemoryRefinementMemory memory, String primaryKey, String fallbackKey) {
        double primary = doubleMetadata(memory.metadata(), primaryKey);
        return primary > 0D ? primary : doubleMetadata(memory.metadata(), fallbackKey);
    }

    private MemoryRefinementContextZones refinementContextZones(String sanitizedContent) {
        if (isBlank(sanitizedContent)) {
            return new MemoryRefinementContextZones("", "");
        }
        List<ContextBlockTurn> turns = contextBlockTurns(sanitizedContent);
        if (turns.isEmpty()) {
            return new MemoryRefinementContextZones("", sanitizedContent);
        }
        int targetStart = Math.max(0, turns.size() - REFINER_TARGET_ZONE_TURN_COUNT);
        List<ContextBlockTurn> targetTurns = turns.subList(targetStart, turns.size());
        return new MemoryRefinementContextZones(
                joinContextTurnBlocks(turns.subList(0, targetStart)),
                joinContextTurnBlocks(targetTurns),
                sourceMessageIdsFromSpans(targetTurns));
    }

    private List<ContextBlockTurn> contextBlockTurns(String content) {
        String normalized = Objects.requireNonNullElse(content, "").replace("\r\n", "\n").replace('\r', '\n');
        int turnsStart = normalized.indexOf("[turns]");
        if (turnsStart < 0) {
            return List.of();
        }
        int bodyStart = turnsStart + "[turns]".length();
        int sourceSpansStart = normalized.indexOf("[source_spans]", bodyStart);
        String turnsBody = sourceSpansStart > bodyStart
                ? normalized.substring(bodyStart, sourceSpansStart)
                : normalized.substring(bodyStart);
        Map<Integer, String> sourceSpans = sourceSpansStart > bodyStart
                ? splitContextSourceSpans(normalized.substring(sourceSpansStart + "[source_spans]".length()))
                : Map.of();
        return splitContextTurns(turnsBody, sourceSpans);
    }

    private List<ContextBlockTurn> splitContextTurns(String turnsBody, Map<Integer, String> sourceSpans) {
        List<ContextBlockTurn> turns = new ArrayList<>();
        Matcher matcher = CONTEXT_TURN_HEADER_PATTERN.matcher(Objects.requireNonNullElse(turnsBody, ""));
        int currentStart = -1;
        while (matcher.find()) {
            if (currentStart >= 0) {
                String block = turnsBody.substring(currentStart, matcher.start()).trim();
                if (!block.isBlank()) {
                    turns.add(contextBlockTurn(block, sourceSpans));
                }
            }
            currentStart = matcher.start();
        }
        if (currentStart >= 0) {
            String block = turnsBody.substring(currentStart).trim();
            if (!block.isBlank()) {
                turns.add(contextBlockTurn(block, sourceSpans));
            }
        }
        return List.copyOf(turns);
    }

    private Map<Integer, String> splitContextSourceSpans(String spansBody) {
        Map<Integer, String> spans = new LinkedHashMap<>();
        Matcher matcher = CONTEXT_SOURCE_SPAN_PATTERN.matcher(Objects.requireNonNullElse(spansBody, ""));
        while (matcher.find()) {
            int index = parsePositiveInt(matcher.group(1));
            if (index > 0) {
                spans.put(index, ("span_" + index + ": " + matcher.group(2).trim()).trim());
            }
        }
        return Map.copyOf(spans);
    }

    private ContextBlockTurn contextBlockTurn(String block, Map<Integer, String> sourceSpans) {
        int index = contextTurnIndex(block);
        return new ContextBlockTurn(
                index,
                block,
                index <= 0 ? "" : Objects.requireNonNullElse(sourceSpans.get(index), ""));
    }

    private int contextTurnIndex(String block) {
        Matcher matcher = CONTEXT_TURN_INDEX_PATTERN.matcher(Objects.requireNonNullElse(block, ""));
        if (!matcher.find()) {
            return 0;
        }
        return parsePositiveInt(matcher.group(1));
    }

    private int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String joinContextTurnBlocks(List<ContextBlockTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        String turnBlocks = String.join("\n\n", turns.stream()
                .map(ContextBlockTurn::turnBlock)
                .toList());
        List<String> sourceSpans = turns.stream()
                .map(ContextBlockTurn::sourceSpan)
                .filter(span -> !span.isBlank())
                .toList();
        if (sourceSpans.isEmpty()) {
            return turnBlocks;
        }
        return turnBlocks + "\n\n[source_spans]\n" + String.join("\n", sourceSpans);
    }

    private List<String> sourceMessageIdsFromSpans(List<ContextBlockTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        return turns.stream()
                .map(ContextBlockTurn::sourceSpan)
                .map(this::sourceMessageIdFromSpan)
                .filter(messageId -> !isBlank(messageId))
                .distinct()
                .toList();
    }

    private String sourceMessageIdFromSpan(String sourceSpan) {
        if (isBlank(sourceSpan)) {
            return "";
        }
        String span = sourceSpan.trim();
        int separatorIndex = span.indexOf(':');
        String body = separatorIndex >= 0 ? span.substring(separatorIndex + 1) : span;
        int assistantSeparatorIndex = body.indexOf("->");
        String userMessageId = assistantSeparatorIndex >= 0 ? body.substring(0, assistantSeparatorIndex) : body;
        return userMessageId.trim();
    }

    private MemoryClassificationResult applyRefinementResult(MemoryRefinementResult result,
                                                             MemoryClassificationResult baseline,
                                                             MemoryRefinementContextZones contextZones) {
        if (result == null || !result.refined() || result.operations().isEmpty()) {
            return withRefinerDelta(
                    baseline,
                    MemoryIngestionAction.IGNORE,
                    "",
                    "",
                    result == null ? "empty_result" : result.reason(),
                    Map.of("status", "empty"));
        }
        RefinedMemoryOperation operation = firstSupportedOperation(result.operations());
        if (operation == null) {
            return withRefinerDelta(
                    baseline,
                    MemoryIngestionAction.IGNORE,
                    "",
                    "",
                    "unsupported_refined_operation",
                    Map.of("status", "unsupported"));
        }
        List<MemoryClassificationResult> classifications = supportedRefinedClassifications(result, contextZones);
        if (classifications.isEmpty()) {
            return withRefinerDelta(
                    baseline,
                    MemoryIngestionAction.IGNORE,
                    "",
                    "",
                    "unsupported_refined_operation",
                    Map.of("status", "unsupported"));
        }
        MemoryClassificationResult circuitBreaker = circuitBreakUnsafeBatch(result, classifications);
        if (circuitBreaker != null) {
            return circuitBreaker;
        }
        if (classifications.size() == 1) {
            return classifications.get(0);
        }
        return withRefinerDelta(
                baseline,
                classifications.get(0).refinedDelta().action(),
                classifications.get(0).refinedDelta().targetKind(),
                classifications.get(0).refinedDelta().targetKey(),
                result.reason(),
                Map.of(
                        "status", "batch",
                        METADATA_REFINER_BATCH, classifications,
                        METADATA_REFINER_OPERATION_COUNT, classifications.size()));
    }

    private MemoryClassificationResult circuitBreakUnsafeBatch(MemoryRefinementResult result,
                                                               List<MemoryClassificationResult> classifications) {
        int operationCount = classifications.size();
        if (operationCount > options.maxRefinerBatchOperations()) {
            return circuitBrokenBatchClassification(
                    result,
                    classifications,
                    REFINER_BATCH_CIRCUIT_OPERATION_COUNT,
                    REFINER_BATCH_REASON_OPERATION_COUNT_EXCEEDED);
        }
        double deleteRatio = refinerBatchDeleteRatio(classifications);
        if (operationCount > 1 && deleteRatio > options.maxRefinerDeleteRatio()) {
            return circuitBrokenBatchClassification(
                    result,
                    classifications,
                    REFINER_BATCH_CIRCUIT_DELETE_RATIO,
                    REFINER_BATCH_REASON_DELETE_RATIO_EXCEEDED);
        }
        return null;
    }

    private MemoryClassificationResult circuitBrokenBatchClassification(MemoryRefinementResult result,
                                                                        List<MemoryClassificationResult> classifications,
                                                                        String circuitType,
                                                                        String circuitReason) {
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
        metadata.put("status", REFINER_STATUS_CIRCUIT_BREAKER);
        metadata.put(METADATA_REFINER_BATCH_OPERATION_COUNT, classifications.size());
        metadata.put(METADATA_REFINER_BATCH_DELETE_RATIO, refinerBatchDeleteRatio(classifications));
        metadata.put(METADATA_REFINER_BATCH_CIRCUIT_TYPE, circuitType);
        metadata.put(METADATA_REFINER_BATCH_CIRCUIT_REASON, circuitReason);
        metadata.put("maxRefinerBatchOperations", options.maxRefinerBatchOperations());
        metadata.put("maxRefinerDeleteRatio", options.maxRefinerDeleteRatio());
        metadata.put(METADATA_REFINER_BATCH_OPERATIONS, refinerBatchOperationSummaries(classifications));
        return new MemoryClassificationResult(
                MemoryIngestionAction.REVIEW,
                null,
                null,
                new RefinedMemoryDelta(
                        MemoryIngestionAction.REVIEW,
                        REFINER_BATCH_TARGET_KIND,
                        "",
                        REFINER_BATCH_CIRCUIT_BREAKER_REASON,
                        metadata),
                REFINER_BATCH_CIRCUIT_BREAKER_REASON);
    }

    private double refinerBatchDeleteRatio(List<MemoryClassificationResult> classifications) {
        if (classifications == null || classifications.isEmpty()) {
            return 0D;
        }
        int deleteCount = 0;
        for (MemoryClassificationResult classification : classifications) {
            RefinedMemoryDelta delta = classification.refinedDelta();
            if (delta != null && delta.action() == MemoryIngestionAction.DELETE) {
                deleteCount++;
            }
        }
        return (double) deleteCount / classifications.size();
    }

    private List<Map<String, Object>> refinerBatchOperationSummaries(List<MemoryClassificationResult> classifications) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (MemoryClassificationResult classification : classifications) {
            RefinedMemoryDelta delta = classification.refinedDelta();
            if (delta == null) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("action", delta.action().name());
            summary.put("targetKind", delta.targetKind());
            summary.put("targetKey", delta.targetKey());
            summary.put("content", refinerOperationContent(classification));
            summaries.add(summary);
        }
        return summaries;
    }

    private String refinerOperationContent(MemoryClassificationResult classification) {
        if (classification.decision() != null) {
            return classification.decision().content();
        }
        RefinedMemoryDelta delta = classification.refinedDelta();
        if (delta == null) {
            return "";
        }
        return stringMetadata(delta.metadata(), METADATA_CONTENT, "");
    }

    private RefinedMemoryOperation firstSupportedOperation(List<RefinedMemoryOperation> operations) {
        for (RefinedMemoryOperation operation : operations) {
            if (operation != null
                    && (operation.action() == MemoryIngestionAction.ADD
                    || requiresReviewStaging(operation.action()))) {
                return operation;
            }
        }
        return null;
    }

    private List<MemoryClassificationResult> supportedRefinedClassifications(MemoryRefinementResult result,
                                                                             MemoryRefinementContextZones contextZones) {
        List<MemoryClassificationResult> classifications = new ArrayList<>();
        List<RefinedMemoryOperation> operations = result == null ? List.of() : result.operations();
        int supportedIndex = 0;
        int supportedCount = supportedOperationCount(operations);
        for (RefinedMemoryOperation operation : operations) {
            if (operation == null
                    || operation.action() != MemoryIngestionAction.ADD && !requiresReviewStaging(operation.action())) {
                continue;
            }
            Map<String, Object> batchMetadata = Map.of(
                    METADATA_REFINER_OPERATION_INDEX, supportedIndex,
                    METADATA_REFINER_OPERATION_COUNT, supportedCount);
            MemoryClassificationResult classification = operation.action() == MemoryIngestionAction.ADD
                    ? refinedAddClassification(operation, result, batchMetadata, contextZones.targetSourceMessageIds())
                    : refinedReviewClassification(operation, result, batchMetadata, contextZones.targetSourceMessageIds());
            classifications.add(classification);
            supportedIndex++;
        }
        return classifications;
    }

    private int supportedOperationCount(List<RefinedMemoryOperation> operations) {
        int count = 0;
        for (RefinedMemoryOperation operation : operations) {
            if (operation != null
                    && (operation.action() == MemoryIngestionAction.ADD
                    || requiresReviewStaging(operation.action()))) {
                count++;
            }
        }
        return count;
    }

    private MemoryClassificationResult refinedAddClassification(RefinedMemoryOperation operation,
                                                                MemoryRefinementResult result,
                                                                Map<String, Object> extraMetadata,
                                                                List<String> fallbackSourceMessageIds) {
        MemoryCaptureDecision decision = MemoryCaptureDecision.refinedAdd(
                operation.content(),
                isBlank(operation.targetKind()) ? "FACT" : operation.targetKind(),
                operation.importance(),
                operation.confidence(),
                operation.valueScore(),
                operation.riskScore(),
                List.of("llm_refiner"),
                operation.signals());
        Map<String, Object> metadata = new LinkedHashMap<>(operation.metadata());
        metadata.putAll(result.metadata());
        metadata.put("status", "enabled");
        metadata.put(METADATA_SOURCE_MESSAGE_IDS, effectiveSourceMessageIds(operation, fallbackSourceMessageIds));
        metadata.putAll(extraMetadata);
        return MemoryClassificationResult.refinedAdd(decision, new RefinedMemoryDelta(
                operation.action(),
                operation.targetKind(),
                operation.targetKey(),
                result.reason(),
                metadata));
    }

    private MemoryClassificationResult refinedReviewClassification(RefinedMemoryOperation operation,
                                                                   MemoryRefinementResult result) {
        return refinedReviewClassification(operation, result, Map.of());
    }

    private MemoryClassificationResult refinedReviewClassification(RefinedMemoryOperation operation,
                                                                   MemoryRefinementResult result,
                                                                   Map<String, Object> extraMetadata) {
        return refinedReviewClassification(operation, result, extraMetadata, List.of());
    }

    private MemoryClassificationResult refinedReviewClassification(RefinedMemoryOperation operation,
                                                                   MemoryRefinementResult result,
                                                                   Map<String, Object> extraMetadata,
                                                                   List<String> fallbackSourceMessageIds) {
        Map<String, Object> metadata = new LinkedHashMap<>(operation.metadata());
        metadata.putAll(result.metadata());
        metadata.put("status", "pending_review");
        metadata.put(METADATA_CONTENT, operation.content());
        metadata.put(METADATA_CONFIDENCE, operation.confidence());
        metadata.put(METADATA_IMPORTANCE, operation.importance());
        metadata.put(METADATA_VALUE_SCORE, operation.valueScore());
        metadata.put(METADATA_RISK_SCORE, operation.riskScore());
        metadata.put(METADATA_SOURCE_MESSAGE_IDS, effectiveSourceMessageIds(operation, fallbackSourceMessageIds));
        metadata.putAll(extraMetadata);
        return new MemoryClassificationResult(
                MemoryIngestionAction.REVIEW,
                null,
                null,
                new RefinedMemoryDelta(
                        operation.action(),
                        operation.targetKind(),
                        operation.targetKey(),
                        result.reason(),
                        metadata),
                result.reason());
    }

    private List<String> effectiveSourceMessageIds(RefinedMemoryOperation operation, List<String> fallbackSourceMessageIds) {
        List<String> operationSourceMessageIds = operation == null ? List.of() : operation.sourceMessageIds();
        List<String> sanitizedOperationSourceMessageIds = nonBlankDistinct(operationSourceMessageIds);
        if (!sanitizedOperationSourceMessageIds.isEmpty()) {
            return sanitizedOperationSourceMessageIds;
        }
        return nonBlankDistinct(fallbackSourceMessageIds);
    }

    private List<String> nonBlankDistinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> !isBlank(value))
                .distinct()
                .toList();
    }

    private boolean requiresReviewStaging(MemoryIngestionAction action) {
        return action == MemoryIngestionAction.REVIEW
                || action == MemoryIngestionAction.UPDATE
                || action == MemoryIngestionAction.DELETE;
    }

    private MemoryIngestionAction resultAction(MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta != null && delta.metadata().containsKey(METADATA_REVIEW_REQUESTED_ACTION)
                && delta.action() == MemoryIngestionAction.UPDATE) {
            return MemoryIngestionAction.UPDATE;
        }
        return MemoryIngestionAction.ADD;
    }

    private MemoryClassificationResult withRefinerDelta(MemoryClassificationResult baseline,
                                                        MemoryIngestionAction action,
                                                        String targetKind,
                                                        String targetKey,
                                                        String reason,
                                                        Map<String, Object> metadata) {
        if (baseline == null) {
            return new MemoryClassificationResult(
                    MemoryIngestionAction.IGNORE,
                    null,
                    null,
                    new RefinedMemoryDelta(action, targetKind, targetKey, reason, metadata),
                    reason);
        }
        return new MemoryClassificationResult(
                baseline.action(),
                baseline.decision(),
                baseline.correction(),
                new RefinedMemoryDelta(action, targetKind, targetKey, reason, metadata),
                baseline.reason());
    }

    private IngestionExecution executeReviewStaging(String operationId,
                                                    String tenantId,
                                                    MemoryWriteRequest request,
                                                    MemoryClassificationResult classification) {
        MemoryPolicyConfig policy = memoryPolicyConfigPort.current();
        if (!policy.reviewEnabled()) {
            return new IngestionExecution(MemoryIngestionResult.ignored("review_disabled"), classification);
        }
        RefinedMemoryDelta delta = classification.refinedDelta();
        Map<String, Object> metadata = delta == null ? Map.of() : delta.metadata();
        MemoryReviewCandidate candidate = new MemoryReviewCandidate(
                REVIEW_CANDIDATE_PREFIX + operationId,
                operationId,
                tenantId,
                request.userId(),
                request.conversationId(),
                request.messageId(),
                delta == null ? MemoryIngestionAction.REVIEW : delta.action(),
                stringMetadata(metadata, METADATA_TARGET_LAYER, REVIEW_DEFAULT_LAYER),
                delta == null ? "" : delta.targetKind(),
                delta == null ? "" : delta.targetKey(),
                stringMetadata(metadata, METADATA_CONTENT, request.message().getContent()),
                doubleMetadata(metadata, METADATA_CONFIDENCE),
                doubleMetadata(metadata, METADATA_IMPORTANCE),
                doubleMetadata(metadata, METADATA_VALUE_SCORE),
                doubleMetadata(metadata, METADATA_RISK_SCORE),
                classification.reason(),
                sourceMessageIds(metadata, request.messageId()),
                metadata,
                Instant.now());
        memoryReviewCandidatePort.save(candidate);
        return new IngestionExecution(MemoryIngestionResult.review(classification.reason()), classification);
    }

    private String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return value.toString().trim();
    }

    private double doubleMetadata(Map<String, Object> metadata, String key) {
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

    private List<String> sourceMessageIds(Map<String, Object> metadata, String fallbackMessageId) {
        Object value = metadata.get(METADATA_SOURCE_MESSAGE_IDS);
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (!isBlank(fallbackMessageId)) {
            return List.of(fallbackMessageId);
        }
        return List.of();
    }

    private String baselineMemoryType(MemoryClassificationResult classification) {
        if (classification == null || classification.decision() == null) {
            return "";
        }
        return classification.decision().type();
    }

    private Map<String, Object> baselineDetails(MemoryClassificationResult classification) {
        if (classification == null) {
            return Map.of();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action", classification.action().name());
        details.put("reason", classification.reason());
        if (classification.decision() != null) {
            details.put("memoryType", classification.decision().type());
            details.put("valueScore", classification.decision().valueScore());
            details.put("riskScore", classification.decision().riskScore());
            details.put("signals", classification.decision().signals());
            details.put("reasons", classification.decision().reasons());
        }
        return details;
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

    private String profileSlot(MemoryCaptureDecision decision, MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta != null
                && "PROFILE_SLOT".equalsIgnoreCase(delta.targetKind())
                && !isBlank(delta.targetKey())) {
            return delta.targetKey();
        }
        return decision == null ? "" : profileSlotResolver.resolve(decision.type(), decision.content(), "");
    }

    private String profileValue(String slotKey, String content) {
        String value = Objects.requireNonNullElse(content, "").trim();
        if ("identity.name".equals(slotKey)) {
            value = stripPrefix(value, "(?i)^my name is\\s+");
            value = stripPrefix(value, "^\u6211\u53eb");
            value = stripPrefix(value, "^\u6211\u7684\u540d\u5b57\u662f");
            return stripPrefix(value, "^\u6211\u7684\u6635\u79f0\u662f");
        }
        if ("skills.tech_stack".equals(slotKey)) {
            value = stripPrefix(value, "(?i)^my tech stack is\\s+");
            value = stripPrefix(value, "^\u6211\u7684\u6280\u672f\u6808\u662f\\s*");
            return stripPrefix(value, "^\u6211\u4e3b\u8981\u4f7f\u7528\\s*");
        }
        if ("preferences.response_style".equals(slotKey)) {
            value = stripPrefix(value, "(?i)^i prefer\\s+");
            value = stripPrefix(value, "(?i)^i like\\s+");
            value = stripPrefix(value, "^\u6211\u559c\u6b22");
            value = stripPrefix(value, "^\u6211\u504f\u597d");
            return value;
        }
        if ("identity.occupation".equals(slotKey)) {
            return normalizeOccupationValue(stripProfilePrefix(value));
        }
        return "";
    }

    private String stripPrefix(String content, String regex) {
        return Objects.requireNonNullElse(content, "").replaceFirst(regex, "").trim();
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

    private void addRefinedMetadata(Map<String, Object> metadata, MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta == null || delta.action() == MemoryIngestionAction.IGNORE && isBlank(delta.reason())) {
            return;
        }
        metadata.put("refinerStatus", refinerStatus(delta));
        metadata.put("refinerAction", delta.action().name());
        metadata.put("refinerReason", delta.reason());
        metadata.put("targetKind", delta.targetKind());
        metadata.put("targetKey", delta.targetKey());
        for (Map.Entry<String, Object> entry : delta.metadata().entrySet()) {
            if (METADATA_REFINER_BATCH.equals(entry.getKey())) {
                continue;
            }
            metadata.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (classification != null && classification.decision() != null
                && "llm_refiner_v1".equals(classification.decision().policyVersion())) {
            metadata.put("capturePolicy", "llm_refiner");
        }
    }

    private String refinerStatus(RefinedMemoryDelta delta) {
        Object status = delta.metadata().get("status");
        if (status != null && !status.toString().isBlank()) {
            return status.toString();
        }
        if (delta.action() == MemoryIngestionAction.ADD) {
            return "enabled";
        }
        return delta.reason().startsWith("failed_open") ? "failed_open" : "ignored";
    }

    private String memoryId(MemoryWriteRequest request, MemoryClassificationResult classification) {
        String suffix = refinerOperationSuffix(classification);
        if (!isBlank(request.messageId())) {
            return "stm-" + request.messageId() + suffix;
        }
        return "stm-" + UUID.randomUUID() + suffix;
    }

    private String refinerOperationSuffix(MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta == null) {
            return "";
        }
        Object count = delta.metadata().get(METADATA_REFINER_OPERATION_COUNT);
        if (!(count instanceof Number number) || number.intValue() <= 1) {
            return "";
        }
        Object index = delta.metadata().get(METADATA_REFINER_OPERATION_INDEX);
        if (!(index instanceof Number indexNumber)) {
            return "";
        }
        return "-r" + indexNumber.intValue();
    }

    private MemoryOperation buildOperation(String operationId,
                                           String tenantId,
                                           MemoryIngestionCommand command,
                                           MemoryWriteRequest request,
                                           String content) {
        MemoryReviewApplyDirective directive = command == null ? null : command.reviewApplyDirective();
        MemoryOperationType operationType = inferOperationType(directive, content);
        return new MemoryOperation(
                operationId,
                request.userId(),
                tenantId,
                operationType,
                inferTargetKind(operationType, directive, content),
                inferTargetKey(operationType, directive, content),
                requestMap(command, request, content),
                MemoryValueAssessor.POLICY_VERSION,
                Instant.now());
    }

    private MemoryOperationType inferOperationType(MemoryReviewApplyDirective directive, String content) {
        if (directive != null) {
            return switch (directive.requestedAction()) {
                case ADD -> MemoryOperationType.ADD;
                case UPDATE -> MemoryOperationType.UPDATE;
                case DELETE -> MemoryOperationType.DELETE;
                case REVIEW -> MemoryOperationType.REVIEW;
                case IGNORE -> MemoryOperationType.IGNORE;
            };
        }
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

    private String inferTargetKind(MemoryOperationType operationType,
                                   MemoryReviewApplyDirective directive,
                                   String content) {
        if (directive != null && !isBlank(directive.targetKind())) {
            return directive.targetKind();
        }
        if (operationType == MemoryOperationType.UPDATE || OccupationCorrection.extract(content) != null) {
            return "PROFILE_SLOT";
        }
        if (operationType == MemoryOperationType.ADD) {
            return "SHORT_TERM_MEMORY";
        }
        return "NONE";
    }

    private String inferTargetKey(MemoryOperationType operationType,
                                  MemoryReviewApplyDirective directive,
                                  String content) {
        if (directive != null && !isBlank(directive.targetKey())) {
            return directive.targetKey();
        }
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

    private boolean isReviewDeleteApply(MemoryIngestionCommand command) {
        MemoryReviewApplyDirective directive = command == null ? null : command.reviewApplyDirective();
        return directive != null && directive.requestedAction() == MemoryIngestionAction.DELETE;
    }

    private String targetMemoryId(MemoryReviewApplyDirective directive) {
        if (directive == null) {
            return "";
        }
        Object metadataTarget = directive.metadata().get(METADATA_TARGET_MEMORY_ID);
        if (metadataTarget != null && !metadataTarget.toString().isBlank()) {
            return metadataTarget.toString().trim();
        }
        return directive.targetKey();
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

    private Map<String, Object> decisionMap(MemoryIngestionResult result, MemoryClassificationResult classification) {
        Map<String, Object> values = new LinkedHashMap<>(decisionMap(result));
        addRefinedMetadata(values, classification);
        return values;
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

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
