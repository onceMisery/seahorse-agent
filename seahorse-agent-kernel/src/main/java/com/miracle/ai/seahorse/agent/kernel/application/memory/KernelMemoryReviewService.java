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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final MemoryReviewManagementRepositoryPort reviewRepositoryPort;
    private final MemoryIngestionWorkflowPort ingestionWorkflowPort;
    private final MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort;

    public KernelMemoryReviewService(MemoryReviewManagementRepositoryPort reviewRepositoryPort,
                                     MemoryIngestionWorkflowPort ingestionWorkflowPort) {
        this(reviewRepositoryPort, ingestionWorkflowPort, MemoryReviewFeedbackRepositoryPort.empty());
    }

    public KernelMemoryReviewService(MemoryReviewManagementRepositoryPort reviewRepositoryPort,
                                     MemoryIngestionWorkflowPort ingestionWorkflowPort,
                                     MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort) {
        this.reviewRepositoryPort = Objects.requireNonNullElseGet(reviewRepositoryPort,
                MemoryReviewManagementRepositoryPort::empty);
        this.ingestionWorkflowPort = Objects.requireNonNull(ingestionWorkflowPort,
                "ingestionWorkflowPort must not be null");
        this.feedbackRepositoryPort = Objects.requireNonNullElseGet(feedbackRepositoryPort,
                MemoryReviewFeedbackRepositoryPort::empty);
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
        return applyAccepted(current, safeCommand(command), current.content(), SOURCE_APPROVE);
    }

    @Override
    public MemoryReviewRecord modify(String candidateId, MemoryReviewDecisionCommand command) {
        MemoryReviewDecisionCommand safeCommand = safeCommand(command);
        requireText(safeCommand.correctedContent(), "correctedContent");
        MemoryReviewRecord current = requirePending(candidateId);
        return applyAccepted(current, safeCommand, safeCommand.correctedContent(), SOURCE_MODIFY);
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
        recordFeedback(current, rejected);
        return rejected;
    }

    private MemoryReviewRecord applyAccepted(MemoryReviewRecord current,
                                             MemoryReviewDecisionCommand command,
                                             String content,
                                             String source) {
        String operationId = APPLY_OPERATION_PREFIX + current.candidateId();
        MemoryIngestionResult result = ingestionWorkflowPort.ingest(new MemoryIngestionCommand(
                operationId,
                current.tenantId(),
                source,
                MemoryWriteRequest.builder()
                        .userId(current.userId())
                        .conversationId(current.conversationId())
                        .messageId(reviewMessageId(current))
                        .message(ChatMessage.user(content))
                        .build()));
        if (result == null || result.status() != MemoryIngestionStatus.ACCEPTED) {
            String reason = result == null ? "empty_result" : result.reason();
            throw new IllegalStateException("review ingestion was not accepted: " + reason);
        }
        MemoryReviewRecord reviewed = reviewRepositoryPort.applyReviewDecision(new MemoryReviewDecision(
                current.candidateId(),
                MemoryReviewStatus.APPLIED,
                operator(command.reviewerId()),
                command.comment(),
                content,
                command.correctedMetadata(),
                operationId,
                current.targetLayer()));
        recordFeedback(current, reviewed);
        return reviewed;
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
}
