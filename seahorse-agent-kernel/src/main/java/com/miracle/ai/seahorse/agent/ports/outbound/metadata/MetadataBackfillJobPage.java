package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Objects;

/**
 * 元数据回填任务分页结果，字段命名保持与前端常用分页结构一致。
 */
public record MetadataBackfillJobPage(
        List<MetadataBackfillJobRecord> records,
        long total,
        long size,
        long current,
        long pages
) {

    public MetadataBackfillJobPage {
        records = List.copyOf(Objects.requireNonNullElse(records, List.of()));
        total = Math.max(0L, total);
        size = Math.max(1L, size);
        current = Math.max(1L, current);
        pages = Math.max(0L, pages);
    }

    public static MetadataBackfillJobPage empty(long current, long size) {
        return new MetadataBackfillJobPage(List.of(), 0, size, current, 0);
    }
}
