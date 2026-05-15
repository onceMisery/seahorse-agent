package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineRetryCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据隔离区管理服务。
 */
public class KernelMetadataQuarantineService implements MetadataQuarantineInboundPort {

    private static final String DEFAULT_OPERATOR = "system";
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final String EVENT_QUARANTINE_ACTION_COMPLETED = "metadata.quarantine.action.completed";

    private final MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort;
    private final int maxRetryCount;
    private final ObservationPort observationPort;

    public KernelMetadataQuarantineService(MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort) {
        this(quarantineRepositoryPort, DEFAULT_MAX_RETRY_COUNT);
    }

    public KernelMetadataQuarantineService(MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort,
                                           int maxRetryCount) {
        this(quarantineRepositoryPort, maxRetryCount, null);
    }

    public KernelMetadataQuarantineService(MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort,
                                           int maxRetryCount,
                                           ObservationPort observationPort) {
        this.quarantineRepositoryPort = Objects.requireNonNullElse(quarantineRepositoryPort,
                MetadataQuarantineManagementRepositoryPort.empty());
        this.maxRetryCount = maxRetryCount <= 0 ? DEFAULT_MAX_RETRY_COUNT : maxRetryCount;
        this.observationPort = observationPort;
    }

    @Override
    public MetadataQuarantinePage page(String tenantId,
                                       String knowledgeBaseId,
                                       Boolean resolved,
                                       long current,
                                       long size) {
        return page(tenantId, knowledgeBaseId, resolved, null, null, null, null, current, size);
    }

    @Override
    public MetadataQuarantinePage page(String tenantId,
                                       String knowledgeBaseId,
                                       Boolean resolved,
                                       String stage,
                                       String reasonCode,
                                       String documentId,
                                       String jobId,
                                       long current,
                                       long size) {
        requireText(tenantId, "tenantId must not be blank");
        return quarantineRepositoryPort.pageQuarantineItems(
                new MetadataQuarantineQuery(
                        tenantId, knowledgeBaseId, resolved, stage, reasonCode, documentId, jobId, current, size));
    }

    @Override
    public MetadataQuarantineRecord queryById(String itemId) {
        requireText(itemId, "itemId must not be blank");
        return quarantineRepositoryPort.findQuarantineItem(itemId)
                .orElseThrow(() -> new IllegalArgumentException("元数据隔离项不存在: " + itemId));
    }

    @Override
    public MetadataQuarantineRecord resolve(String itemId, String operator) {
        queryById(itemId);
        MetadataQuarantineRecord updated = quarantineRepositoryPort.resolveQuarantineItem(
                new MetadataQuarantineResolution(itemId, operator(operator)));
        recordQuarantineAction(updated, "RESOLVE");
        return updated;
    }

    @Override
    public MetadataQuarantineRecord retry(String itemId, MetadataQuarantineRetryCommand command) {
        MetadataQuarantineRecord current = queryById(itemId);
        if (current.retryCount() >= maxRetryCount) {
            // 隔离区重试必须有上限，避免不可恢复数据反复污染回填队列。
            throw new IllegalStateException("元数据隔离项已达到最大重试次数: " + itemId);
        }
        MetadataQuarantineRetryCommand safeCommand = command == null
                ? new MetadataQuarantineRetryCommand(DEFAULT_OPERATOR, null)
                : command;
        MetadataQuarantineRecord updated = quarantineRepositoryPort.scheduleQuarantineRetry(new MetadataQuarantineRetry(
                itemId,
                operator(safeCommand.operator()),
                safeCommand.nextRetryTime()));
        recordQuarantineAction(updated, "RETRY");
        return updated;
    }

    private void recordQuarantineAction(MetadataQuarantineRecord record, String action) {
        if (observationPort == null || record == null) {
            return;
        }
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tenantId", record.tenantId());
            attributes.put("knowledgeBaseId", record.knowledgeBaseId());
            attributes.put("action", Objects.requireNonNullElse(action, ""));
            attributes.put("stage", record.stage());
            attributes.put("reasonCode", record.reasonCode());
            attributes.put("retryCount", Integer.toString(record.retryCount()));
            attributes.put("maxRetryCount", Integer.toString(maxRetryCount));
            attributes.put("resolved", Boolean.toString(record.resolved()));
            attributes.put("nextRetryScheduled", Boolean.toString(record.nextRetryTime() != null));
            // 隔离区事件记录处理轨迹的指标维度；详细快照仍保留在隔离表中。
            observationPort.recordEvent(new ObservationEvent(EVENT_QUARANTINE_ACTION_COMPLETED, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响隔离项处理或重试调度。
        }
    }

    private String operator(String operator) {
        return operator == null || operator.isBlank() ? DEFAULT_OPERATOR : operator.trim();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
