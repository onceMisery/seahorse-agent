package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractRequest;
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
    private final MetadataIndexCompensationPort indexCompensationPort;
    private final MetadataReviewReExtractPort reExtractPort;

    public KernelMetadataReviewService(MetadataReviewManagementRepositoryPort reviewRepositoryPort,
                                       MetadataCanonicalWritePort canonicalWritePort,
                                       MetadataQuarantinePort quarantinePort) {
        this(reviewRepositoryPort, canonicalWritePort, quarantinePort, MetadataIndexCompensationPort.noop());
    }

    public KernelMetadataReviewService(MetadataReviewManagementRepositoryPort reviewRepositoryPort,
                                       MetadataCanonicalWritePort canonicalWritePort,
                                       MetadataQuarantinePort quarantinePort,
                                       MetadataIndexCompensationPort indexCompensationPort) {
        this(reviewRepositoryPort, canonicalWritePort, quarantinePort, indexCompensationPort,
                MetadataReviewReExtractPort.noop());
    }

    public KernelMetadataReviewService(MetadataReviewManagementRepositoryPort reviewRepositoryPort,
                                       MetadataCanonicalWritePort canonicalWritePort,
                                       MetadataQuarantinePort quarantinePort,
                                       MetadataIndexCompensationPort indexCompensationPort,
                                       MetadataReviewReExtractPort reExtractPort) {
        this.reviewRepositoryPort = Objects.requireNonNullElse(reviewRepositoryPort,
                MetadataReviewManagementRepositoryPort.empty());
        this.canonicalWritePort = Objects.requireNonNullElse(canonicalWritePort, MetadataCanonicalWritePort.noop());
        this.quarantinePort = Objects.requireNonNullElse(quarantinePort, MetadataQuarantinePort.noop());
        this.indexCompensationPort = Objects.requireNonNullElse(indexCompensationPort,
                MetadataIndexCompensationPort.noop());
        this.reExtractPort = Objects.requireNonNullElse(reExtractPort, MetadataReviewReExtractPort.noop());
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
        requestIndexCompensation(updated);
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
        requestIndexCompensation(updated);
        return updated;
    }

    @Override
    public MetadataReviewRecord ignoreField(String itemId, MetadataReviewDecisionCommand command) {
        MetadataReviewDecisionCommand safeCommand = safeCommand(command);
        if (safeCommand.ignoredFields().isEmpty()) {
            throw new IllegalArgumentException("ignoredFields must not be empty");
        }
        MetadataReviewRecord current = queryById(itemId);
        Map<String, Object> remainingMetadata = ignoreFields(current.suggestedMetadata(), safeCommand);
        if (remainingMetadata.isEmpty()) {
            throw new IllegalArgumentException("ignoredFields must not remove all suggested metadata");
        }
        // IGNORE_FIELD 是字段级修正动作，底层复用 CORRECTED 终态，审计和 approved_metadata 保存剩余字段。
        MetadataReviewRecord updated = applyDecision(current, MetadataReviewStatus.CORRECTED, safeCommand,
                remainingMetadata);
        writeCanonical(updated.documentId(), remainingMetadata);
        requestIndexCompensation(updated);
        return updated;
    }

    @Override
    public MetadataReviewRecord reExtract(String itemId, MetadataReviewDecisionCommand command) {
        MetadataReviewDecisionCommand safeCommand = safeCommand(command);
        requireText(safeCommand.extractorVersion(), "extractorVersion must not be blank");
        MetadataReviewRecord current = queryById(itemId);
        String jobId = reExtractPort.requestReExtract(new MetadataReviewReExtractRequest(
                current.tenantId(),
                current.knowledgeBaseId(),
                current.documentId(),
                current.id(),
                safeCommand.extractorVersion(),
                safeCommand.pipelineId(),
                operator(safeCommand.reviewerId())));
        Map<String, Object> decisionMetadata = new LinkedHashMap<>();
        decisionMetadata.put("reExtractJobId", jobId);
        decisionMetadata.put("extractorVersion", safeCommand.extractorVersion());
        decisionMetadata.put("pipelineId", safeCommand.pipelineId());
        decisionMetadata.put("documentId", current.documentId());
        // RE_EXTRACT 只调度重抽取任务，不写 canonical metadata；新结果由回填流水线重新产出。
        return applyDecision(current, MetadataReviewStatus.RE_EXTRACTING, safeCommand, decisionMetadata);
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

    private Map<String, Object> ignoreFields(Map<String, Object> suggestedMetadata,
                                             MetadataReviewDecisionCommand command) {
        Map<String, Object> remainingMetadata = new LinkedHashMap<>(Objects.requireNonNullElse(suggestedMetadata,
                Map.of()));
        for (String fieldKey : command.ignoredFields()) {
            if (fieldKey != null && !fieldKey.isBlank()) {
                remainingMetadata.remove(fieldKey.trim());
            }
        }
        if (remainingMetadata.size() == Objects.requireNonNullElse(suggestedMetadata, Map.of()).size()) {
            throw new IllegalArgumentException("ignoredFields must match suggested metadata");
        }
        return remainingMetadata;
    }

    private void requestIndexCompensation(MetadataReviewRecord record) {
        if (record == null) {
            return;
        }
        String documentId = record.documentId();
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        try {
            indexCompensationPort.rebuildDocument(documentId);
        } catch (RuntimeException ex) {
            quarantineIndexFailure(record, ex);
        }
    }

    private void quarantineIndexFailure(MetadataReviewRecord record, RuntimeException ex) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reviewItemId", record.id());
        snapshot.put("reviewStatus", record.reviewStatus().name());
        snapshot.put("resultId", record.resultId());
        snapshot.put("errorType", ex.getClass().getName());
        snapshot.put("errorMessage", Objects.requireNonNullElse(ex.getMessage(), ""));
        try {
            quarantinePort.quarantine(new MetadataQuarantineItem(
                    record.tenantId(),
                    record.knowledgeBaseId(),
                    record.documentId(),
                    record.resultId(),
                    "INDEX",
                    "METADATA_INDEX_COMPENSATION_FAILED",
                    firstText(ex.getMessage(), "元数据复核后的索引补偿失败"),
                    snapshot));
        } catch (RuntimeException ignored) {
            // 隔离写入失败也不能覆盖已经完成的复核决策和 canonical metadata 写回。
        }
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
