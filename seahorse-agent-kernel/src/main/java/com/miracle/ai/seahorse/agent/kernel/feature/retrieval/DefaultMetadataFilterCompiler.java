package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.MetadataCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldContains;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldExists;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldIn;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldRange;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.MetadataFilterExpr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 默认元数据过滤编译器。
 * <p>
 * 该实现只接受已注册且 filterable 的字段；不能下推到向量后端的字段会进入 guard-only。
 */
public class DefaultMetadataFilterCompiler implements MetadataFilterCompiler {

    @Override
    public CompiledMetadataFilter compile(RetrievalFilter filter, MetadataSchema schema) {
        RetrievalFilter safeFilter = Objects.requireNonNullElseGet(filter, RetrievalFilter::empty);
        MetadataSchema safeSchema = Objects.requireNonNullElseGet(schema,
                () -> MetadataSchema.empty("", ""));
        List<MetadataFilterExpr> expressions = new ArrayList<>();
        List<MetadataCondition> guardOnlyConditions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (MetadataCondition condition : safeFilter.metadataConditions()) {
            MetadataFieldDescriptor field = safeSchema.find(condition.fieldKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "metadata filter field is not registered: " + condition.fieldKey()));
            validate(field, condition);
            Object normalizedValue = normalizeValue(field, condition.value(), condition.operator());
            if (field.backendMapping().guardOnly() || !field.backendMapping().pushdownToVector()) {
                guardOnlyConditions.add(new MetadataCondition(field.fieldKey(), condition.operator(), normalizedValue));
                warnings.add("metadata filter is guard-only: " + field.fieldKey());
                continue;
            }
            expressions.add(toExpression(field, condition.operator(), normalizedValue));
        }

        return new CompiledMetadataFilter(safeFilter, new FilterAnd(expressions), guardOnlyConditions, warnings);
    }

    private void validate(MetadataFieldDescriptor field, MetadataCondition condition) {
        if (!field.filterable()) {
            throw new IllegalArgumentException("metadata field is not filterable: " + field.fieldKey());
        }
        if (!field.allowedOperators().isEmpty() && !field.allowedOperators().contains(condition.operator())) {
            throw new IllegalArgumentException("metadata operator is not allowed: " + condition.operator());
        }
    }

    private MetadataFilterExpr toExpression(MetadataFieldDescriptor field, MetadataOperator operator, Object value) {
        return switch (operator) {
            case EQ, NE -> new FieldEq(field, value);
            case IN -> new FieldIn(field, value instanceof List<?> list ? list : List.of(value));
            case RANGE -> {
                List<?> range = value instanceof List<?> list ? list : List.of();
                Object from = range.isEmpty() ? null : range.get(0);
                Object to = range.size() < 2 ? null : range.get(1);
                yield new FieldRange(field, from, to);
            }
            case CONTAINS -> new FieldContains(field, value);
            case EXISTS -> new FieldExists(field);
        };
    }

    private Object normalizeValue(MetadataFieldDescriptor field, Object value, MetadataOperator operator) {
        if (operator == MetadataOperator.EXISTS) {
            return Boolean.TRUE;
        }
        if (operator == MetadataOperator.IN) {
            if (!(value instanceof Collection<?> collection)) {
                throw new IllegalArgumentException("IN operator requires collection value: " + field.fieldKey());
            }
            return collection.stream()
                    .map(item -> normalizeScalar(field, item))
                    .toList();
        }
        if (operator == MetadataOperator.RANGE) {
            if (!(value instanceof Collection<?> collection)) {
                throw new IllegalArgumentException("RANGE operator requires collection value: " + field.fieldKey());
            }
            List<?> values = collection.stream().toList();
            if (values.isEmpty() || values.size() > 2) {
                throw new IllegalArgumentException("RANGE operator requires one or two boundary values: "
                        + field.fieldKey());
            }
            return values.stream()
                    .map(item -> normalizeScalar(field, item))
                    .toList();
        }
        return normalizeScalar(field, value);
    }

    private Object normalizeScalar(MetadataFieldDescriptor field, Object value) {
        if (value == null) {
            return null;
        }
        MetadataValueType valueType = field.valueType();
        return switch (valueType) {
            case STRING, STRING_ARRAY, ENUM -> Objects.toString(value, "").trim();
            case NUMBER, NUMBER_ARRAY -> value instanceof Number number
                    ? BigDecimal.valueOf(number.doubleValue())
                    : new BigDecimal(Objects.toString(value).trim());
            case BOOLEAN -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(Objects.toString(value));
            case DATE_TIME -> normalizeInstant(value);
        };
    }

    private String normalizeInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        String text = Objects.toString(value, "").trim();
        try {
            return Instant.parse(text).toString();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid DATE_TIME metadata filter value: " + text, ex);
        }
    }
}
