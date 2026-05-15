package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldQuality;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MetadataNormalizerNodeFeature implements IngestionNodeFeature {

    public static final String NODE_TYPE = "metadata_normalizer";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_KB_ID = "kbId";
    private static final double PRIORITY_CONFIDENCE_EPSILON = 0.05D;
    private static final double AGREEMENT_CONFIDENCE_BOOST = 0.03D;
    private static final double DEFAULT_MIN_CONFIDENCE = 0.8D;

    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final MetadataDictionaryPort dictionaryPort;

    public MetadataNormalizerNodeFeature(MetadataSchemaRegistryPort schemaRegistryPort,
                                         MetadataDictionaryPort dictionaryPort) {
        this.schemaRegistryPort = Objects.requireNonNullElse(schemaRegistryPort, MetadataSchemaRegistryPort.empty());
        this.dictionaryPort = Objects.requireNonNullElse(dictionaryPort, MetadataDictionaryPort.noop());
    }

    @Override
    public String name() {
        return NODE_TYPE;
    }

    @Override
    public String nodeType() {
        return NODE_TYPE;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        IngestionContext safeContext = Objects.requireNonNull(context, "context must not be null");
        try {
            MetadataSchema schema = resolveSchema(safeContext, config);
            Map<String, Object> normalized = new LinkedHashMap<>();
            List<MetadataFieldQuality> qualities = new ArrayList<>();
            List<MetadataIssue> issues = new ArrayList<>(Objects.requireNonNullElse(safeContext.getMetadataIssues(),
                    List.of()));
            for (MetadataFieldCandidate candidate : bestCandidates(schema, safeContext.getMetadataCandidates(), issues)) {
                MetadataFieldDescriptor field = schema.find(candidate.fieldKey()).orElse(null);
                if (field == null) {
                    issues.add(MetadataIssue.warn(candidate.fieldKey(), NODE_TYPE, "UNREGISTERED_FIELD",
                            "候选字段未注册 Schema"));
                    continue;
                }
                normalizeField(schema, field, candidate, normalized, qualities, issues);
            }
            safeContext.setNormalizedMetadata(normalized);
            safeContext.setMetadataFieldQualities(qualities);
            safeContext.setMetadataIssues(issues);
            return NodeResult.ok("normalized metadata fields=" + normalized.size());
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    private void normalizeField(MetadataSchema schema,
                                MetadataFieldDescriptor field,
                                MetadataFieldCandidate candidate,
                                Map<String, Object> normalized,
                                List<MetadataFieldQuality> qualities,
                                List<MetadataIssue> issues) {
        try {
            Object value = convert(field, candidate.rawValue(), schema.tenantId());
            if (value != null) {
                normalized.put(field.fieldKey(), value);
                qualities.add(new MetadataFieldQuality(field.fieldKey(), candidate.confidence(),
                        candidate.sourceType(), candidate.extractorName(), true, ""));
            }
        } catch (RuntimeException ex) {
            issues.add(MetadataIssue.warn(field.fieldKey(), NODE_TYPE, "NORMALIZE_FAILED", ex.getMessage()));
            qualities.add(new MetadataFieldQuality(field.fieldKey(), candidate.confidence(), candidate.sourceType(),
                    candidate.extractorName(), false, ex.getMessage()));
        }
    }

    private Object convert(MetadataFieldDescriptor field, Object rawValue, String tenantId) {
        if (rawValue == null) {
            return null;
        }
        return switch (field.valueType()) {
            case NUMBER -> number(rawValue);
            case BOOLEAN -> bool(rawValue);
            case DATE_TIME -> dateTime(rawValue);
            case STRING_ARRAY -> stringArray(rawValue, field, tenantId);
            case NUMBER_ARRAY -> numberArray(rawValue);
            case ENUM -> dictionary(field, tenantId, string(rawValue));
            case STRING -> dictionary(field, tenantId, string(rawValue));
        };
    }

    private String dictionary(MetadataFieldDescriptor field, String tenantId, String value) {
        if (!hasText(value)) {
            return null;
        }
        String dictCode = Objects.toString(field.extractionHints().get("dictionaryCode"), "");
        if (!hasText(dictCode)) {
            return value;
        }
        return dictionaryPort.canonicalValue(tenantId, dictCode, value).orElse(value);
    }

    private String string(Object value) {
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal number(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).replace(",", "").trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private Boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return switch (text) {
            case "true", "1", "y", "yes", "是" -> true;
            case "false", "0", "n", "no", "否" -> false;
            default -> throw new IllegalArgumentException("boolean value not recognized: " + value);
        };
    }

    private String dateTime(Object value) {
        String text = string(value);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text).toString();
        } catch (RuntimeException ignored) {
            return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        }
    }

    private List<String> stringArray(Object value, MetadataFieldDescriptor field, String tenantId) {
        List<?> list = value instanceof List<?> values ? values : List.of(String.valueOf(value).split("[,;，；]"));
        return list.stream()
                .filter(Objects::nonNull)
                .map(item -> dictionary(field, tenantId, String.valueOf(item).trim()))
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private List<BigDecimal> numberArray(Object value) {
        List<?> list = value instanceof List<?> values ? values : List.of(String.valueOf(value).split("[,;，；]"));
        return list.stream()
                .filter(Objects::nonNull)
                .map(this::number)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<MetadataFieldCandidate> bestCandidates(MetadataSchema schema,
                                                        List<MetadataFieldCandidate> candidates,
                                                        List<MetadataIssue> issues) {
        Map<String, MetadataFieldCandidate> best = new LinkedHashMap<>();
        for (MetadataFieldCandidate candidate : Objects.requireNonNullElse(candidates, List.<MetadataFieldCandidate>of())) {
            if (candidate == null || !hasText(candidate.fieldKey())) {
                continue;
            }
            best.merge(candidate.fieldKey(), candidate,
                    (left, right) -> {
                        double minConfidence = minConfidence(schema, left.fieldKey());
                        if (Objects.equals(left.rawValue(), right.rawValue())) {
                            return agreedCandidate(left, right, minConfidence);
                        }
                        issues.add(MetadataIssue.warn(left.fieldKey(), NODE_TYPE, "CANDIDATE_CONFLICT",
                                "字段存在多个候选值，已按抽取器优先级和置信度选择主值"));
                        return betterCandidate(left, right, minConfidence);
                    });
        }
        return List.copyOf(best.values());
    }

    private MetadataFieldCandidate agreedCandidate(MetadataFieldCandidate left,
                                                   MetadataFieldCandidate right,
                                                   double minConfidence) {
        MetadataFieldCandidate selected = betterCandidate(left, right, minConfidence);
        double confidence = Math.min(1D, Math.max(left.confidence(), right.confidence()) + AGREEMENT_CONFIDENCE_BOOST);
        // 多抽取器给出相同值时小幅提升置信度，避免单一来源过度放大。
        return new MetadataFieldCandidate(selected.fieldKey(), selected.rawValue(), selected.sourceType(),
                selected.extractorName(), confidence, selected.evidence(), selected.schemaVersion(),
                selected.extractorVersion(), selected.promptVersion());
    }

    private MetadataFieldCandidate betterCandidate(MetadataFieldCandidate left,
                                                   MetadataFieldCandidate right,
                                                   double minConfidence) {
        // LLM 只能补充缺失或低置信字段，不能覆盖已达标的确定性候选。
        if (isLlm(right) && !isLlm(left) && left.confidence() >= minConfidence) {
            return left;
        }
        if (isLlm(left) && !isLlm(right) && right.confidence() >= minConfidence) {
            return right;
        }
        double confidenceDiff = Math.abs(left.confidence() - right.confidence());
        if (confidenceDiff <= PRIORITY_CONFIDENCE_EPSILON) {
            return sourcePriority(left) <= sourcePriority(right) ? left : right;
        }
        return left.confidence() >= right.confidence() ? left : right;
    }

    private double minConfidence(MetadataSchema schema, String fieldKey) {
        if (schema == null) {
            return DEFAULT_MIN_CONFIDENCE;
        }
        return schema.find(fieldKey).map(MetadataFieldDescriptor::minConfidence).orElse(DEFAULT_MIN_CONFIDENCE);
    }

    private boolean isLlm(MetadataFieldCandidate candidate) {
        return "llm".equalsIgnoreCase(candidate.sourceType())
                || "LlmMetadataExtractor".equalsIgnoreCase(candidate.extractorName());
    }

    private int sourcePriority(MetadataFieldCandidate candidate) {
        String sourceType = Objects.requireNonNullElse(candidate.sourceType(), "").toLowerCase();
        return switch (sourceType) {
            case "source", "metadata" -> 10;
            case "tika", "parsemetadata" -> 20;
            case "path" -> 30;
            case "rule", "text" -> 40;
            case "dictionary" -> 50;
            case "llm" -> 80;
            default -> 60;
        };
    }

    private MetadataSchema resolveSchema(IngestionContext context, NodeConfig config) {
        if (context.getMetadataSchema() != null) {
            return context.getMetadataSchema();
        }
        MetadataSchema schema = schemaRegistryPort.loadSchema(
                firstText(setting(config, KEY_TENANT_ID), metadataText(context, KEY_TENANT_ID)),
                firstText(setting(config, KEY_KB_ID), metadataText(context, KEY_KB_ID)));
        context.setMetadataSchema(schema);
        return schema;
    }

    private String setting(NodeConfig config, String key) {
        JsonNode settings = config == null ? null : config.getSettings();
        JsonNode value = settings == null ? null : settings.get(key);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String metadataText(IngestionContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
