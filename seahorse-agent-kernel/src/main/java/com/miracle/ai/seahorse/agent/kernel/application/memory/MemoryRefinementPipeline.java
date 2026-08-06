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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPolicyDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.RefinedMemoryOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 记忆精炼流水线协作者（从 {@link DefaultMemoryEnginePort} 提取）。
 * 按 §7 收敛原则外提：只负责 LLM refiner 调用、精炼结果到分类的翻译（含策略门控）与 refiner batch 落地编排。
 */
final class MemoryRefinementPipeline {

    private static final String METADATA_CONTENT = "content";
    private static final String METADATA_CONFIDENCE = "confidence";
    private static final String METADATA_IMPORTANCE = "importance";
    private static final String METADATA_VALUE_SCORE = "valueScore";
    private static final String METADATA_RISK_SCORE = "riskScore";
    private static final String METADATA_SOURCE_MESSAGE_IDS = "sourceMessageIds";
    private static final String METADATA_REFINER_OPERATION_INDEX = "refinerOperationIndex";
    private static final String METADATA_REFINER_OPERATION_COUNT = "refinerOperationCount";
    private static final String METADATA_REFINER_BATCH = "refinerBatch";
    private static final String REFINER_ADD_LOW_CONFIDENCE = MemoryReviewPolicyPort.REFINER_ADD_LOW_CONFIDENCE;
    private static final String REFINER_ADD_REVIEW_CONFIDENCE = MemoryReviewPolicyPort.REFINER_ADD_REVIEW_CONFIDENCE;
    private static final String REFINER_ADD_REVIEW_RISK = MemoryReviewPolicyPort.REFINER_ADD_REVIEW_RISK;
    private static final String REFINER_STATUS_DROPPED = "dropped";
    private static final String REFINER_STATUS_PENDING_REVIEW = "pending_review";

    private final MemoryEngineOptions options;
    private final MemorySchemaValidator memorySchemaValidator;
    private final MemoryRefinerPort memoryRefinerPort;
    private final MemoryRefinementContextParser refinementContextParser;
    private final MemoryRefinementInputBuilder refinementInputBuilder;
    private final MemoryRefinerBatchCircuitBreaker refinerBatchCircuitBreaker;
    private final MemoryRefinerFeedbackLookup refinerFeedbackLookup;
    private final MemoryRefinementDepthGuard refinementDepthGuard;
    private final MemoryReviewPolicyPort memoryReviewPolicyPort;
    private final MemoryPolicyConfigPort memoryPolicyConfigPort;
    private final MemoryReviewStagingSupport reviewStagingSupport;
    private final MemoryCaptureExecutionSupport captureExecutionSupport;

    MemoryRefinementPipeline(MemoryEngineOptions options,
                             MemorySchemaValidator memorySchemaValidator,
                             MemoryRefinerPort memoryRefinerPort,
                             MemoryRefinementContextParser refinementContextParser,
                             MemoryRefinementInputBuilder refinementInputBuilder,
                             MemoryRefinerBatchCircuitBreaker refinerBatchCircuitBreaker,
                             MemoryRefinerFeedbackLookup refinerFeedbackLookup,
                             MemoryRefinementDepthGuard refinementDepthGuard,
                             MemoryReviewPolicyPort memoryReviewPolicyPort,
                             MemoryPolicyConfigPort memoryPolicyConfigPort,
                             MemoryReviewStagingSupport reviewStagingSupport,
                             MemoryCaptureExecutionSupport captureExecutionSupport) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.memorySchemaValidator = Objects.requireNonNull(memorySchemaValidator,
                "memorySchemaValidator must not be null");
        this.memoryRefinerPort = Objects.requireNonNull(memoryRefinerPort, "memoryRefinerPort must not be null");
        this.refinementContextParser = Objects.requireNonNull(refinementContextParser,
                "refinementContextParser must not be null");
        this.refinementInputBuilder = Objects.requireNonNull(refinementInputBuilder,
                "refinementInputBuilder must not be null");
        this.refinerBatchCircuitBreaker = Objects.requireNonNull(refinerBatchCircuitBreaker,
                "refinerBatchCircuitBreaker must not be null");
        this.refinerFeedbackLookup = Objects.requireNonNull(refinerFeedbackLookup,
                "refinerFeedbackLookup must not be null");
        this.refinementDepthGuard = Objects.requireNonNull(refinementDepthGuard,
                "refinementDepthGuard must not be null");
        this.memoryReviewPolicyPort = Objects.requireNonNull(memoryReviewPolicyPort,
                "memoryReviewPolicyPort must not be null");
        this.memoryPolicyConfigPort = Objects.requireNonNull(memoryPolicyConfigPort,
                "memoryPolicyConfigPort must not be null");
        this.reviewStagingSupport = Objects.requireNonNull(reviewStagingSupport,
                "reviewStagingSupport must not be null");
        this.captureExecutionSupport = Objects.requireNonNull(captureExecutionSupport,
                "captureExecutionSupport must not be null");
    }

    IngestionExecution executeRefinerBatch(String operationId,
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
                    ? reviewStagingSupport.executeReviewStaging(operationId, tenantId, request, classification)
                    : captureExecutionSupport.executeAcceptedClassification(
                    operationId, tenantId, request, message, classification);
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
            return new IngestionExecution(MemoryIngestionResult.review(batchClassification.reason()),
                    batchClassification);
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

    MemoryClassificationResult refineClassification(String operationId,
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
                                "refiner_failed:" + failureMessage(ex),
                                Map.of("status", "failed_closed")),
                        "refiner_failed");
            }
            return withRefinerDelta(
                    baseline,
                    MemoryIngestionAction.IGNORE,
                    "",
                    "",
                    "failed_open:" + failureMessage(ex),
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
        List<MemoryClassificationResult> classifications = supportedRefinedClassifications(
                result, contextZones, currentDepth);
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
                    : refinedReviewClassification(
                    operation, result, batchMetadata, contextZones.targetSourceMessageIds());
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

    private List<String> effectiveSourceMessageIds(RefinedMemoryOperation operation,
                                                   List<String> fallbackSourceMessageIds) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String failureMessage(RuntimeException ex) {
        return CredentialTextRedactor.redact(
                Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
    }
}
