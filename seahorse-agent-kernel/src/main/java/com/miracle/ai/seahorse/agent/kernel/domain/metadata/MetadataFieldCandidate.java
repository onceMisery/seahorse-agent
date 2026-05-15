package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.Objects;

public record MetadataFieldCandidate(
        String fieldKey,
        Object rawValue,
        String sourceType,
        String extractorName,
        double confidence,
        String evidence,
        int schemaVersion,
        String extractorVersion,
        String promptVersion
) {

    public MetadataFieldCandidate {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        sourceType = Objects.requireNonNullElse(sourceType, "");
        extractorName = Objects.requireNonNullElse(extractorName, "");
        evidence = Objects.requireNonNullElse(evidence, "");
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        promptVersion = Objects.requireNonNullElse(promptVersion, "");
    }

    public MetadataFieldCandidate(String fieldKey,
                                  Object rawValue,
                                  String sourceType,
                                  String extractorName,
                                  double confidence,
                                  String evidence,
                                  int schemaVersion,
                                  String extractorVersion) {
        // 兼容确定性抽取器和旧测试数据：非 LLM 候选没有 prompt 版本。
        this(fieldKey, rawValue, sourceType, extractorName, confidence, evidence, schemaVersion, extractorVersion, "");
    }
}
