package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetadataExtractorNodeFeature implements IngestionNodeFeature {

    public static final String NODE_TYPE = "metadata_extractor";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String EXTRACTOR_VERSION = "deterministic-1";
    private static final String LLM_EXTRACTOR_VERSION = "llm-1";
    private static final String LLM_PROMPT_VERSION = "prompt-v1";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_KB_ID = "kbId";
    private static final String KEY_DOC_ID = "docId";
    private static final String KEY_PARSE_METADATA = "parseMetadata";
    private static final String KEY_RULES = "rules";
    private static final String KEY_EXTRACTOR_VERSION = "extractorVersion";
    private static final String KEY_METADATA_EXTRACTION_CONTEXT = "metadataExtractionContext";
    private static final String KEY_LLM_ENABLED = "llmEnabled";
    private static final String KEY_LLM_MODEL = "llmModel";
    private static final String KEY_LLM_EXTRACTOR_VERSION = "llmExtractorVersion";
    private static final String KEY_LLM_PROMPT_VERSION = "llmPromptVersion";
    private static final String KEY_LLM_CONFIDENCE = "llmConfidence";
    private static final String KEY_LLM_MAX_TEXT_CHARS = "llmMaxTextChars";
    private static final String EVENT_EXTRACTION_COMPLETED = "metadata.extraction.completed";
    private static final double LLM_MISSING_EVIDENCE_CONFIDENCE_CAP = 0.6D;
    private static final Set<String> LLM_FORBIDDEN_FIELDS = Set.of(
            "tenantid", "kbid", "docid", "aclsubjects", "securitylevel");

    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final ChatModelPort chatModelPort;
    private final ObservationPort observationPort;

    public MetadataExtractorNodeFeature(MetadataSchemaRegistryPort schemaRegistryPort) {
        this(schemaRegistryPort, ChatModelPort.noop());
    }

    public MetadataExtractorNodeFeature(MetadataSchemaRegistryPort schemaRegistryPort,
                                        ChatModelPort chatModelPort) {
        this(schemaRegistryPort, chatModelPort, null);
    }

    public MetadataExtractorNodeFeature(MetadataSchemaRegistryPort schemaRegistryPort,
                                        ChatModelPort chatModelPort,
                                        ObservationPort observationPort) {
        this.schemaRegistryPort = Objects.requireNonNullElse(schemaRegistryPort, MetadataSchemaRegistryPort.empty());
        this.chatModelPort = Objects.requireNonNullElse(chatModelPort, ChatModelPort.noop());
        this.observationPort = observationPort;
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
        long startedAt = System.nanoTime();
        try {
            MetadataSchema schema = schemaRegistryPort.loadSchema(
                    firstText(setting(config, KEY_TENANT_ID), metadataText(safeContext, KEY_TENANT_ID)),
                    firstText(setting(config, KEY_KB_ID), metadataText(safeContext, KEY_KB_ID)));
            safeContext.setMetadataSchema(schema);
            List<MetadataIssue> issues = new ArrayList<>();
            List<MetadataFieldCandidate> candidates = new ArrayList<>();
            Map<String, Object> sourceMetadata = sourceMetadata(safeContext);
            Map<String, Object> parseMetadata = parseMetadata(sourceMetadata);
            String extractorVersion = resolveExtractorVersion(safeContext, config);
            String llmExtractorVersion = resolveLlmExtractorVersion(safeContext, config);
            String llmPromptVersion = resolveLlmPromptVersion(safeContext, config);
            boolean llmEnabled = booleanSetting(config, KEY_LLM_ENABLED);
            attachExtractionContext(safeContext, extractorVersion, llmExtractorVersion, llmPromptVersion, llmEnabled);
            extractFromSchemaFields(schema, sourceMetadata, parseMetadata, safeContext, extractorVersion,
                    candidates, issues);
            extractFromConfiguredRules(schema, safeContext, config, sourceMetadata, parseMetadata,
                    extractorVersion, candidates, issues);
            extractFromLlm(schema, safeContext, config, sourceMetadata, parseMetadata, llmExtractorVersion,
                    llmPromptVersion, candidates, issues);
            safeContext.setMetadataCandidates(candidates);
            safeContext.setMetadataIssues(issues);
            recordExtractionEvent(schema, safeContext, config, candidates.size(), issues.size(), true, null,
                    elapsedMillis(startedAt));
            return NodeResult.ok("metadata candidates=" + candidates.size());
        } catch (Exception ex) {
            recordExtractionEvent(null, safeContext, config, 0, 0, false, ex, elapsedMillis(startedAt));
            return NodeResult.fail(ex);
        }
    }

    private void recordExtractionEvent(MetadataSchema schema,
                                       IngestionContext context,
                                       NodeConfig config,
                                       int candidateCount,
                                       int issueCount,
                                       boolean success,
                                       Exception ex,
                                       long durationMs) {
        if (observationPort == null) {
            return;
        }
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tenantId", firstText(schema == null ? "" : schema.tenantId(),
                    firstText(setting(config, KEY_TENANT_ID), metadataText(context, KEY_TENANT_ID))));
            attributes.put("knowledgeBaseId", firstText(schema == null ? "" : schema.knowledgeBaseId(),
                    firstText(setting(config, KEY_KB_ID), metadataText(context, KEY_KB_ID))));
            attributes.put("schemaVersion", Integer.toString(schema == null ? 0 : schema.schemaVersion()));
            attributes.put("extractorVersion", resolveExtractorVersion(context, config));
            attributes.put("llmEnabled", Boolean.toString(booleanSetting(config, KEY_LLM_ENABLED)));
            attributes.put("candidateCount", Integer.toString(candidateCount));
            attributes.put("issueCount", Integer.toString(issueCount));
            attributes.put("durationMs", Long.toString(durationMs));
            attributes.put("success", Boolean.toString(success));
            if (ex != null) {
                attributes.put("exception", ex.getClass().getSimpleName());
            }
            observationPort.recordEvent(new ObservationEvent(EVENT_EXTRACTION_COMPLETED, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响元数据抽取主流程。
        }
    }

    private void extractFromSchemaFields(MetadataSchema schema,
                                         Map<String, Object> sourceMetadata,
                                         Map<String, Object> parseMetadata,
                                         IngestionContext context,
                                         String extractorVersion,
                                         List<MetadataFieldCandidate> candidates,
                                         List<MetadataIssue> issues) {
        for (MetadataFieldDescriptor field : schema.fields()) {
            collectDirect(field, sourceMetadata, "source", "SourceMetadataExtractor", 0.99D,
                    schema.schemaVersion(), extractorVersion, candidates);
            collectDirect(field, parseMetadata, "tika", "TikaMetadataExtractor", 0.95D,
                    schema.schemaVersion(), extractorVersion, candidates);
            collectRegex(field, context.getRawText(), "rule", "RuleMetadataExtractor", 0.9D,
                    schema.schemaVersion(), extractorVersion, candidates, issues);
            collectRegex(field, metadataText(context, "fileName"), "path", "PathMetadataExtractor", 0.88D,
                    schema.schemaVersion(), extractorVersion, candidates, issues);
        }
    }

    private void extractFromConfiguredRules(MetadataSchema schema,
                                            IngestionContext context,
                                            NodeConfig config,
                                            Map<String, Object> sourceMetadata,
                                            Map<String, Object> parseMetadata,
                                            String extractorVersion,
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
                        confidence, evidence(value), schema.schemaVersion(), extractorVersion));
            }
        }
    }

    private void extractFromLlm(MetadataSchema schema,
                                IngestionContext context,
                                NodeConfig config,
                                Map<String, Object> sourceMetadata,
                                Map<String, Object> parseMetadata,
                                String llmExtractorVersion,
                                String llmPromptVersion,
                                List<MetadataFieldCandidate> candidates,
                                List<MetadataIssue> issues) {
        if (!booleanSetting(config, KEY_LLM_ENABLED) || schema.fields().isEmpty()) {
            return;
        }
        try {
            String modelId = setting(config, KEY_LLM_MODEL);
            int maxTextChars = intSetting(config, KEY_LLM_MAX_TEXT_CHARS, 4000);
            String response = chatModelPort.chat(modelId, List.of(
                    ChatMessage.system(llmSystemPrompt()),
                    ChatMessage.user(llmUserPrompt(schema, context, sourceMetadata, parseMetadata,
                            maxTextChars, llmPromptVersion))));
            JsonNode root = parseLlmJson(response);
            if (root == null || !root.isObject()) {
                issues.add(MetadataIssue.warn("", NODE_TYPE, "LLM_RESPONSE_INVALID", "LLM 元数据抽取结果不是 JSON 对象"));
                return;
            }
            collectLlmCandidates(schema, config, root, llmExtractorVersion, llmPromptVersion, candidates, issues);
        } catch (RuntimeException ex) {
            issues.add(MetadataIssue.warn("", NODE_TYPE, "LLM_EXTRACT_FAILED", ex.getMessage()));
        }
    }

    private void collectLlmCandidates(MetadataSchema schema,
                                      NodeConfig config,
                                      JsonNode root,
                                      String llmExtractorVersion,
                                      String llmPromptVersion,
                                      List<MetadataFieldCandidate> candidates,
                                      List<MetadataIssue> issues) {
        double defaultConfidence = doubleSetting(config, KEY_LLM_CONFIDENCE, 0.72D);
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            String fieldKey = entry.getKey();
            if (isLlmForbiddenField(fieldKey)) {
                issues.add(MetadataIssue.warn(fieldKey, NODE_TYPE, "LLM_FORBIDDEN_FIELD",
                        "LLM 返回系统或权限字段，已忽略"));
                continue;
            }
            if (schema.find(fieldKey).isEmpty()) {
                // LLM 输出永远不直接信任，未注册字段只记录治理问题，不能进入候选集。
                issues.add(MetadataIssue.warn(fieldKey, NODE_TYPE, "LLM_UNREGISTERED_FIELD",
                        "LLM 返回未注册字段，已忽略"));
                continue;
            }
            LlmFieldValue fieldValue = llmFieldValue(entry.getValue(), defaultConfidence);
            if (present(fieldValue.value())) {
                double confidence = fieldValue.confidence();
                if (!hasText(fieldValue.evidence())) {
                    confidence = Math.min(confidence, LLM_MISSING_EVIDENCE_CONFIDENCE_CAP);
                    issues.add(MetadataIssue.warn(fieldKey, NODE_TYPE, "LLM_EVIDENCE_MISSING",
                            "LLM 返回字段缺少证据片段"));
                }
                candidates.add(candidate(fieldKey, fieldValue.value(), "llm", "LlmMetadataExtractor",
                        confidence, fieldValue.evidence(), schema.schemaVersion(), llmExtractorVersion,
                        llmPromptVersion));
            }
        }
    }

    private LlmFieldValue llmFieldValue(JsonNode node, double defaultConfidence) {
        JsonNode valueNode = node;
        double confidence = defaultConfidence;
        String evidence = "";
        if (node != null && node.isObject() && node.has("value")) {
            valueNode = node.get("value");
            confidence = clamp(doubleValue(node, "confidence", defaultConfidence), 0D, 1D);
            evidence = text(node, "evidence");
        }
        Object value = valueNode == null || valueNode.isNull()
                ? null : OBJECT_MAPPER.convertValue(valueNode, Object.class);
        return new LlmFieldValue(value, confidence, evidence);
    }

    private String llmSystemPrompt() {
        return """
                你是企业元数据抽取器。只能抽取已注册 Schema 字段。
                禁止输出 tenant_id、kb_id、doc_id、acl_subjects、security_level 等系统或权限字段。
                返回格式必须是 JSON 对象：{"fieldKey":{"value":...,"confidence":0.0-1.0,"evidence":"..."}}。
                """;
    }

    private String llmUserPrompt(MetadataSchema schema,
                                 IngestionContext context,
                                 Map<String, Object> sourceMetadata,
                                 Map<String, Object> parseMetadata,
                                 int maxTextChars,
                                 String llmPromptVersion) {
        return """
                请只返回 JSON，不要输出 Markdown 或解释。
                Schema 字段：
                %s

                解析元数据：
                %s

                来源元数据：
                %s

                文档文本：
                %s
                """.formatted(schemaSummary(schema), json(parseMetadata), json(sourceMetadata),
                truncate(Objects.requireNonNullElse(context.getRawText(), ""), maxTextChars))
                + "\nPrompt version: " + llmPromptVersion;
    }

    private String schemaSummary(MetadataSchema schema) {
        return schema.fields().stream()
                .map(field -> "- %s (%s): %s".formatted(field.fieldKey(), field.valueType(), field.displayName()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private JsonNode parseLlmJson(String response) {
        if (!hasText(response)) {
            return null;
        }
        String text = response.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(text.substring(start, end + 1));
        } catch (Exception ex) {
            return null;
        }
    }

    private void collectDirect(MetadataFieldDescriptor field,
                               Map<String, Object> metadata,
                               String sourceType,
                               String extractorName,
                               double confidence,
                               int schemaVersion,
                               String extractorVersion,
                               List<MetadataFieldCandidate> candidates) {
        for (String key : candidateKeys(field, sourceType)) {
            Object value = metadata.get(key);
            if (present(value)) {
                candidates.add(candidate(field.fieldKey(), value, sourceType, extractorName, confidence,
                        key, schemaVersion, extractorVersion));
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
                              String extractorVersion,
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
                        pattern, schemaVersion, extractorVersion));
            }
        } catch (RuntimeException ex) {
            issues.add(MetadataIssue.warn(field.fieldKey(), NODE_TYPE, "REGEX_FAILED", ex.getMessage()));
        }
    }

    private String resolveExtractorVersion(IngestionContext context, NodeConfig config) {
        // 复核重抽取会把目标 extractorVersion 写入上下文，这里统一收敛版本来源。
        return firstText(firstText(setting(config, KEY_EXTRACTOR_VERSION),
                metadataText(context, KEY_EXTRACTOR_VERSION)), EXTRACTOR_VERSION);
    }

    private String resolveLlmExtractorVersion(IngestionContext context, NodeConfig config) {
        return firstText(
                firstText(setting(config, KEY_LLM_EXTRACTOR_VERSION), setting(config, KEY_EXTRACTOR_VERSION)),
                firstText(metadataText(context, KEY_EXTRACTOR_VERSION), LLM_EXTRACTOR_VERSION));
    }

    private String resolveLlmPromptVersion(IngestionContext context, NodeConfig config) {
        return firstText(firstText(setting(config, KEY_LLM_PROMPT_VERSION),
                metadataText(context, KEY_LLM_PROMPT_VERSION)), LLM_PROMPT_VERSION);
    }

    private void attachExtractionContext(IngestionContext context,
                                         String extractorVersion,
                                         String llmExtractorVersion,
                                         String llmPromptVersion,
                                         boolean llmEnabled) {
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(context.getMetadata(), Map.of()));
        Map<String, Object> extractionContext = new LinkedHashMap<>();
        // 仅写入审计上下文，不作为候选字段参与 Schema 抽取或 accepted metadata 写回。
        extractionContext.put(KEY_EXTRACTOR_VERSION, extractorVersion);
        if (llmEnabled) {
            extractionContext.put(KEY_LLM_EXTRACTOR_VERSION, llmExtractorVersion);
            extractionContext.put(KEY_LLM_PROMPT_VERSION, llmPromptVersion);
        }
        metadata.put(KEY_METADATA_EXTRACTION_CONTEXT, extractionContext);
        context.setMetadata(metadata);
    }

    private List<String> candidateKeys(MetadataFieldDescriptor field, String sourceType) {
        List<String> keys = new ArrayList<>();
        keys.add(field.fieldKey());
        keys.add(field.backendMapping().canonicalName());
        // 不同抽取来源使用各自的别名，避免 sourceKeys 误匹配 Tika 解析元数据。
        if ("source".equals(sourceType)) {
            addHintKeys(keys, field, "sourceKeys");
        } else if ("tika".equals(sourceType)) {
            addHintKeys(keys, field, "parseKeys");
            addHintKeys(keys, field, "tikaKeys");
        }
        return keys.stream().filter(this::hasText).distinct().toList();
    }

    private void addHintKeys(List<String> keys, MetadataFieldDescriptor field, String hintName) {
        Object hint = field.extractionHints().get(hintName);
        if (hint instanceof List<?> list) {
            list.stream().filter(Objects::nonNull).map(String::valueOf).forEach(keys::add);
        }
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
        return candidate(fieldKey, value, sourceType, extractorName, confidence, evidence, schemaVersion,
                EXTRACTOR_VERSION);
    }

    private MetadataFieldCandidate candidate(String fieldKey,
                                             Object value,
                                             String sourceType,
                                             String extractorName,
                                             double confidence,
                                             String evidence,
                                             int schemaVersion,
                                             String extractorVersion) {
        return new MetadataFieldCandidate(fieldKey, value, sourceType, extractorName, confidence, evidence,
                Math.max(schemaVersion, 1), extractorVersion);
    }

    private MetadataFieldCandidate candidate(String fieldKey,
                                             Object value,
                                             String sourceType,
                                             String extractorName,
                                             double confidence,
                                             String evidence,
                                             int schemaVersion,
                                             String extractorVersion,
                                             String promptVersion) {
        return new MetadataFieldCandidate(fieldKey, value, sourceType, extractorName, confidence, evidence,
                Math.max(schemaVersion, 1), extractorVersion, promptVersion);
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

    private boolean booleanSetting(NodeConfig config, String key) {
        JsonNode settings = config == null ? null : config.getSettings();
        JsonNode value = settings == null ? null : settings.get(key);
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return Boolean.parseBoolean(value.asText(""));
    }

    private int intSetting(NodeConfig config, String key, int defaultValue) {
        JsonNode settings = config == null ? null : config.getSettings();
        JsonNode value = settings == null ? null : settings.get(key);
        if (value == null || !value.isNumber()) {
            return defaultValue;
        }
        return value.asInt(defaultValue);
    }

    private double doubleSetting(NodeConfig config, String key, double defaultValue) {
        JsonNode settings = config == null ? null : config.getSettings();
        JsonNode value = settings == null ? null : settings.get(key);
        if (value == null || !value.isNumber()) {
            return defaultValue;
        }
        return value.asDouble(defaultValue);
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
        return truncate(text, 128);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        int safeMaxLength = maxLength <= 0 ? 4000 : maxLength;
        return text.length() <= safeMaxLength ? text : text.substring(0, safeMaxLength);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private boolean isLlmForbiddenField(String fieldKey) {
        String normalized = Objects.requireNonNullElse(fieldKey, "")
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return LLM_FORBIDDEN_FIELDS.contains(normalized);
    }

    private String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Objects.requireNonNullElse(value, Map.of()));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private record LlmFieldValue(Object value, double confidence, String evidence) {
    }
}
