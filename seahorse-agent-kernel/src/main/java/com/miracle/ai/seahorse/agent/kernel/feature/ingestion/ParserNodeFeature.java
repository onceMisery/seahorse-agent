/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParseResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 原生文档解析节点。
 *
 * <p>节点兼容旧 parser 配置的 rules 结构，并通过 {@link DocumentParserPort} 把具体格式解析交给 L3 adapter。
 */
public class ParserNodeFeature implements IngestionNodeFeature {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String NODE_TYPE = "parser";
    private static final String KEY_RULES = "rules";
    private static final String KEY_MIME_TYPE = "mimeType";
    private static final String KEY_OPTIONS = "options";
    private static final String KEY_FILE_NAME = "fileName";
    private static final String KEY_PARSE_METADATA = "parseMetadata";

    private final DocumentParserPort parserPort;

    public ParserNodeFeature(DocumentParserPort parserPort) {
        this.parserPort = Objects.requireNonNullElse(parserPort, DocumentParserPort.plainText());
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
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        IngestionContext safeContext = Objects.requireNonNull(context, "context must not be null");
        byte[] rawBytes = safeContext.getRawBytes();
        if (rawBytes == null || rawBytes.length == 0) {
            return NodeResult.fail(new IllegalArgumentException("rawBytes must not be empty"));
        }
        try {
            ParserRule rule = matchRule(parseRules(config), safeContext);
            DocumentParseResult result = parserPort.parse(rawBytes, safeContext.getMimeType(),
                    resolveFileName(safeContext), rule.options());
            applyResult(safeContext, result);
            return NodeResult.ok("parsed text length=" + result.text().length());
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    private List<ParserRule> parseRules(NodeConfig config) {
        JsonNode settings = config == null ? null : config.getSettings();
        if (settings == null || settings.isNull()) {
            return List.of();
        }
        JsonNode rules = settings.get(KEY_RULES);
        if (rules == null || !rules.isArray()) {
            return List.of();
        }
        List<ParserRule> parsedRules = new ArrayList<>();
        for (JsonNode rule : rules) {
            ParserRule parsedRule = parseRule(rule);
            if (parsedRule != null) {
                parsedRules.add(parsedRule);
            }
        }
        return parsedRules;
    }

    private ParserRule parseRule(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String mimeType = text(node.get(KEY_MIME_TYPE));
        Map<String, Object> options = toMap(node.get(KEY_OPTIONS));
        return new ParserRule(normalizeType(mimeType), options);
    }

    private ParserRule matchRule(List<ParserRule> rules, IngestionContext context) {
        if (rules.isEmpty()) {
            return ParserRule.empty();
        }
        String resolvedType = resolveType(context);
        return rules.stream()
                .filter(rule -> rule.matches(resolvedType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported document type: " + resolvedType));
    }

    private String resolveType(IngestionContext context) {
        String byName = resolveTypeByName(resolveFileName(context));
        if (hasText(byName)) {
            return byName;
        }
        return resolveTypeByMime(context.getMimeType());
    }

    private String resolveTypeByName(String fileName) {
        String lower = Objects.requireNonNullElse(fileName, "").toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "MARKDOWN";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "WORD";
        }
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return "EXCEL";
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return "PPT";
        }
        if (lower.endsWith(".txt")) {
            return "TEXT";
        }
        return "";
    }

    private String resolveTypeByMime(String mimeType) {
        String lower = Objects.requireNonNullElse(mimeType, "").toLowerCase(Locale.ROOT);
        if (lower.contains("pdf")) {
            return "PDF";
        }
        if (lower.contains("markdown")) {
            return "MARKDOWN";
        }
        if (lower.contains("word") || lower.contains("msword") || lower.contains("wordprocessingml")) {
            return "WORD";
        }
        if (lower.contains("excel") || lower.contains("spreadsheetml")) {
            return "EXCEL";
        }
        if (lower.contains("powerpoint") || lower.contains("presentation")) {
            return "PPT";
        }
        if (lower.startsWith("text/")) {
            return "TEXT";
        }
        return "UNKNOWN";
    }

    private String normalizeType(String value) {
        String raw = Objects.requireNonNullElse(value, "").trim().toUpperCase(Locale.ROOT);
        return switch (raw) {
            case "", "*", "ALL", "DEFAULT" -> "ALL";
            case "MD", "MARKDOWN" -> "MARKDOWN";
            case "DOC", "DOCX", "WORD" -> "WORD";
            case "XLS", "XLSX", "EXCEL" -> "EXCEL";
            case "PPT", "PPTX", "POWERPOINT" -> "PPT";
            case "TXT", "TEXT" -> "TEXT";
            default -> raw;
        };
    }

    private void applyResult(IngestionContext context, DocumentParseResult result) {
        context.setRawText(result.text());
        context.setDocument(result);
        if (!result.metadata().isEmpty()) {
            Map<String, Object> metadata = new HashMap<>(Objects.requireNonNullElse(context.getMetadata(), Map.of()));
            metadata.put(KEY_PARSE_METADATA, result.metadata());
            context.setMetadata(metadata);
        }
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
    }

    private String resolveFileName(IngestionContext context) {
        Map<String, Object> metadata = context.getMetadata();
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get(KEY_FILE_NAME);
        return value == null ? "" : String.valueOf(value);
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ParserRule(String mimeType, Map<String, Object> options) {

        private static ParserRule empty() {
            return new ParserRule("ALL", Map.of());
        }

        ParserRule {
            mimeType = Objects.requireNonNullElse(mimeType, "ALL");
            options = Objects.requireNonNullElse(options, Map.of());
        }

        private boolean matches(String resolvedType) {
            return "ALL".equals(mimeType) || mimeType.equalsIgnoreCase(resolvedType);
        }
    }
}
