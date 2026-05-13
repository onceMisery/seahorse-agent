package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.MetadataCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldContains;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldExists;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldIn;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldRange;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.MetadataFilterExpr;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据兜底过滤后处理器。
 * <p>
 * 向量库或关键词后端即使已经做了下推过滤，这里仍会按系统字段和 guard-only 条件再过滤一次。
 */
public class MetadataGuardPostProcessorFeature implements SearchResultPostProcessorFeature {

    private static final String NAME = "MetadataGuard";
    private static final int ORDER = 50;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean enabled(SearchContext context) {
        return context != null
                && (context.getFilter() != null || hasCompiledFilter(context.getCompiledFilter()));
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        RetrievalFilter filter = effectiveFilter(context);
        CompiledMetadataFilter compiledFilter = context == null ? CompiledMetadataFilter.empty()
                : Objects.requireNonNullElseGet(context.getCompiledFilter(), CompiledMetadataFilter::empty);
        return chunks.stream()
                .filter(chunk -> passesSystemFilter(chunk, filter.system()))
                .filter(chunk -> passesExpression(chunk, compiledFilter.expression()))
                .filter(chunk -> passesGuardOnlyConditions(chunk, compiledFilter.guardOnlyConditions()))
                .toList();
    }

    private RetrievalFilter effectiveFilter(SearchContext context) {
        if (context == null) {
            return RetrievalFilter.empty();
        }
        if (context.getFilter() != null) {
            return context.getFilter();
        }
        CompiledMetadataFilter compiledFilter = context.getCompiledFilter();
        if (compiledFilter != null) {
            return compiledFilter.sourceFilter();
        }
        return RetrievalFilter.empty();
    }

    private boolean hasCompiledFilter(CompiledMetadataFilter filter) {
        return filter != null && (filter.hasExpression() || !filter.guardOnlyConditions().isEmpty());
    }

    private boolean passesSystemFilter(RetrievedChunk chunk, SystemRetrievalFilter filter) {
        if (chunk == null || filter == null) {
            return true;
        }
        return matchesText(filter.tenantId(), value(chunk.getTenantId(), chunk.getMetadata(), "tenant_id"))
                && matchesAny(filter.knowledgeBaseIds(), value(chunk.getKbId(), chunk.getMetadata(), "kb_id"))
                && matchesAny(filter.collectionNames(), value(chunk.getCollectionName(), chunk.getMetadata(), "collection_name"))
                && matchesAny(filter.documentIds(), value(chunk.getDocId(), chunk.getMetadata(), "doc_id"))
                && matchesAny(filter.fileTypes(), metadataValue(chunk.getMetadata(), "file_type"))
                && matchesAny(filter.sourceTypes(), metadataValue(chunk.getMetadata(), "source_type"))
                && matchesAcl(filter.aclSubjectIds(), chunk.getMetadata())
                && matchesEnabled(filter.enabledOnly(), chunk.getMetadata());
    }

    private boolean passesExpression(RetrievedChunk chunk, MetadataFilterExpr expression) {
        if (expression == null) {
            return true;
        }
        // 兜底过滤只消费编译后的 AST，避免把用户原始字段名直接解释成后端查询语义。
        if (expression instanceof FilterAnd filterAnd) {
            return filterAnd.children().stream().allMatch(child -> passesExpression(chunk, child));
        }
        if (expression instanceof FieldEq fieldEq) {
            return compareValues(metadataValue(chunk.getMetadata(), fieldEq.field().backendMapping().canonicalName()),
                    fieldEq.value()) == 0;
        }
        if (expression instanceof FieldIn fieldIn) {
            Object actual = metadataValue(chunk.getMetadata(), fieldIn.field().backendMapping().canonicalName());
            return fieldIn.values().stream().anyMatch(expected -> compareValues(actual, expected) == 0);
        }
        if (expression instanceof FieldRange fieldRange) {
            Object actual = metadataValue(chunk.getMetadata(), fieldRange.field().backendMapping().canonicalName());
            return withinRange(actual, fieldRange.from(), fieldRange.to());
        }
        if (expression instanceof FieldContains fieldContains) {
            Object actual = metadataValue(chunk.getMetadata(), fieldContains.field().backendMapping().canonicalName());
            return containsValue(actual, fieldContains.value());
        }
        if (expression instanceof FieldExists fieldExists) {
            return metadataValue(chunk.getMetadata(), fieldExists.field().backendMapping().canonicalName()) != null;
        }
        return true;
    }

    private boolean passesGuardOnlyConditions(RetrievedChunk chunk, List<MetadataCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        return conditions.stream().allMatch(condition -> passesCondition(chunk, condition));
    }

    private boolean passesCondition(RetrievedChunk chunk, MetadataCondition condition) {
        Object actual = metadataValue(chunk.getMetadata(), condition.fieldKey());
        MetadataOperator operator = condition.operator();
        return switch (operator) {
            case EQ -> compareValues(actual, condition.value()) == 0;
            case NE -> compareValues(actual, condition.value()) != 0;
            case IN -> condition.value() instanceof Collection<?> collection
                    && collection.stream().anyMatch(value -> compareValues(actual, value) == 0);
            case RANGE -> condition.value() instanceof List<?> range
                    && withinRange(actual, range.isEmpty() ? null : range.get(0), range.size() > 1 ? range.get(1) : null);
            case CONTAINS -> containsValue(actual, condition.value());
            case EXISTS -> actual != null;
        };
    }

    private boolean matchesText(String expected, Object actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(Objects.toString(actual, ""));
    }

    private boolean matchesAny(List<String> expectedValues, Object actual) {
        if (expectedValues == null || expectedValues.isEmpty()) {
            return true;
        }
        String actualText = Objects.toString(actual, "");
        return expectedValues.contains(actualText);
    }

    private boolean matchesAcl(List<String> aclSubjectIds, Map<String, Object> metadata) {
        if (aclSubjectIds == null || aclSubjectIds.isEmpty()) {
            return true;
        }
        Object actual = metadataValue(metadata, "acl_subjects");
        if (actual instanceof Collection<?> collection) {
            return collection.stream().map(Objects::toString).anyMatch(aclSubjectIds::contains);
        }
        return aclSubjectIds.contains(Objects.toString(actual, ""));
    }

    private boolean matchesEnabled(boolean enabledOnly, Map<String, Object> metadata) {
        if (!enabledOnly) {
            return true;
        }
        Object enabled = metadataValue(metadata, "enabled");
        return enabled == null || Boolean.parseBoolean(Objects.toString(enabled));
    }

    private Object value(Object preferred, Map<String, Object> metadata, String key) {
        if (preferred != null) {
            return preferred;
        }
        return metadataValue(metadata, key);
    }

    private Object metadataValue(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null || key.isBlank()) {
            return null;
        }
        return metadata.get(key);
    }

    private boolean withinRange(Object actual, Object from, Object to) {
        if (actual == null) {
            return false;
        }
        return (from == null || compareValues(actual, from) >= 0)
                && (to == null || compareValues(actual, to) <= 0);
    }

    private boolean containsValue(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> compareValues(item, expected) == 0);
        }
        return Objects.toString(actual, "").contains(Objects.toString(expected, ""));
    }

    private int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null || right == null) {
            return -1;
        }
        BigDecimal leftNumber = number(left);
        BigDecimal rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        return Objects.toString(left).compareTo(Objects.toString(right));
    }

    private BigDecimal number(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(Objects.toString(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
