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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerFeedbackExportRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPendingSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KernelMemoryReviewService implements MemoryReviewInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelMemoryReviewService.class);

    private static final String DEFAULT_OPERATOR = "system";
    private static final String APPLY_OPERATION_PREFIX = "memory-review-apply-";
    private static final String FEEDBACK_SAMPLE_PREFIX = "memory-review-feedback-";
    private static final String REVIEW_MESSAGE_PREFIX = "review-";
    private static final String SOURCE_APPROVE = "memory-review-approve";
    private static final String SOURCE_MODIFY = "memory-review-modify";
    private static final String SOURCE_ALIAS = "memory-review-alias";
    private static final String TARGET_KIND_ALIAS = "ALIAS";
    private static final String REVIEWED_LAYER_ALIAS = "ALIAS";
    private static final String ALIAS_REVIEWED_ID_PREFIX = "alias:";
    private static final String METADATA_ALIAS_TEXT = "aliasText";
    private static final String METADATA_CANONICAL_ENTITY_ID = "canonicalEntityId";
    private static final String METADATA_CANONICAL_NAME = "canonicalName";
    private static final String METADATA_ENTITY_TYPE = "entityType";
    private static final String METADATA_CONFIDENCE_LEVEL = "confidenceLevel";
    private static final String METADATA_SOURCE_MEMORY_IDS = "sourceMemoryIds";
    private static final String DETAIL_APPLY_OPERATION_ID = "applyOperationId";
    private static final String DETAIL_CANDIDATE_ID = "candidateId";
    private static final String DETAIL_CANDIDATE_OPERATION_ID = "candidateOperationId";
    private static final String DETAIL_FEEDBACK_SAMPLE_ID = "feedbackSampleId";
    private static final String DETAIL_OPERATION_ID = "operationId";
    private static final String DETAIL_REQUESTED_ACTION = "requestedAction";
    private static final String DETAIL_REVIEWER_ID = "reviewerId";
    private static final String DETAIL_SOURCE_MESSAGE_IDS = "sourceMessageIds";
    private static final String DETAIL_TARGET_KEY = "targetKey";
    private static final String DETAIL_TARGET_KIND = "targetKind";
    private static final String DETAIL_TARGET_LAYER = "targetLayer";

    private final MemoryReviewManagementRepositoryPort reviewRepositoryPort;
    private final MemoryIngestionWorkflowPort ingestionWorkflowPort;
    private final MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort;
    private final MemoryTraceRecorder traceRecorder;
    private final MemoryAliasPort aliasPort;

    public KernelMemoryReviewService(MemoryReviewManagementRepositoryPort reviewRepositoryPort,
                                     MemoryIngestionWorkflowPort ingestionWorkflowPort) {
        this(reviewRepositoryPort, ingestionWorkflowPort, MemoryReviewFeedbackRepositoryPort.empty(),
                MemoryTraceRecorder.noop());
    }

    public KernelMemoryReviewService(MemoryReviewManagementRepositoryPort reviewRepositoryPort,
                                     MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                     MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort) {
        this(reviewRepositoryPort, ingestionWorkflowPort, feedbackRepositoryPort, MemoryTraceRecorder.noop());
    }

    public KernelMemoryReviewService(MemoryReviewManagementRepositoryPort reviewRepositoryPort,
                                     MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                     MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort,
                                     MemoryTraceRecorder traceRecorder) {
        this(reviewRepositoryPort, ingestionWorkflowPort, feedbackRepositoryPort, traceRecorder,
                MemoryAliasPort.noop());
    }

    public KernelMemoryReviewService(MemoryReviewManagementRepositoryPort reviewRepositoryPort,
                                     MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                     MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort,
                                     MemoryTraceRecorder traceRecorder,
                                     MemoryAliasPort aliasPort) {
        this.reviewRepositoryPort = Objects.requireNonNullElseGet(reviewRepositoryPort,
                MemoryReviewManagementRepositoryPort::empty);
        this.ingestionWorkflowPort = Objects.requireNonNull(ingestionWorkflowPort,
                "ingestionWorkflowPort must not be null");
        this.feedbackRepositoryPort = Objects.requireNonNullElseGet(feedbackRepositoryPort,
                MemoryReviewFeedbackRepositoryPort::empty);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
        this.aliasPort = Objects.requireNonNullElseGet(aliasPort, MemoryAliasPort::noop);
    }

    @Override
    public MemoryReviewPage page(String tenantId,
                                 String userId,
                                 MemoryReviewStatus status,
                                 String targetKind,
                                 String targetKey,
                                 long current,
                                 long size) {
        return reviewRepositoryPort.pageReviewCandidates(
                new MemoryReviewQuery(tenantId, userId, status, targetKind, targetKey, current, size));
    }

    @Override
    public MemoryReviewPendingSummary pendingSummary(String tenantId,
                                                     String userId,
                                                     String targetKind,
                                                     String targetKey) {
        MemoryReviewPage page = reviewRepositoryPort.pageReviewCandidates(new MemoryReviewQuery(
                tenantId,
                userId,
                MemoryReviewStatus.PENDING,
                targetKind,
                targetKey,
                1,
                1));
        MemoryReviewRecord latest = page.records().isEmpty() ? null : page.records().get(0);
        return new MemoryReviewPendingSummary(page.total(), latest);
    }

    @Override
    public MemoryReviewRecord queryById(String candidateId) {
        return reviewRepositoryPort.findReviewItem(requireText(candidateId, "candidateId"))
                .orElseThrow(() -> new IllegalArgumentException("memory review candidate not found: " + candidateId));
    }

    @Override
    public MemoryReviewRecord approve(String candidateId, MemoryReviewDecisionCommand command) {
        MemoryReviewRecord current = requirePending(candidateId);
        return applyAccepted(current, safeCommand(command), current.content(), SOURCE_APPROVE, "approve");
    }

    @Override
    public MemoryReviewRecord modify(String candidateId, MemoryReviewDecisionCommand command) {
        MemoryReviewDecisionCommand safeCommand = safeCommand(command);
        requireText(safeCommand.correctedContent(), "correctedContent");
        MemoryReviewRecord current = requirePending(candidateId);
        return applyAccepted(current, safeCommand, safeCommand.correctedContent(), SOURCE_MODIFY, "modify");
    }

    @Override
    public MemoryReviewRecord reject(String candidateId, MemoryReviewDecisionCommand command) {
        MemoryReviewRecord current = requirePending(candidateId);
        MemoryReviewDecisionCommand safeCommand = safeCommand(command);
        MemoryReviewRecord rejected = reviewRepositoryPort.applyReviewDecision(new MemoryReviewDecision(
                current.candidateId(),
                MemoryReviewStatus.REJECTED,
                operator(safeCommand.reviewerId()),
                safeCommand.comment(),
                "",
                Map.of(),
                "",
                ""));
        recordTrace("reject", MemoryReviewStatus.REJECTED.name(), current, safeCommand, Map.of(
                "reviewComment", safeCommand.comment(),
                "reviewStatus", rejected.reviewStatus().name()));
        recordFeedback(current, rejected);
        return rejected;
    }

    @Override
    public List<MemoryReviewFeedbackSample> listFeedbackSamples(String candidateId, int limit) {
        return feedbackRepositoryPort.listByCandidate(
                requireText(candidateId, "candidateId"),
                limit > 0 ? limit : 20);
    }

    @Override
    public List<MemoryReviewFeedbackSample> listFeedbackSamples(String tenantId,
                                                                String userId,
                                                                MemoryReviewStatus status,
                                                                String targetKind,
                                                                String targetKey,
                                                                int limit) {
        return feedbackRepositoryPort.listSamples(new MemoryReviewFeedbackQuery(
                tenantId,
                userId,
                status,
                targetKind,
                targetKey,
                limit));
    }

    @Override
    public List<MemoryRefinerFeedbackExportRecord> exportRefinerFeedbackSamples(String tenantId,
                                                                                String userId,
                                                                                MemoryReviewStatus status,
                                                                                String targetKind,
                                                                                String targetKey,
                                                                                int limit) {
        return listFeedbackSamples(tenantId, userId, status, targetKind, targetKey, limit).stream()
                .map(MemoryRefinerFeedbackExportRecord::fromReviewFeedbackSample)
                .toList();
    }

    private MemoryReviewRecord applyAccepted(MemoryReviewRecord current,
                                             MemoryReviewDecisionCommand command,
                                             String content,
                                             String source,
                                             String eventType) {
        if (isAliasTarget(current)) {
            return applyAlias(current, command, content, eventType);
        }
        String operationId = APPLY_OPERATION_PREFIX + current.candidateId();
        String applyContent = reviewApplyContent(current, content);
        claimForApply(current, command);
        MemoryIngestionResult result = ingestionWorkflowPort.ingest(MemoryIngestionCommand.reviewApply(
                operationId,
                current.tenantId(),
                source,
                MemoryWriteRequest.builder()
                        .userId(current.userId())
                        .conversationId(current.conversationId())
                        .messageId(reviewMessageId(current))
                        .message(ChatMessage.user(applyContent))
                        .build(),
                reviewDirective(current, command)));
        if (result == null || result.status() != MemoryIngestionStatus.ACCEPTED) {
            String reason = result == null ? "empty_result" : result.reason();
            releaseApplyClaim(current, command);
            recordTrace(eventType, MemoryTraceEvent.STATUS_FAILED, current, command, Map.of(
                    DETAIL_OPERATION_ID, operationId,
                    DETAIL_APPLY_OPERATION_ID, operationId,
                    "source", source,
                    "resultStatus", result == null ? "NULL" : result.status().name(),
                    "reason", reason));
            throw new IllegalStateException("review ingestion was not accepted: " + reason);
        }
        MemoryReviewRecord reviewed = reviewRepositoryPort.applyReviewDecision(new MemoryReviewDecision(
                current.candidateId(),
                MemoryReviewStatus.APPLIED,
                operator(command.reviewerId()),
                command.comment(),
                applyContent,
                command.correctedMetadata(),
                operationId,
                current.targetLayer()));
        recordTrace(eventType, reviewed.reviewStatus().name(), current, command, Map.of(
                DETAIL_OPERATION_ID, operationId,
                DETAIL_APPLY_OPERATION_ID, operationId,
                "source", source,
                "reviewStatus", reviewed.reviewStatus().name(),
                "reviewedMemoryId", reviewed.reviewedMemoryId(),
                "reviewedLayer", reviewed.reviewedLayer()));
        recordFeedback(current, reviewed);
        return reviewed;
    }

    private MemoryReviewRecord applyAlias(MemoryReviewRecord current,
                                          MemoryReviewDecisionCommand command,
                                          String content,
                                          String eventType) {
        String operationId = APPLY_OPERATION_PREFIX + current.candidateId();
        Map<String, Object> metadata = mergedMetadata(current.metadata(), command.correctedMetadata());
        String aliasText = aliasApplyContent(current, content, metadata);
        claimForApply(current, command);
        MemoryAliasCommand aliasCommand;
        try {
            aliasCommand = aliasCommand(current, aliasText, metadata);
            aliasPort.upsertAlias(aliasCommand);
        } catch (RuntimeException ex) {
            releaseApplyClaim(current, command);
            recordTrace(eventType, MemoryTraceEvent.STATUS_FAILED, current, command, Map.of(
                    DETAIL_OPERATION_ID, operationId,
                    DETAIL_APPLY_OPERATION_ID, operationId,
                    "source", SOURCE_ALIAS,
                    "reason", errorMessage(ex)));
            throw new IllegalStateException("alias review apply failed: " + errorMessage(ex), ex);
        }
        Map<String, Object> chosenMetadata = aliasReviewMetadata(current, command, metadata);
        MemoryReviewRecord reviewed = reviewRepositoryPort.applyReviewDecision(new MemoryReviewDecision(
                current.candidateId(),
                MemoryReviewStatus.APPLIED,
                operator(command.reviewerId()),
                command.comment(),
                aliasText,
                chosenMetadata,
                reviewedAliasId(aliasCommand),
                REVIEWED_LAYER_ALIAS));
        recordTrace(eventType, reviewed.reviewStatus().name(), current, command, Map.of(
                DETAIL_OPERATION_ID, operationId,
                DETAIL_APPLY_OPERATION_ID, operationId,
                "source", SOURCE_ALIAS,
                "reviewStatus", reviewed.reviewStatus().name(),
                "reviewedMemoryId", reviewed.reviewedMemoryId(),
                "reviewedLayer", reviewed.reviewedLayer()));
        recordFeedback(current, reviewed);
        return reviewed;
    }

    private boolean isAliasTarget(MemoryReviewRecord current) {
        return TARGET_KIND_ALIAS.equalsIgnoreCase(current.targetKind());
    }

    private MemoryAliasCommand aliasCommand(MemoryReviewRecord current,
                                            String aliasText,
                                            Map<String, Object> metadata) {
        String canonicalEntityId = requireText(stringMetadata(metadata, METADATA_CANONICAL_ENTITY_ID, ""),
                METADATA_CANONICAL_ENTITY_ID);
        String canonicalName = stringMetadata(metadata, METADATA_CANONICAL_NAME, canonicalEntityId);
        String entityType = stringMetadata(metadata, METADATA_ENTITY_TYPE, "ENTITY");
        double confidenceLevel = doubleMetadata(metadata, METADATA_CONFIDENCE_LEVEL);
        if (confidenceLevel <= 0D) {
            confidenceLevel = current.confidence();
        }
        return new MemoryAliasCommand(
                current.userId(),
                current.tenantId(),
                requireText(aliasText, METADATA_ALIAS_TEXT),
                canonicalEntityId,
                canonicalName,
                entityType,
                confidenceLevel,
                SOURCE_ALIAS,
                sourceMemoryIds(metadata),
                aliasReviewMetadata(current, null, metadata));
    }

    private Map<String, Object> aliasReviewMetadata(MemoryReviewRecord current,
                                                    MemoryReviewDecisionCommand command,
                                                    Map<String, Object> metadata) {
        Map<String, Object> values = new LinkedHashMap<>(Objects.requireNonNullElse(metadata, Map.of()));
        values.put("reviewCandidateId", current.candidateId());
        values.put("reviewOperationId", current.operationId());
        values.put("reviewSource", SOURCE_ALIAS);
        if (command != null) {
            values.put("reviewerId", operator(command.reviewerId()));
        }
        return values;
    }

    private String aliasApplyContent(MemoryReviewRecord current,
                                     String content,
                                     Map<String, Object> metadata) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        String aliasText = stringMetadata(metadata, METADATA_ALIAS_TEXT, "");
        if (!aliasText.isBlank()) {
            return aliasText;
        }
        if (current.targetKey() != null && !current.targetKey().isBlank()) {
            return current.targetKey();
        }
        return current.content();
    }

    private List<String> sourceMemoryIds(Map<String, Object> metadata) {
        Object value = metadata.get(METADATA_SOURCE_MEMORY_IDS);
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    private String reviewedAliasId(MemoryAliasCommand command) {
        return ALIAS_REVIEWED_ID_PREFIX + command.canonicalEntityId() + ":" + command.aliasText();
    }

    private String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = Objects.requireNonNullElse(metadata, Map.of()).get(key);
        if (value == null || value.toString().isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return value.toString().trim();
    }

    private double doubleMetadata(Map<String, Object> metadata, String key) {
        Object value = Objects.requireNonNullElse(metadata, Map.of()).get(key);
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

    private MemoryReviewRecord claimForApply(MemoryReviewRecord current, MemoryReviewDecisionCommand command) {
        return reviewRepositoryPort.applyReviewDecision(new MemoryReviewDecision(
                current.candidateId(),
                MemoryReviewStatus.APPLYING,
                operator(command.reviewerId()),
                command.comment(),
                "",
                Map.of(),
                "",
                ""));
    }

    private void releaseApplyClaim(MemoryReviewRecord current, MemoryReviewDecisionCommand command) {
        try {
            reviewRepositoryPort.applyReviewDecision(new MemoryReviewDecision(
                    current.candidateId(),
                    MemoryReviewStatus.PENDING,
                    operator(command.reviewerId()),
                    command.comment(),
                    "",
                    Map.of(),
                    "",
                    ""));
        } catch (RuntimeException ex) {
            LOG.warn("Failed to release memory review apply claim: {} ({})", current.candidateId(), ex.toString());
        }
    }

    private String reviewApplyContent(MemoryReviewRecord current, String content) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (MemoryIngestionAction.DELETE.name().equalsIgnoreCase(current.requestedAction())) {
            if (current.targetKey() != null && !current.targetKey().isBlank()) {
                return current.targetKey();
            }
            return "memory delete review";
        }
        return content;
    }

    private void recordFeedback(MemoryReviewRecord pending, MemoryReviewRecord reviewed) {
        try {
            feedbackRepositoryPort.save(MemoryReviewFeedbackSample.fromDecision(
                    feedbackSampleId(pending),
                    pending,
                    reviewed));
        } catch (RuntimeException ex) {
            LOG.warn("Failed to record memory review feedback sample: {} ({})", pending.candidateId(),
                    ex.toString());
        }
    }

    private MemoryReviewApplyDirective reviewDirective(MemoryReviewRecord current,
                                                       MemoryReviewDecisionCommand command) {
        return new MemoryReviewApplyDirective(
                requestedAction(current.requestedAction()),
                current.targetLayer(),
                current.targetKind(),
                current.targetKey(),
                current.confidence(),
                current.importance(),
                current.valueScore(),
                current.riskScore(),
                current.sourceMessageIds(),
                mergedMetadata(current.metadata(), command.correctedMetadata()));
    }

    private MemoryIngestionAction requestedAction(String value) {
        if (value == null || value.isBlank()) {
            return MemoryIngestionAction.REVIEW;
        }
        try {
            return MemoryIngestionAction.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MemoryIngestionAction.REVIEW;
        }
    }

    private Map<String, Object> mergedMetadata(Map<String, Object> candidateMetadata,
                                               Map<String, Object> correctedMetadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(Objects.requireNonNullElse(candidateMetadata, Map.of()));
        merged.putAll(Objects.requireNonNullElse(correctedMetadata, Map.of()));
        return merged;
    }

    private String feedbackSampleId(MemoryReviewRecord record) {
        return FEEDBACK_SAMPLE_PREFIX + record.candidateId();
    }

    private MemoryReviewRecord requirePending(String candidateId) {
        MemoryReviewRecord current = queryById(candidateId);
        if (current.reviewStatus() != MemoryReviewStatus.PENDING) {
            throw new IllegalStateException("review candidate is not pending: " + current.candidateId());
        }
        return current;
    }

    private MemoryReviewDecisionCommand safeCommand(MemoryReviewDecisionCommand command) {
        return command == null ? new MemoryReviewDecisionCommand(DEFAULT_OPERATOR, "", "", Map.of()) : command;
    }

    private String reviewMessageId(MemoryReviewRecord record) {
        String messageId = record.messageId();
        if (messageId == null || messageId.isBlank()) {
            return REVIEW_MESSAGE_PREFIX + record.candidateId();
        }
        return REVIEW_MESSAGE_PREFIX + messageId.trim();
    }

    private String operator(String reviewerId) {
        return reviewerId == null || reviewerId.isBlank() ? DEFAULT_OPERATOR : reviewerId.trim();
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
    }

    private void recordTrace(String eventType,
                             String status,
                             MemoryReviewRecord current,
                             MemoryReviewDecisionCommand command,
                             Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(DETAIL_CANDIDATE_ID, current.candidateId());
        details.put(DETAIL_OPERATION_ID, current.operationId());
        details.put(DETAIL_CANDIDATE_OPERATION_ID, current.operationId());
        details.put(DETAIL_FEEDBACK_SAMPLE_ID, feedbackSampleId(current));
        details.put(DETAIL_REVIEWER_ID, operator(command == null ? null : command.reviewerId()));
        details.put(DETAIL_REQUESTED_ACTION, requestedAction(current.requestedAction()).name());
        details.put(DETAIL_TARGET_LAYER, current.targetLayer());
        details.put(DETAIL_TARGET_KIND, current.targetKind());
        details.put(DETAIL_TARGET_KEY, current.targetKey());
        details.put(DETAIL_SOURCE_MESSAGE_IDS, current.sourceMessageIds());
        details.putAll(extra);
        traceRecorder.record(new MemoryTraceEvent(
                current.candidateId(),
                current.tenantId(),
                current.userId(),
                current.conversationId(),
                current.messageId(),
                "memory-review",
                eventType,
                status,
                current.candidateId(),
                "candidate",
                details,
                java.time.Instant.now()));
    }
}
