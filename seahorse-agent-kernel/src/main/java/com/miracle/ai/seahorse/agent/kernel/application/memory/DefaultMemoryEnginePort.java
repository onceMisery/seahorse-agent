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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    private static final String TARGET_KIND_PROFILE_SLOT = "PROFILE_SLOT";
    private static final String TARGET_KEY_IDENTITY_OCCUPATION = "identity.occupation";

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
    private final MemoryIndexStoreSupport indexStoreSupport;
    private final MemoryProfileTrackSupport profileTrackSupport;
    private final MemoryReviewStagingSupport reviewStagingSupport;
    private final MemoryCaptureExecutionSupport captureExecutionSupport;
    private final MemoryRefinementPipeline refinementPipeline;

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
        this.indexStoreSupport = new MemoryIndexStoreSupport(this.stores, this.derivedIndexDispatch);
        this.profileTrackSupport = new MemoryProfileTrackSupport(this.trackWriteService);
        this.reviewStagingSupport = new MemoryReviewStagingSupport(
                this.memoryReviewCandidatePort, this.memoryPolicyConfigPort, this.stores, this.indexStoreSupport);
        this.captureExecutionSupport = new MemoryCaptureExecutionSupport(
                this.profileValueNormalizer,
                this.refinerMetadataWriter,
                this.canonicalAliasResolver,
                this.profileTrackSupport,
                this.indexStoreSupport,
                this.reviewStagingSupport);
        this.refinementPipeline = new MemoryRefinementPipeline(
                this.options,
                this.memorySchemaValidator,
                this.memoryRefinerPort,
                this.refinementContextParser,
                this.refinementInputBuilder,
                this.refinerBatchCircuitBreaker,
                this.refinerFeedbackLookup,
                this.refinementDepthGuard,
                this.memoryReviewPolicyPort,
                this.memoryPolicyConfigPort,
                this.reviewStagingSupport,
                this.captureExecutionSupport);
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
        boolean reviewDeleteApply = reviewStagingSupport.isReviewDeleteApply(command);
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
            operationGateway.markFailed(operationId, failureMessage(ex));
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
            return reviewStagingSupport.executeReviewDeleteApply(tenantId, request, directive);
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
            return captureExecutionSupport.executeAcceptedClassification(
                    operationId, tenantId, request, message, reviewClassification);
        }
        MemoryPreFilterResult preFilterResult = memoryPreFilter.filter(sanitized.content());
        if (!preFilterResult.accepted()) {
            return new IngestionExecution(MemoryIngestionResult.ignored(preFilterResult.reason()), null);
        }
        MemoryClassificationResult classification = memorySemanticClassifier.classify(sanitized.content());
        classification = refinementPipeline.refineClassification(
                operationId, tenantId, command, request, sanitized.content(), classification);
        if (classification.refinedDelta() != null && classification.refinedDelta().metadata().containsKey("refinerBatch")) {
            return refinementPipeline.executeRefinerBatch(operationId, tenantId, request, message, classification);
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
            return reviewStagingSupport.executeReviewStaging(operationId, tenantId, request, classification);
        }
        if (classification.action() == MemoryIngestionAction.UPDATE) {
            List<String> operations = profileTrackSupport.captureCorrection(
                    request, tenantId, classification.correction());
            OccupationCorrection correction = classification.correction();
            return new IngestionExecution(MemoryIngestionResult.accepted(MemoryIngestionAction.UPDATE, operations, Map.of(
                    "targetKind", TARGET_KIND_PROFILE_SLOT,
                    "targetKey", TARGET_KEY_IDENTITY_OCCUPATION,
                    "incorrectValue", correction.incorrectValue(),
                    "correctValue", correction.correctValue())), classification);
        }
        return captureExecutionSupport.executeAcceptedClassification(
                operationId, tenantId, request, message, classification);
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

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String failureMessage(RuntimeException ex) {
        return CredentialTextRedactor.redact(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
    }

}
