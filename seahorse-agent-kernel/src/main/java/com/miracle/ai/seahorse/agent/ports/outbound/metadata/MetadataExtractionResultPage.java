package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Objects;

/**
 * 元数据抽取结果分页。
 */
public record MetadataExtractionResultPage(
        List<MetadataExtractionResultRecord> records,
        long total,
        long size,
        long current,
        long pages
) {

    public MetadataExtractionResultPage {
        records = List.copyOf(Objects.requireNonNullElse(records, List.of()));
        total = Math.max(0L, total);
        size = Math.max(1L, size);
        current = Math.max(1L, current);
        pages = Math.max(0L, pages);
    }

    public static MetadataExtractionResultPage empty(long current, long size) {
        return new MetadataExtractionResultPage(List.of(), 0, size, current, 0);
    }
}
