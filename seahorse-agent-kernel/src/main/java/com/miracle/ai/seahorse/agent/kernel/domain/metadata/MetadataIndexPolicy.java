package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

public enum MetadataIndexPolicy {
    NONE,
    JSON_GIN,
    EXPRESSION_INDEX,
    SEARCH_KEYWORD,
    SEARCH_TEXT,
    MILVUS_JSON,
    MILVUS_SCALAR
}
