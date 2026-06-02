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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPolicyDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    private static final String TARGET_KIND_PROFILE_SLOT = "PROFILE_SLOT";
    private static final String TARGET_KEY_IDENTITY_OCCUPATION = "identity.occupation";
    private static final String REFINER_ADD_LOW_CONFIDENCE = MemoryReviewPolicyPort.REFINER_ADD_LOW_CONFIDENCE;
    private static final String REFINER_ADD_REVIEW_CONFIDENCE = MemoryReviewPolicyPort.REFINER_ADD_REVIEW_CONFIDENCE;
    private static final String REFINER_ADD_REVIEW_RISK = MemoryReviewPolicyPort.REFINER_ADD_REVIEW_RISK;
    private static final String REFINER_STATUS_DROPPED = "dropped";
    private static final String REFINER_STATUS_PENDING_REVIEW = "pending_review";

    private record IngestionExecution(MemoryIngestionResult result, MemoryClassificationResult classification) {

        private IngestionExecution {
            Objects.requireNonNull(result, "result must not be null");
        }
    }

    private final MemoryLayerStoreRegistry stores;
    private final ProfileMemoryPort profileMemoryPort;
    private final CorrectionLedgerPort correctionLedgerPort;
    private final MemoryRouterPort memoryRouterPort;
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
    private final MemoryAliasPort memoryAliasPort;
    private final MemoryReviewPolicyPort memoryReviewPolicyPort;
    private final MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort;
    private final MemorySanitizer memorySanitizer;
    private final MemoryPreFilter memoryPreFilter;
    private final MemorySemanticClassifier memorySemanticClassifier;
    private final MemorySchemaValidator memorySchemaValidator;
    private final ProfileSlotResolver profileSlotResolver;
    private final MemoryDerivedIndexDispatchService derivedIndexDispatch;
    private final MemoryTrackWriteService trackWriteService;
    private final MemoryRefinementContextParser refinementContextParser;
    private final MemoryRefinerBatchCircuitBreaker refinerBatchCircuitBreaker;
    private final MemoryProfileValueNormalizer profileValueNormalizer;
    private final MemoryRefinementInputBuilder refinementInputBuilder;
    private final MemoryCanonicalAliasResolver canonicalAliasResolver;
    private final MemoryRefinerMetadataWriter refinerMetadataWriter;
    private final MemoryOperationBuilder operationBuilder;
    private final MemoryOperationGateway operationGateway;
    private final MemoryRefinerFeedbackLookup refinerFeedbackLookup;
    private final MemoryReviewApplyClassificationBuilder reviewApplyClassificationBuilder;
    private final MemoryRefinementDepthGuard refinementDepthGuard;

    public static Builder builder(ShortTermMemoryPort shortTermPort,
                                  LongTermMemoryPort longTermPort,
                                  SemanticMemoryPort semanticPort,
                                  ObjectMapper objectMapper) {
        return new Builder(shortTermPort, longTermPort, semanticPort, objectMapper);
    }

    public static DefaultMemoryEnginePort builder(ShortTermMemoryPort shortTermPort,
                                                  LongTermMemoryPort longTermPort,
                                                  SemanticMemoryPort semanticPort,
                                                  ObjectMapper objectMapper,
                                                  MemoryEngineOptions options) {
        return builder(shortTermPort, longTermPort, semanticPort, objectMapper)
                .options(options)
                .build();
    }

    public static DefaultMemoryEnginePort builder(ShortTermMemoryPort shortTermPort,
                                                  LongTermMemoryPort longTermPort,
                                                  SemanticMemoryPort semanticPort,
                                                  ObjectMapper objectMapper,
                                                  MemoryEngineOptions options,
                                                  ProfileMemoryPort profileMemoryPort,
                                                  CorrectionLedgerPort correctionLedgerPort) {
        return builder(shortTermPort, longTermPort, semanticPort, objectMapper)
                .options(options)
                .profileMemoryPort(profileMemoryPort)
                .correctionLedgerPort(correctionLedgerPort)
                .build();
    }

    public static DefaultMemoryEnginePort builder(ShortTermMemoryPort shortTermPort,
                                                  LongTermMemoryPort longTermPort,
                                                  SemanticMemoryPort semanticPort,
                                                  ObjectMapper objectMapper,
                                                  MemoryEngineOptions options,
                                                  ProfileMemoryPort profileMemoryPort,
                                                  CorrectionLedgerPort correctionLedgerPort,
                                                  MemoryRouterPort memoryRouterPort,
                                                  MemoryOperationLogPort memoryOperationLogPort) {
        return builder(shortTermPort, longTermPort, semanticPort, objectMapper)
                .options(options)
                .profileMemoryPort(profileMemoryPort)
                .correctionLedgerPort(correctionLedgerPort)
                .memoryRouterPort(memoryRouterPort)
                .memoryOperationLogPort(memoryOperationLogPort)
                .build();
    }

    public static DefaultMemoryEnginePort builder(ShortTermMemoryPort shortTermPort,
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
        return builder(shortTermPort, longTermPort, semanticPort, objectMapper)
                .options(options)
                .profileMemoryPort(profileMemoryPort)
                .correctionLedgerPort(correctionLedgerPort)
                .memoryRouterPort(memoryRouterPort)
                .memoryOperationLogPort(memoryOperationLogPort)
                .memoryVectorPort(memoryVectorPort)
                .memoryOutboxPort(memoryOutboxPort)
                .businessDocumentRetrieverPort(businessDocumentRetrieverPort)
                .build();
    }

    public static DefaultMemoryEnginePort builder(ShortTermMemoryPort shortTermPort,
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
        return builder(shortTermPort, longTermPort, semanticPort, objectMapper)
                .options(options)
                .profileMemoryPort(profileMemoryPort)
                .correctionLedgerPort(correctionLedgerPort)
                .memoryRouterPort(memoryRouterPort)
                .memoryOperationLogPort(memoryOperationLogPort)
                .memoryVectorPort(memoryVectorPort)
                .memoryOutboxPort(memoryOutboxPort)
                .businessDocumentRetrieverPort(businessDocumentRetrieverPort)
                .memoryLifecyclePort(memoryLifecyclePort)
                .memoryPolicyConfigPort(memoryPolicyConfigPort)
                .memoryRetrievalPipelinePort(memoryRetrievalPipelinePort)
                .memoryRefinerPort(memoryRefinerPort)
                .build();
    }

    public static DefaultMemoryEnginePort builder(ShortTermMemoryPort shortTermPort,
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
                                                  MemoryReviewCandidatePort memoryReviewCandidatePort,
                                                  MemoryAliasPort memoryAliasPort,
                                                  MemoryReviewPolicyPort memoryReviewPolicyPort) {
        return builder(shortTermPort, longTermPort, semanticPort, objectMapper)
                .options(options)
                .profileMemoryPort(profileMemoryPort)
                .correctionLedgerPort(correctionLedgerPort)
                .memoryRouterPort(memoryRouterPort)
                .memoryOperationLogPort(memoryOperationLogPort)
                .memoryVectorPort(memoryVectorPort)
                .memoryOutboxPort(memoryOutboxPort)
                .businessDocumentRetrieverPort(businessDocumentRetrieverPort)
                .memoryLifecyclePort(memoryLifecyclePort)
                .memoryPolicyConfigPort(memoryPolicyConfigPort)
                .memoryRetrievalPipelinePort(memoryRetrievalPipelinePort)
                .memoryRefinerPort(memoryRefinerPort)
                .memoryReviewCandidatePort(memoryReviewCandidatePort)
                .memoryAliasPort(memoryAliasPort)
                .memoryReviewPolicyPort(memoryReviewPolicyPort)
                .build();
    }

    public static DefaultMemoryEnginePort builder(ShortTermMemoryPort shortTermPort,
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
                                                  MemoryReviewCandidatePort memoryReviewCandidatePort,
                                                  MemoryAliasPort memoryAliasPort,
                                                  MemoryReviewPolicyPort memoryReviewPolicyPort,
                                                  MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort) {
        return builder(shortTermPort, longTermPort, semanticPort, objectMapper)
                .options(options)
                .profileMemoryPort(profileMemoryPort)
                .correctionLedgerPort(correctionLedgerPort)
                .memoryRouterPort(memoryRouterPort)
                .memoryOperationLogPort(memoryOperationLogPort)
                .memoryVectorPort(memoryVectorPort)
                .memoryOutboxPort(memoryOutboxPort)
                .businessDocumentRetrieverPort(businessDocumentRetrieverPort)
                .memoryLifecyclePort(memoryLifecyclePort)
                .memoryPolicyConfigPort(memoryPolicyConfigPort)
                .memoryRetrievalPipelinePort(memoryRetrievalPipelinePort)
                .memoryRefinerPort(memoryRefinerPort)
                .memoryReviewCandidatePort(memoryReviewCandidatePort)
                .memoryAliasPort(memoryAliasPort)
                .memoryReviewPolicyPort(memoryReviewPolicyPort)
                .memoryReviewFeedbackRepositoryPort(memoryReviewFeedbackRepositoryPort)
                .build();
    }

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
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort, memoryOutboxPort,
                businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort,
                memoryRetrievalPipelinePort, memoryRefinerPort, memoryReviewCandidatePort, MemoryAliasPort.noop());
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
                                   MemoryReviewCandidatePort memoryReviewCandidatePort,
                                   MemoryAliasPort memoryAliasPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort, memoryOutboxPort,
                businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort,
                memoryRetrievalPipelinePort, memoryRefinerPort, memoryReviewCandidatePort, memoryAliasPort,
                MemoryReviewPolicyPort.defaults());
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
                                   MemoryReviewCandidatePort memoryReviewCandidatePort,
                                   MemoryAliasPort memoryAliasPort,
                                   MemoryReviewPolicyPort memoryReviewPolicyPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort, memoryOutboxPort,
                businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort,
                memoryRetrievalPipelinePort, memoryRefinerPort, memoryReviewCandidatePort, memoryAliasPort,
                memoryReviewPolicyPort, MemoryReviewFeedbackRepositoryPort.empty());
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
                                   MemoryReviewCandidatePort memoryReviewCandidatePort,
                                   MemoryAliasPort memoryAliasPort,
                                   MemoryReviewPolicyPort memoryReviewPolicyPort,
                                   MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort) {
        this(shortTermPort, longTermPort, semanticPort, objectMapper, options, profileMemoryPort,
                correctionLedgerPort, memoryRouterPort, memoryOperationLogPort, memoryVectorPort, memoryOutboxPort,
                businessDocumentRetrieverPort, memoryLifecyclePort, memoryPolicyConfigPort,
                memoryRetrievalPipelinePort, memoryRefinerPort, memoryReviewCandidatePort, memoryAliasPort,
                memoryReviewPolicyPort, memoryReviewFeedbackRepositoryPort, MemoryCaptureRules.defaults());
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
                                   MemoryReviewCandidatePort memoryReviewCandidatePort,
                                   MemoryAliasPort memoryAliasPort,
                                   MemoryReviewPolicyPort memoryReviewPolicyPort,
                                   MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort,
                                   MemoryCaptureRules captureRules) {
        this.stores = new MemoryLayerStoreRegistry(shortTermPort, longTermPort, semanticPort);
        this.profileMemoryPort = Objects.requireNonNull(profileMemoryPort, "profileMemoryPort must not be null");
        this.correctionLedgerPort = Objects.requireNonNull(correctionLedgerPort, "correctionLedgerPort must not be null");
        this.memoryRouterPort = Objects.requireNonNull(memoryRouterPort, "memoryRouterPort must not be null");
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
                this.stores.shortTerm(),
                this.stores.longTerm(),
                this.stores.semantic(),
                this.objectMapper,
                this.options,
                this.profileMemoryPort,
                this.correctionLedgerPort,
                this.memoryRouterPort,
                this.memoryVectorPort,
                this.businessDocumentRetrieverPort,
                this.memoryLifecyclePort)
                : memoryRetrievalPipelinePort;
        this.captureCandidateExtractor = new MemoryCaptureCandidateExtractor(
                Objects.requireNonNullElseGet(captureRules, MemoryCaptureRules::defaults));
        this.memoryValueAssessor = new MemoryValueAssessor(this.memoryPolicyConfigPort);
        this.memoryRefinerPort = Objects.requireNonNullElseGet(memoryRefinerPort, MemoryRefinerPort::noop);
        this.memoryReviewCandidatePort = Objects.requireNonNullElseGet(memoryReviewCandidatePort,
                MemoryReviewCandidatePort::noop);
        this.memoryAliasPort = Objects.requireNonNullElseGet(memoryAliasPort, MemoryAliasPort::noop);
        this.memoryReviewPolicyPort = Objects.requireNonNullElseGet(memoryReviewPolicyPort,
                MemoryReviewPolicyPort::defaults);
        this.memoryReviewFeedbackRepositoryPort = Objects.requireNonNullElseGet(memoryReviewFeedbackRepositoryPort,
                MemoryReviewFeedbackRepositoryPort::empty);
        this.memorySanitizer = new MemorySanitizer();
        this.memoryPreFilter = new MemoryPreFilter();
        this.memorySemanticClassifier = new MemorySemanticClassifier(captureCandidateExtractor, memoryValueAssessor);
        this.memorySchemaValidator = new MemorySchemaValidator(memorySanitizer);
        this.profileSlotResolver = new ProfileSlotResolver();
        this.derivedIndexDispatch = new MemoryDerivedIndexDispatchService(
                this.memoryVectorPort,
                this.memoryOutboxPort,
                this.options.keywordIndexOutboxEnabled(),
                this.options.graphIndexOutboxEnabled(),
                DEFAULT_VECTOR_EMBEDDING_MODEL);
        this.trackWriteService = new MemoryTrackWriteService(
                this.profileMemoryPort,
                this.correctionLedgerPort,
                this.memoryLifecyclePort,
                TARGET_KIND_PROFILE_SLOT,
                TARGET_KEY_IDENTITY_OCCUPATION);
        this.refinementContextParser = new MemoryRefinementContextParser(
                this.options.refinerTargetZoneTurnCount());
        this.refinerBatchCircuitBreaker = new MemoryRefinerBatchCircuitBreaker(
                this.options.maxRefinerBatchOperations(),
                this.options.maxRefinerDeleteRatio());
        this.profileValueNormalizer = new MemoryProfileValueNormalizer(this.profileSlotResolver);
        this.refinementInputBuilder = new MemoryRefinementInputBuilder(
                this.stores.shortTerm(),
                this.stores.longTerm(),
                this.stores.semantic(),
                this.options.refinerReadMaskPerLayerLimit(),
                this.options.refinerStickyAnchorLimit(),
                this.options.refinerStickyAnchorImportanceThreshold(),
                this.options.refinerStickyAnchorConfidenceThreshold());
        this.canonicalAliasResolver = new MemoryCanonicalAliasResolver(this.memoryAliasPort);
        this.refinerMetadataWriter = new MemoryRefinerMetadataWriter();
        this.operationBuilder = new MemoryOperationBuilder(
                this.memorySanitizer,
                this.memoryPreFilter,
                this.memorySemanticClassifier);
        this.operationGateway = new MemoryOperationGateway(
                memoryOperationLogPort,
                new MemoryOperationCompletionWriter(memoryOperationLogPort, this.refinerMetadataWriter));
        this.refinerFeedbackLookup = new MemoryRefinerFeedbackLookup(
                this.memoryReviewFeedbackRepositoryPort,
                this.profileValueNormalizer,
                this.options.refinerFeedbackExampleLimit(),
                TARGET_KIND_PROFILE_SLOT,
                TARGET_KEY_IDENTITY_OCCUPATION);
        this.reviewApplyClassificationBuilder = new MemoryReviewApplyClassificationBuilder();
        this.refinementDepthGuard = new MemoryRefinementDepthGuard(this.options.maxRefinementDepth());
    }

    public static final class Builder {

        private final ShortTermMemoryPort shortTermPort;
        private final LongTermMemoryPort longTermPort;
        private final SemanticMemoryPort semanticPort;
        private final ObjectMapper objectMapper;
        private MemoryEngineOptions options = MemoryEngineOptions.defaults();
        private ProfileMemoryPort profileMemoryPort = ProfileMemoryPort.noop();
        private CorrectionLedgerPort correctionLedgerPort = CorrectionLedgerPort.noop();
        private MemoryRouterPort memoryRouterPort = new DefaultMemoryRouter();
        private MemoryOperationLogPort memoryOperationLogPort = MemoryOperationLogPort.noop();
        private MemoryVectorPort memoryVectorPort = MemoryVectorPort.noop();
        private MemoryOutboxPort memoryOutboxPort = MemoryOutboxPort.noop();
        private MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort =
                MemoryBusinessDocumentRetrieverPort.noop();
        private MemoryLifecyclePort memoryLifecyclePort = MemoryLifecyclePort.noop();
        private MemoryPolicyConfigPort memoryPolicyConfigPort = MemoryPolicyConfigPort.defaults();
        private MemoryRetrievalPipelinePort memoryRetrievalPipelinePort;
        private MemoryRefinerPort memoryRefinerPort = MemoryRefinerPort.noop();
        private MemoryReviewCandidatePort memoryReviewCandidatePort = MemoryReviewCandidatePort.noop();
        private MemoryAliasPort memoryAliasPort = MemoryAliasPort.noop();
        private MemoryReviewPolicyPort memoryReviewPolicyPort = MemoryReviewPolicyPort.defaults();
        private MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort =
                MemoryReviewFeedbackRepositoryPort.empty();
        private MemoryCaptureRules captureRules = MemoryCaptureRules.defaults();

        private Builder(ShortTermMemoryPort shortTermPort,
                        LongTermMemoryPort longTermPort,
                        SemanticMemoryPort semanticPort,
                        ObjectMapper objectMapper) {
            this.shortTermPort = shortTermPort;
            this.longTermPort = longTermPort;
            this.semanticPort = semanticPort;
            this.objectMapper = objectMapper;
        }

        public Builder options(MemoryEngineOptions options) {
            this.options = Objects.requireNonNullElseGet(options, MemoryEngineOptions::defaults);
            return this;
        }

        public Builder profileMemoryPort(ProfileMemoryPort profileMemoryPort) {
            this.profileMemoryPort = Objects.requireNonNullElseGet(profileMemoryPort, ProfileMemoryPort::noop);
            return this;
        }

        public Builder correctionLedgerPort(CorrectionLedgerPort correctionLedgerPort) {
            this.correctionLedgerPort = Objects.requireNonNullElseGet(correctionLedgerPort, CorrectionLedgerPort::noop);
            return this;
        }

        public Builder memoryRouterPort(MemoryRouterPort memoryRouterPort) {
            this.memoryRouterPort = Objects.requireNonNullElseGet(memoryRouterPort, DefaultMemoryRouter::new);
            return this;
        }

        public Builder memoryOperationLogPort(MemoryOperationLogPort memoryOperationLogPort) {
            this.memoryOperationLogPort = Objects.requireNonNullElseGet(memoryOperationLogPort,
                    MemoryOperationLogPort::noop);
            return this;
        }

        public Builder memoryVectorPort(MemoryVectorPort memoryVectorPort) {
            this.memoryVectorPort = Objects.requireNonNullElseGet(memoryVectorPort, MemoryVectorPort::noop);
            return this;
        }

        public Builder memoryOutboxPort(MemoryOutboxPort memoryOutboxPort) {
            this.memoryOutboxPort = Objects.requireNonNullElseGet(memoryOutboxPort, MemoryOutboxPort::noop);
            return this;
        }

        public Builder businessDocumentRetrieverPort(
                MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort) {
            this.businessDocumentRetrieverPort = Objects.requireNonNullElseGet(
                    businessDocumentRetrieverPort,
                    MemoryBusinessDocumentRetrieverPort::noop);
            return this;
        }

        public Builder memoryLifecyclePort(MemoryLifecyclePort memoryLifecyclePort) {
            this.memoryLifecyclePort = Objects.requireNonNullElseGet(memoryLifecyclePort, MemoryLifecyclePort::noop);
            return this;
        }

        public Builder memoryPolicyConfigPort(MemoryPolicyConfigPort memoryPolicyConfigPort) {
            this.memoryPolicyConfigPort = Objects.requireNonNullElseGet(memoryPolicyConfigPort,
                    MemoryPolicyConfigPort::defaults);
            return this;
        }

        public Builder memoryRetrievalPipelinePort(MemoryRetrievalPipelinePort memoryRetrievalPipelinePort) {
            this.memoryRetrievalPipelinePort = memoryRetrievalPipelinePort;
            return this;
        }

        public Builder memoryRefinerPort(MemoryRefinerPort memoryRefinerPort) {
            this.memoryRefinerPort = Objects.requireNonNullElseGet(memoryRefinerPort, MemoryRefinerPort::noop);
            return this;
        }

        public Builder memoryReviewCandidatePort(MemoryReviewCandidatePort memoryReviewCandidatePort) {
            this.memoryReviewCandidatePort = Objects.requireNonNullElseGet(memoryReviewCandidatePort,
                    MemoryReviewCandidatePort::noop);
            return this;
        }

        public Builder memoryAliasPort(MemoryAliasPort memoryAliasPort) {
            this.memoryAliasPort = Objects.requireNonNullElseGet(memoryAliasPort, MemoryAliasPort::noop);
            return this;
        }

        public Builder memoryReviewPolicyPort(MemoryReviewPolicyPort memoryReviewPolicyPort) {
            this.memoryReviewPolicyPort = Objects.requireNonNullElseGet(memoryReviewPolicyPort,
                    MemoryReviewPolicyPort::defaults);
            return this;
        }

        public Builder memoryReviewFeedbackRepositoryPort(
                MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort) {
            this.memoryReviewFeedbackRepositoryPort = Objects.requireNonNullElseGet(
                    memoryReviewFeedbackRepositoryPort,
                    MemoryReviewFeedbackRepositoryPort::empty);
            return this;
        }

        public Builder captureRules(MemoryCaptureRules captureRules) {
            this.captureRules = Objects.requireNonNullElseGet(captureRules, MemoryCaptureRules::defaults);
            return this;
        }

        public DefaultMemoryEnginePort build() {
            return new DefaultMemoryEnginePort(
                    shortTermPort,
                    longTermPort,
                    semanticPort,
                    objectMapper,
                    options,
                    profileMemoryPort,
                    correctionLedgerPort,
                    memoryRouterPort,
                    memoryOperationLogPort,
                    memoryVectorPort,
                    memoryOutboxPort,
                    businessDocumentRetrieverPort,
                    memoryLifecyclePort,
                    memoryPolicyConfigPort,
                    memoryRetrievalPipelinePort,
                    memoryRefinerPort,
                    memoryReviewCandidatePort,
                    memoryAliasPort,
                    memoryReviewPolicyPort,
                    memoryReviewFeedbackRepositoryPort,
                    captureRules);
        }
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
        MemoryOperation operation = operationBuilder.build(operationId, tenantId, command, request, message.getContent());
        if (!operationGateway.tryStart(operation)) {
            return MemoryIngestionResult.ignored("duplicate_operation");
        }
        try {
            IngestionExecution execution = executeIngestion(operationId, tenantId, command, request, message);
            operationGateway.markCompleted(operationId, execution.result(), execution.classification());
            return execution.result();
        } catch (RuntimeException ex) {
            operationGateway.markFailed(operationId,
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
        int shortTermCount = safeSize(stores.shortTerm().listByUser(userId, Integer.MAX_VALUE));
        int longTermCount = safeSize(stores.longTerm().listByUser(userId, Integer.MAX_VALUE));
        int semanticCount = safeSize(stores.semantic().listByUser(userId, Integer.MAX_VALUE));
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
        MemorySchemaValidationResult reviewTargetLayerValidation =
                reviewApplyClassificationBuilder.validateTargetLayer(directive);
        if (!reviewTargetLayerValidation.valid()) {
            return new IngestionExecution(MemoryIngestionResult.rejected(reviewTargetLayerValidation.reason()), null);
        }
        if (directive != null && directive.requestedAction() == MemoryIngestionAction.DELETE) {
            return executeReviewDeleteApply(tenantId, request, directive);
        }
        SanitizedMemoryInput sanitized = memorySanitizer.sanitize(message.getContent());
        if (sanitized.rejected()) {
            return new IngestionExecution(
                    MemoryIngestionResult.rejected(sanitized.reason(), Map.of("signals", sanitized.signals())),
                    null);
        }
        MemoryClassificationResult reviewClassification =
                reviewApplyClassificationBuilder.build(directive, sanitized.content());
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
        MemorySchemaValidationResult validation = memorySchemaValidator.validate(classification);
        if (!validation.valid()) {
            return new IngestionExecution(MemoryIngestionResult.rejected(validation.reason()), classification);
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
        if (classification.action() == MemoryIngestionAction.UPDATE) {
            List<String> operations = captureCorrection(request, tenantId, classification.correction());
            OccupationCorrection correction = classification.correction();
            return new IngestionExecution(MemoryIngestionResult.accepted(MemoryIngestionAction.UPDATE, operations, Map.of(
                    "targetKind", TARGET_KIND_PROFILE_SLOT,
                    "targetKey", TARGET_KEY_IDENTITY_OCCUPATION,
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
        boolean deleted = stores.storeFor(layer).deleteById(targetMemoryId);
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
        String profileSlot = profileValueNormalizer.resolveSlot(decision, classification);
        String profileGenerationId = isBlank(profileSlot) ? "" : profileSlot + ":" + SnowflakeIds.nextIdString();
        Map<String, Object> metadata = captureMetadata(operationId, tenantId, request, message, decision);
        refinerMetadataWriter.appendRefined(metadata, classification);
        if (!isBlank(profileSlot)) {
            metadata.put("profileSlot", profileSlot);
            metadata.put("generationId", profileGenerationId);
        }
        canonicalAliasResolver.attachIfResolved(metadata, request.userId(), tenantId, decision.content());
        MemoryLayer targetLayer = targetLayer(classification);
        MemoryRecord record = new MemoryRecord(
                refinerMetadataWriter.buildMemoryId(request, classification),
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
        return derivedIndexDispatch.dispatchUpsert(record, userId, tenantId);
    }

    private List<String> deleteIndexesOrEnqueueOutbox(String memoryId, String userId, String tenantId) {
        return derivedIndexDispatch.dispatchDelete(memoryId, userId, tenantId);
    }

    private String saveMemory(MemoryRecord record, MemoryLayer targetLayer) {
        MemoryLayer safeLayer = targetLayer == null ? MemoryLayer.SHORT_TERM : targetLayer;
        stores.storeFor(safeLayer).save(record);
        return switch (safeLayer) {
            case LONG_TERM -> "LONG_TERM_SAVE";
            case SEMANTIC -> "SEMANTIC_SAVE";
            case WORKING, SHORT_TERM -> "SHORT_TERM_SAVE";
        };
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
            List<MemoryRefinementMemory> existingMemories = refinementInputBuilder.existingMemories(request.userId());
            if (refinementDepthGuard.exceedsMaxDepth(existingMemories)) {
                return baseline;
            }
            int currentDepth = refinementDepthGuard.currentMaxDepth(existingMemories);
            MemoryRefinementContextParser.Zones contextZones = refinementContextParser.parse(sanitizedContent);
            List<MemoryReviewFeedbackSample> feedbackExamples =
                    refinerFeedbackLookup.recentResolved(tenantId, request.userId(), baseline);
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
                    refinementInputBuilder.stickyAnchors(existingMemories),
                    feedbackExamples));
            return applyRefinementResult(result, baseline, contextZones, currentDepth);
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

    private MemoryClassificationResult applyRefinementResult(MemoryRefinementResult result,
                                                             MemoryClassificationResult baseline,
                                                             MemoryRefinementContextParser.Zones contextZones,
                                                             int currentDepth) {
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
        List<MemoryClassificationResult> classifications = supportedRefinedClassifications(result, contextZones, currentDepth);
        if (classifications.isEmpty()) {
            return withRefinerDelta(
                    baseline,
                    MemoryIngestionAction.IGNORE,
                    "",
                    "",
                    "unsupported_refined_operation",
                    Map.of("status", "unsupported"));
        }
        MemoryClassificationResult circuitBreaker = refinerBatchCircuitBreaker.evaluate(result, classifications);
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
                                                                             MemoryRefinementContextParser.Zones contextZones,
                                                                             int currentDepth) {
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
                    METADATA_REFINER_OPERATION_COUNT, supportedCount,
                    MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, currentDepth + 1);
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
        Map<String, Object> metadata = new LinkedHashMap<>(operation.metadata());
        metadata.putAll(result.metadata());
        metadata.put(METADATA_CONFIDENCE, operation.confidence());
        metadata.put(METADATA_IMPORTANCE, operation.importance());
        metadata.put(METADATA_VALUE_SCORE, operation.valueScore());
        metadata.put(METADATA_RISK_SCORE, operation.riskScore());
        metadata.put(METADATA_SOURCE_MESSAGE_IDS, effectiveSourceMessageIds(operation, fallbackSourceMessageIds));
        metadata.putAll(extraMetadata);
        MemoryReviewPolicyDecision gateDecision = Objects.requireNonNullElseGet(
                memoryReviewPolicyPort.evaluateRefinedAdd(operation, memoryPolicyConfigPort.current()),
                MemoryReviewPolicyDecision::autoCommit);
        if (gateDecision.action() == MemoryReviewPolicyDecision.Action.DROP) {
            String dropReason = isBlank(gateDecision.reason()) ? REFINER_ADD_LOW_CONFIDENCE : gateDecision.reason();
            metadata.put("status", REFINER_STATUS_DROPPED);
            metadata.put("dropReason", dropReason);
            return new MemoryClassificationResult(
                    MemoryIngestionAction.IGNORE,
                    null,
                    null,
                    new RefinedMemoryDelta(
                            MemoryIngestionAction.ADD,
                            operation.targetKind(),
                            operation.targetKey(),
                            dropReason,
                            metadata),
                    dropReason);
        }
        if (gateDecision.action() == MemoryReviewPolicyDecision.Action.REVIEW) {
            String reviewReason = isBlank(gateDecision.reason()) ? result.reason() : gateDecision.reason();
            metadata.put("status", REFINER_STATUS_PENDING_REVIEW);
            metadata.put("reviewReason", reviewReason);
            metadata.put(METADATA_CONTENT, operation.content());
            return new MemoryClassificationResult(
                    MemoryIngestionAction.REVIEW,
                    null,
                    null,
                    new RefinedMemoryDelta(
                            MemoryIngestionAction.ADD,
                            operation.targetKind(),
                            operation.targetKey(),
                            result.reason(),
                            metadata),
                    result.reason());
        }
        MemoryCaptureDecision decision = MemoryCaptureDecision.refinedAdd(
                operation.content(),
                isBlank(operation.targetKind()) ? "FACT" : operation.targetKind(),
                operation.importance(),
                operation.confidence(),
                operation.valueScore(),
                operation.riskScore(),
                List.of("llm_refiner"),
                operation.signals());
        metadata.put("status", "enabled");
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
        MemoryTrackWriteResult result = trackWriteService.writeOccupationCorrection(
                request.userId(),
                tenantId,
                request.messageId(),
                correction.incorrectValue(),
                correction.correctValue());
        return result.operations();
    }

    private boolean captureProfileFact(MemoryWriteRequest request,
                                       String tenantId,
                                       MemoryCaptureDecision decision,
                                       String slotKey,
                                       String generationId) {
        if (isBlank(slotKey)) {
            return false;
        }
        String value = profileValueNormalizer.normalize(slotKey, decision.content());
        return trackWriteService.writeProfileFact(
                request.userId(),
                tenantId,
                slotKey,
                value,
                decision.confidenceLevel(),
                request.messageId(),
                generationId);
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

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
