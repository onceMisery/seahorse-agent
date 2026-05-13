package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 未处理隔离项的原因聚合。
 */
public record MetadataQuarantineReasonCount(
        String reasonCode,
        String reasonMessage,
        int count
) {

    public MetadataQuarantineReasonCount {
        reasonCode = Objects.requireNonNullElse(reasonCode, "");
        reasonMessage = Objects.requireNonNullElse(reasonMessage, "");
        count = Math.max(0, count);
    }
}
