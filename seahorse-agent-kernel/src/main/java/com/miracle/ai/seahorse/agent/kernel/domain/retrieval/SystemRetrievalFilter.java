package com.miracle.ai.seahorse.agent.kernel.domain.retrieval;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 系统级检索过滤条件。
 * <p>
 * 这里承载租户、知识库、文档、ACL 等安全边界字段，不允许业务动态字段覆盖。
 */
@Builder
public record SystemRetrievalFilter(
        String tenantId,
        String userId,
        List<String> knowledgeBaseIds,
        List<String> collectionNames,
        List<String> documentIds,
        List<String> aclSubjectIds,
        List<String> fileTypes,
        List<String> sourceTypes,
        Instant createdFrom,
        Instant createdTo,
        Instant updatedFrom,
        Instant updatedTo,
        boolean enabledOnly
) {

    public SystemRetrievalFilter {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        userId = Objects.requireNonNullElse(userId, "");
        knowledgeBaseIds = copyTextList(knowledgeBaseIds);
        collectionNames = copyTextList(collectionNames);
        documentIds = copyTextList(documentIds);
        aclSubjectIds = copyTextList(aclSubjectIds);
        fileTypes = copyTextList(fileTypes);
        sourceTypes = copyTextList(sourceTypes);
    }

    public static SystemRetrievalFilter defaults() {
        return SystemRetrievalFilter.builder().enabledOnly(true).build();
    }

    private static List<String> copyTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
