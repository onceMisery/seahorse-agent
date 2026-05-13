package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetadataExtractorNodeFeature implements IngestionNodeFeature {

    public static final String NODE_TYPE = "metadata_extractor";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String EXTRACTOR_VERSION = "deterministic-1";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_KB_ID = "kbId";
    private static final String KEY_DOC_ID = "docId";
    private static final String KEY_PARSE_METADATA = "parseMetadata";
    private static final String KEY_RULES = "rules";

    private final MetadataSchemaRegistryPort schemaRegistryPort;

    public MetadataExtractorNodeFeature(MetadataSchemaRegistryPort schemaRegistryPort) {
        this.schemaRegistryPort = Objects.requireNonNullElse(schemaRegistryPort, MetadataSchemaRegistryPort.empty());
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
        return 30;
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        IngestionContext safeContext = Objects.requireNonNull(context, "context must not be null");
        try {
            MetadataSchema schema = schemaRegistryPort.loadSchema(
                    firstText(setting(config, KEY_TENANT_ID), metadataText(safeContext, KEY_TENANT_ID)),
                    firstText(setting(config, KEY_KB_ID), metadataText(safeContext, KEY_KB_ID)));
            safeContext.setMetadataSchema(schema);
            List<MetadataIssue> issues = new ArrayList<>();
            List<MetadataFieldCandidate> candidates = new ArrayList<>();
            Map<String, Object> sourceMetadata = sourceMetadata(safeContext);
            Map<String, Object> parseMetadata = parseMetadata(sourceMetadata);
            extractFromSchemaFields(schema, sourceMetadata, parseMetadata, safeContext, candidates, issues);
            extractFromConfiguredRules(schema, safeContext, config, sourceMetadata, parseMetadata, candidates, issues);
            safeContext.setMetadataCandidates(candidates);
            safeContext.setMetadataIssues(issues);
            return NodeResult.ok("metadata candidates=" + candidates.size());
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    private void extractFromSchemaFields(MetadataSchema schema,
                                         Map<String, Object> sourceMetadata,
                                         Map<String, Object> parseMetadata,
                                         IngestionContext context,
                                         List<MetadataFieldCandidate> candidates,
                                         List<MetadataIssue> issues) {
        for (MetadataFieldDescriptor field : schema.fields()) {
            collectDirect(field, sourceMetadata, "source", "SourceMetadataExtractor", 0.99D,
                    schema.schemaVersion(), candidates);
            collectDirect(field, parseMetadata, "tika", "TikaMetadataExtractor", 0.95D,
                    schema.schemaVersion(), candidates);
            collectRegex(field, context.getRawText(), "rule", "RuleMetadataExtractor", 0.9D,
                    schema.schemaVersion(), candidates, issues);
            collectRegex(field, metadataText(context, "fileName"), "path", "PathMetadataExtractor", 0.88D,
                    schema.schemaVersion(), candidates, issues);
        }
    }

    private void extractFromConfiguredRules(MetadataSchema schema,
                                            IngestionContext context,
                                            NodeConfig config,
                                            Map<String, Object> sourceMetadata,
                                            Map<String, Object> parseMetadata,
                                            List<MetadataFieldCandidate> candidates,
                                            List<MetadataIssue> issues) {
        JsonNode rules = config == null || config.getSettings() == null ? null : config.getSettings().get(KEY_RULES);
        if (rules == null || !rules.isArray()) {
            return;
        }
        for (JsonNode rule : rules) {
            String fieldKey = text(rule, "fieldKey");
            if (!hasText(fieldKey) || schema.find(fieldKey).isEmpty()) {
                issues.add(MetadataIssue.warn(fieldKey, NODE_TYPE, "UNKNOWN_RULE_FIELD", "规则字段未注册"));
                continue;
            }
            String source = firstText(text(rule, "source"), "metadata");
            String key = firstText(text(rule, "key"), fieldKey);
            double confidence = doubleValue(rule, "confidence", 0.9D);
            Object value = switch (source) {
                case "parseMetadata" -> parseMetadata.get(key);
                case "text" -> regexValue(context.getRawText(), text(rule, "regex"));
                case "path" -> regexValue(metadataText(context, "fileName"), text(rule, "regex"));
                default -> sourceMetadata.get(key);
            };
            if (present(value)) {
                candidates.add(candidate(fieldKey, value, source, "ConfiguredMetadataExtractor",
                        confidence, evidence(value), schema.schemaVersion()));
            }
        }
    }

    private void collectDirect(MetadataFieldDescriptor field,
                               Map<String, Object> metadata,
                               String sourceType,
                               String extractorName,
                               double confidence,
                               int schemaVersion,
                               List<MetadataFieldCandidate> candidates) {
        for (String key : candidateKeys(field)) {
            Object value = metadata.get(key);
            if (present(value)) {
                candidates.add(candidate(field.fieldKey(), value, sourceType, extractorName, confidence,
                        key, schemaVersion));
                return;
            }
        }
    }

    private void collectRegex(MetadataFieldDescriptor field,
                              String text,
                              String sourceType,
                              String extractorName,
                              double confidence,
                              int schemaVersion,
                              List<MetadataFieldCandidate> candidates,
                              List<MetadataIssue> issues) {
        Object regex = field.extractionHints().get(sourceType + "Regex");
        if (!(regex instanceof String pattern) || pattern.isBlank() || !hasText(text)) {
            return;
        }
        try {
            Object value = regexValue(text, pattern);
            if (present(value)) {
                candidates.add(candidate(field.fieldKey(), value, sourceType, extractorName, confidence,
                        pattern, schemaVersion));
            }
        } catch (RuntimeException ex) {
            issues.add(MetadataIssue.warn(field.fieldKey(), NODE_TYPE, "REGEX_FAILED", ex.getMessage()));
        }
    }

    private List<String> candidateKeys(MetadataFieldDescriptor field) {
        List<String> keys = new ArrayList<>();
        keys.add(field.fieldKey());
        keys.add(field.backendMapping().canonicalName());
        Object hint = field.extractionHints().get("sourceKeys");
        if (hint instanceof List<?> list) {
            list.stream().filter(Objects::nonNull).map(String::valueOf).forEach(keys::add);
        }
        return keys.stream().filter(this::hasText).distinct().toList();
    }

    private Object regexValue(String text, String regex) {
        if (!hasText(text) || !hasText(regex)) {
            return null;
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
    }

    private MetadataFieldCandidate candidate(String fieldKey,
                                             Object value,
                                             String sourceType,
                                             String extractorName,
                                             double confidence,
                                             String evidence,
                                             int schemaVersion) {
        return new MetadataFieldCandidate(fieldKey, value, sourceType, extractorName, confidence, evidence,
                Math.max(schemaVersion, 1), EXTRACTOR_VERSION);
    }

    private Map<String, Object> sourceMetadata(IngestionContext context) {
        return new LinkedHashMap<>(Objects.requireNonNullElse(context.getMetadata(), Map.of()));
    }

    private Map<String, Object> parseMetadata(Map<String, Object> metadata) {
        Object value = metadata.get(KEY_PARSE_METADATA);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> parsed = new LinkedHashMap<>();
            map.forEach((key, item) -> parsed.put(String.valueOf(key), item));
            return parsed;
        }
        return Map.of();
    }

    private String setting(NodeConfig config, String key) {
        return config == null ? "" : text(config.getSettings(), key);
    }

    private String metadataText(IngestionContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String text(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return "";
        }
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private double doubleValue(JsonNode node, String key, double defaultValue) {
        if (node == null || node.get(key) == null || !node.get(key).isNumber()) {
            return defaultValue;
        }
        return node.get(key).asDouble(defaultValue);
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean present(Object value) {
        return value != null && !(value instanceof String text && text.isBlank());
    }

    private String evidence(Object value) {
        String text = String.valueOf(value);
        return text.length() <= 128 ? text : text.substring(0, 128);
    }
}
