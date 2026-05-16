package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 回填运维视图中的通用计数项。
 */
public record MetadataBackfillCountItem(
        String key,
        long count
) {

    public MetadataBackfillCountItem {
        key = Objects.requireNonNullElse(key, "");
        count = Math.max(0L, count);
    }
}
