package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 隔离项人工处理命令。
 */
public record MetadataQuarantineResolution(String itemId, String operator) {

    public MetadataQuarantineResolution {
        itemId = Objects.requireNonNullElse(itemId, "");
        operator = Objects.requireNonNullElse(operator, "");
    }
}
