package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Objects;

/**
 * 隔离项分页结果，字段名兼容前端常用 IPage 结构。
 */
public record MetadataQuarantinePage(
        List<MetadataQuarantineRecord> records,
        long total,
        long size,
        long current,
        long pages
) {

    public MetadataQuarantinePage {
        records = List.copyOf(Objects.requireNonNullElse(records, List.of()));
        total = Math.max(0L, total);
        size = Math.max(1L, size);
        current = Math.max(1L, current);
        pages = Math.max(0L, pages);
    }

    public static MetadataQuarantinePage empty(long current, long size) {
        return new MetadataQuarantinePage(List.of(), 0, size, current, 0);
    }
}
