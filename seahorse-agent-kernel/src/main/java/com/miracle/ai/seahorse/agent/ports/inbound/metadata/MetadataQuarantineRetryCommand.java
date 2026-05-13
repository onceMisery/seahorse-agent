package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import java.time.Instant;
import java.util.Objects;

/**
 * 隔离项重试调度入站命令。
 */
public record MetadataQuarantineRetryCommand(String operator, Instant nextRetryTime) {

    public MetadataQuarantineRetryCommand {
        operator = Objects.requireNonNullElse(operator, "");
        nextRetryTime = Objects.requireNonNullElseGet(nextRetryTime, Instant::now);
    }
}
