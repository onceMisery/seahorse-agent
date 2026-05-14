package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineRetryCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;

import java.util.Objects;

/**
 * 元数据隔离区管理服务。
 */
public class KernelMetadataQuarantineService implements MetadataQuarantineInboundPort {

    private static final String DEFAULT_OPERATOR = "system";
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort;
    private final int maxRetryCount;

    public KernelMetadataQuarantineService(MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort) {
        this(quarantineRepositoryPort, DEFAULT_MAX_RETRY_COUNT);
    }

    public KernelMetadataQuarantineService(MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort,
                                           int maxRetryCount) {
        this.quarantineRepositoryPort = Objects.requireNonNullElse(quarantineRepositoryPort,
                MetadataQuarantineManagementRepositoryPort.empty());
        this.maxRetryCount = maxRetryCount <= 0 ? DEFAULT_MAX_RETRY_COUNT : maxRetryCount;
    }

    @Override
    public MetadataQuarantinePage page(String tenantId,
                                       String knowledgeBaseId,
                                       Boolean resolved,
                                       long current,
                                       long size) {
        requireText(tenantId, "tenantId must not be blank");
        return quarantineRepositoryPort.pageQuarantineItems(
                new MetadataQuarantineQuery(tenantId, knowledgeBaseId, resolved, current, size));
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
        return quarantineRepositoryPort.resolveQuarantineItem(
                new MetadataQuarantineResolution(itemId, operator(operator)));
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
        return quarantineRepositoryPort.scheduleQuarantineRetry(new MetadataQuarantineRetry(
                itemId,
                operator(safeCommand.operator()),
                safeCommand.nextRetryTime()));
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
