package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.Objects;

/**
 * 元数据标准化字典项管理记录。
 */
public record MetadataDictionaryItemRecord(
        String id,
        String tenantId,
        String dictionaryCode,
        String rawValue,
        String canonicalValue,
        String displayName,
        boolean enabled,
        Instant createTime,
        Instant updateTime
) {

    public MetadataDictionaryItemRecord {
        id = Objects.requireNonNullElse(id, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        dictionaryCode = Objects.requireNonNullElse(dictionaryCode, "");
        rawValue = Objects.requireNonNullElse(rawValue, "");
        canonicalValue = Objects.requireNonNullElse(canonicalValue, "");
        displayName = Objects.requireNonNullElse(displayName, canonicalValue);
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
        updateTime = Objects.requireNonNullElseGet(updateTime, Instant::now);
    }
}
