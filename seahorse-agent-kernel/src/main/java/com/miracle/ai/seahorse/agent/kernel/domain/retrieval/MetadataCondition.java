package com.miracle.ai.seahorse.agent.kernel.domain.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;

import java.util.Objects;

/**
 * 业务元数据过滤条件。
 * <p>
 * fieldKey 是 Schema 中注册的逻辑字段名，不能是后端 JSON 路径或 SQL 片段。
 */
public record MetadataCondition(
        String fieldKey,
        MetadataOperator operator,
        Object value
) {

    public MetadataCondition {
        fieldKey = requireText(fieldKey, "fieldKey");
        operator = Objects.requireNonNullElse(operator, MetadataOperator.EQ);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
