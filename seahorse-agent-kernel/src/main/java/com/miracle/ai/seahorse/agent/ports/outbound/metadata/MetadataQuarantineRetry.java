package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.Objects;

/**
 * 隔离项重试调度命令。
 */
public record MetadataQuarantineRetry(
        String itemId,
        String operator,
        Instant nextRetryTime
) {

    public MetadataQuarantineRetry {
        itemId = Objects.requireNonNullElse(itemId, "");
        operator = Objects.requireNonNullElse(operator, "");
        nextRetryTime = Objects.requireNonNullElseGet(nextRetryTime, Instant::now);
    }
}
