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
    private static final String FEEDBACK_TYPE_APPROVE = "APPROVE";
    private static final String FEEDBACK_TYPE_MODIFY = "MODIFY";
    private static final String FEEDBACK_TYPE_REJECT = "REJECT";
    private static final String REFINER_ACTION_ADD = "ADD";
    private static final String REFINER_ACTION_IGNORE = "IGNORE";

    private final MemoryReviewManagementRepositoryPort reviewRepositoryPort;
    private final MemoryIngestionWorkflowPort ingestionWorkflowPort;
    private final MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort;
    private final MemoryTraceRecorder traceRecorder;

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
        this.reviewRepositoryPort = Objects.requireNonNullElseGet(reviewRepositoryPort,
                MemoryReviewManagementRepositoryPort::empty);
        this.ingestionWorkflowPort = Objects.requireNonNull(ingestionWorkflowPort,
                "ingestionWorkflowPort must not be null");
        this.feedbackRepositoryPort = Objects.requireNonNullElseGet(feedbackRepositoryPort,
                MemoryReviewFeedbackRepositoryPort::empty);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, MemoryTraceRecorder::noop);
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
                .map(this::toRefinerFeedbackExportRecord)
                .toList();
    }

    private MemoryReviewRecord applyAccepted(MemoryReviewRecord current,
                                             MemoryReviewDecisionCommand command,
                                             String content,
                                             String source,
                                             String eventType) {
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
                    "operationId", operationId,
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
                "operationId", operationId,
                "source", source,
                "reviewStatus", reviewed.reviewStatus().name(),
                "reviewedMemoryId", reviewed.reviewedMemoryId(),
                "reviewedLayer", reviewed.reviewedLayer()));
        recordFeedback(current, reviewed);
        return reviewed;
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

    private MemoryRefinerFeedbackExportRecord toRefinerFeedbackExportRecord(MemoryReviewFeedbackSample sample) {
        return new MemoryRefinerFeedbackExportRecord(
                sample.sampleId(),
                sample.candidateId(),
                feedbackType(sample),
                promptInput(sample),
                rejectedOutput(sample),
                chosenOutput(sample),
                feedbackMetadata(sample),
                sample.createdAt());
    }

    private String feedbackType(MemoryReviewFeedbackSample sample) {
        if (sample.reviewStatus() == MemoryReviewStatus.REJECTED) {
            return FEEDBACK_TYPE_REJECT;
        }
        if (!Objects.equals(sample.rejectedContent(), sample.chosenContent())
                || !sample.chosenMetadata().isEmpty()) {
            return FEEDBACK_TYPE_MODIFY;
        }
        return FEEDBACK_TYPE_APPROVE;
    }

    private Map<String, Object> promptInput(MemoryReviewFeedbackSample sample) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tenantId", sample.tenantId());
        input.put("userId", sample.userId());
        input.put("operationId", sample.operationId());
        input.put("candidateId", sample.candidateId());
        input.put("targetLayer", sample.targetLayer());
        input.put("targetKind", sample.targetKind());
        input.put("targetKey", sample.targetKey());
        input.put("sourceMessageIds", sample.sourceMessageIds());
        input.put("reviewComment", sample.reviewComment());
        return input;
    }

    private Map<String, Object> rejectedOutput(MemoryReviewFeedbackSample sample) {
        return refinerOutput(sample.requestedAction(), sample.rejectedContent(), sample.rejectedMetadata());
    }

    private Map<String, Object> chosenOutput(MemoryReviewFeedbackSample sample) {
        if (sample.reviewStatus() == MemoryReviewStatus.REJECTED) {
            return refinerOutput(REFINER_ACTION_IGNORE, "", Map.of());
        }
        return refinerOutput(REFINER_ACTION_ADD, sample.chosenContent(), sample.chosenMetadata());
    }

    private Map<String, Object> refinerOutput(String action, String content, Map<String, Object> metadata) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("action", action);
        output.put("content", Objects.requireNonNullElse(content, ""));
        output.put("metadata", Objects.requireNonNullElse(metadata, Map.of()));
        return output;
    }

    private Map<String, Object> feedbackMetadata(MemoryReviewFeedbackSample sample) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestedAction", sample.requestedAction());
        metadata.put("reviewStatus", sample.reviewStatus().name());
        metadata.put("reviewerId", sample.reviewerId());
        metadata.put("targetLayer", sample.targetLayer());
        metadata.put("reviewedMemoryId", sample.reviewedMemoryId());
        metadata.put("reviewedLayer", sample.reviewedLayer());
        return metadata;
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

    private void recordTrace(String eventType,
                             String status,
                             MemoryReviewRecord current,
                             MemoryReviewDecisionCommand command,
                             Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("candidateId", current.candidateId());
        details.put("operationId", current.operationId());
        details.put("reviewerId", operator(command == null ? null : command.reviewerId()));
        details.put("targetLayer", current.targetLayer());
        details.put("targetKind", current.targetKind());
        details.put("targetKey", current.targetKey());
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
