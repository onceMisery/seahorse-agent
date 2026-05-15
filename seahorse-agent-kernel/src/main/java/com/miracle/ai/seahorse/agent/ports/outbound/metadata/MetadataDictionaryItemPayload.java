package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 元数据标准化字典项写入载荷。
 */
public record MetadataDictionaryItemPayload(
        String tenantId,
        String dictionaryCode,
        String rawValue,
        String canonicalValue,
        String displayName,
        boolean enabled
) {

    public MetadataDictionaryItemPayload {
        tenantId = Objects.requireNonNullElse(tenantId, "").trim();
        dictionaryCode = Objects.requireNonNullElse(dictionaryCode, "").trim();
        rawValue = Objects.requireNonNullElse(rawValue, "").trim();
        canonicalValue = Objects.requireNonNullElse(canonicalValue, "").trim();
        displayName = Objects.requireNonNullElse(displayName, canonicalValue).trim();
    }
}
