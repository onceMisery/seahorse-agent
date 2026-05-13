package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据复核管理服务。
 *
 * <p>服务只编排治理动作：APPROVE/CORRECT 会把可信元数据写回文档 canonical metadata；
 * QUARANTINE 只写隔离项，避免未通过复核的数据进入索引链路。
 */
public class KernelMetadataReviewService implements MetadataReviewInboundPort {

    private static final String DEFAULT_OPERATOR = "system";

    private final MetadataReviewManagementRepositoryPort reviewRepositoryPort;
    private final MetadataCanonicalWritePort canonicalWritePort;
    private final MetadataQuarantinePort quarantinePort;

    public KernelMetadataReviewService(MetadataReviewManagementRepositoryPort reviewRepositoryPort,
                                       MetadataCanonicalWritePort canonicalWritePort,
                                       MetadataQuarantinePort quarantinePort) {
        this.reviewRepositoryPort = Objects.requireNonNullElse(reviewRepositoryPort,
                MetadataReviewManagementRepositoryPort.empty());
        this.canonicalWritePort = Objects.requireNonNullElse(canonicalWritePort, MetadataCanonicalWritePort.noop());
        this.quarantinePort = Objects.requireNonNullElse(quarantinePort, MetadataQuarantinePort.noop());
    }

    @Override
    public MetadataReviewPage page(String tenantId,
                                   String knowledgeBaseId,
                                   MetadataReviewStatus status,
                                   long current,
                                   long size) {
        requireText(tenantId, "tenantId must not be blank");
        return reviewRepositoryPort.pageReviewItems(
                new MetadataReviewQuery(tenantId, knowledgeBaseId, status, current, size));
    }

    @Override
    public MetadataReviewRecord queryById(String itemId) {
        requireText(itemId, "itemId must not be blank");
        return reviewRepositoryPort.findReviewItem(itemId)
                .orElseThrow(() -> new IllegalArgumentException("元数据复核项不存在: " + itemId));
    }

    @Override
    public MetadataReviewRecord approve(String itemId, MetadataReviewDecisionCommand command) {
        MetadataReviewRecord current = queryById(itemId);
        MetadataReviewRecord updated = applyDecision(current, MetadataReviewStatus.APPROVED, command,
                current.suggestedMetadata());
        writeCanonical(updated.documentId(), current.suggestedMetadata());
        return updated;
    }

    @Override
    public MetadataReviewRecord correct(String itemId, MetadataReviewDecisionCommand command) {
        MetadataReviewDecisionCommand safeCommand = safeCommand(command);
        if (safeCommand.correctedMetadata().isEmpty()) {
            throw new IllegalArgumentException("correctedMetadata must not be empty");
        }
        MetadataReviewRecord current = queryById(itemId);
        MetadataReviewRecord updated = applyDecision(current, MetadataReviewStatus.CORRECTED, safeCommand,
                safeCommand.correctedMetadata());
        writeCanonical(updated.documentId(), safeCommand.correctedMetadata());
        return updated;
    }

    @Override
    public MetadataReviewRecord reject(String itemId, MetadataReviewDecisionCommand command) {
        MetadataReviewRecord current = queryById(itemId);
        return applyDecision(current, MetadataReviewStatus.REJECTED, command, Map.of());
    }

    @Override
    public MetadataReviewRecord quarantine(String itemId, MetadataReviewDecisionCommand command) {
        MetadataReviewRecord current = queryById(itemId);
        MetadataReviewRecord updated = applyDecision(current, MetadataReviewStatus.QUARANTINED, command, Map.of());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reviewItemId", updated.id());
        snapshot.put("suggestedMetadata", current.suggestedMetadata());
        snapshot.put("correctedMetadata", updated.correctedMetadata());
        snapshot.put("reviewComment", updated.reviewComment());
        quarantinePort.quarantine(new MetadataQuarantineItem(
                updated.tenantId(),
                updated.knowledgeBaseId(),
                updated.documentId(),
                updated.resultId(),
                "REVIEW",
                "REVIEW_QUARANTINED",
                firstText(updated.reviewComment(), updated.reasonMessage()),
                snapshot));
        return updated;
    }

    private MetadataReviewRecord applyDecision(MetadataReviewRecord current,
                                               MetadataReviewStatus status,
                                               MetadataReviewDecisionCommand command,
                                               Map<String, Object> correctedMetadata) {
        MetadataReviewDecisionCommand safeCommand = safeCommand(command);
        return reviewRepositoryPort.applyReviewDecision(new MetadataReviewDecision(
                current.id(),
                status,
                operator(safeCommand.reviewerId()),
                safeCommand.comment(),
                correctedMetadata));
    }

    private void writeCanonical(String documentId, Map<String, Object> metadata) {
        if (documentId == null || documentId.isBlank() || metadata == null || metadata.isEmpty()) {
            return;
        }
        canonicalWritePort.writeDocumentMetadata(documentId, metadata);
    }

    private MetadataReviewDecisionCommand safeCommand(MetadataReviewDecisionCommand command) {
        return command == null ? new MetadataReviewDecisionCommand(DEFAULT_OPERATOR, "", Map.of()) : command;
    }

    private String operator(String operator) {
        return operator == null || operator.isBlank() ? DEFAULT_OPERATOR : operator.trim();
    }

    private String firstText(String first, String second) {
        return first == null || first.isBlank() ? Objects.requireNonNullElse(second, "") : first;
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
