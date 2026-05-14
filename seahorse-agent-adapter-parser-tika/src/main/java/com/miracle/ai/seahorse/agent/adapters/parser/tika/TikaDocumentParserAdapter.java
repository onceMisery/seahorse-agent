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

package com.miracle.ai.seahorse.agent.adapters.parser.tika;

import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParseResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Apache Tika 文档解析适配器。
 *
 * <p>支持 PDF、Word、Excel、PPT、HTML、纯文本等常见格式。
 */
public class TikaDocumentParserAdapter implements DocumentParserPort {

    private static final Tika TIKA = new Tika();
    private static final String PARSER_NAME = "tika";
    private static final String KEY_PARSER = "parser";
    private static final String KEY_PARSER_VERSION = "parserVersion";
    private static final String KEY_CONTENT_TYPE = "contentType";
    private static final String KEY_MIME_TYPE = "mimeType";
    private static final String KEY_RESOURCE_NAME = "resourceName";
    private static final String KEY_FILE_NAME = "fileName";
    private static final String KEY_TITLE = "title";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_CREATED_TIME = "createdTime";
    private static final String KEY_MODIFIED_AT = "modifiedAt";
    private static final String KEY_MODIFIED_TIME = "modifiedTime";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_PAGE_COUNT = "pageCount";
    private static final String KEY_PARSE_DURATION_MS = "parseDurationMs";
    private static final String KEY_INPUT_SIZE_BYTES = "inputSizeBytes";
    private static final String KEY_TEXT_CHAR_COUNT = "textCharCount";
    private static final String KEY_WARNINGS = "warnings";
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern HORIZONTAL_SPACES = Pattern.compile("[\\t\\x0B\\f\\r ]+");

    @Override
    public DocumentParseResult parse(byte[] content, String mimeType, String fileName, Map<String, Object> options) {
        long startNanos = System.nanoTime();
        byte[] safeContent = Objects.requireNonNullElse(content, new byte[0]);
        if (safeContent.length == 0) {
            return DocumentParseResult.ofText("");
        }
        if (isPlainText(mimeType, fileName)) {
            String text = cleanup(new String(safeContent, StandardCharsets.UTF_8));
            Map<String, Object> metadata = baseMetadata(mimeType, fileName);
            addParseMetrics(metadata, startNanos, safeContent.length, text.length());
            return new DocumentParseResult(text, metadata);
        }
        return parseWithTika(safeContent, mimeType, fileName, startNanos);
    }

    private DocumentParseResult parseWithTika(byte[] content, String mimeType, String fileName, long startNanos) {
        Metadata metadata = new Metadata();
        if (mimeType != null && !mimeType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, mimeType);
        }
        if (fileName != null && !fileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            String text = cleanup(TIKA.parseToString(inputStream, metadata));
            Map<String, Object> values = metadata(metadata);
            addParseMetrics(values, startNanos, content.length, text.length());
            return new DocumentParseResult(text, values);
        } catch (Exception ex) {
            throw new IllegalStateException("Tika document parse failed", ex);
        }
    }

    private Map<String, Object> metadata(Metadata metadata) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            String[] items = metadata.getValues(name);
            if (items == null || items.length == 0) {
                continue;
            }
            values.put(name, items.length == 1 ? items[0] : java.util.List.of(items));
        }
        addStableMetadata(values, metadata);
        return values;
    }

    private Map<String, Object> baseMetadata(String mimeType, String fileName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, KEY_PARSER, PARSER_NAME);
        putIfPresent(metadata, KEY_PARSER_VERSION, parserVersion());
        if (mimeType != null && !mimeType.isBlank()) {
            metadata.put("Content-Type", mimeType);
            putIfPresent(metadata, KEY_CONTENT_TYPE, mediaType(mimeType));
            putIfPresent(metadata, KEY_MIME_TYPE, mediaType(mimeType));
        }
        if (fileName != null && !fileName.isBlank()) {
            putIfPresent(metadata, KEY_RESOURCE_NAME, fileName);
            putIfPresent(metadata, KEY_FILE_NAME, fileName);
        }
        return metadata;
    }

    private void addStableMetadata(Map<String, Object> values, Metadata metadata) {
        // 保留 Tika 原始 key，同时补充治理链路可稳定依赖的规范 key。
        putIfPresent(values, KEY_PARSER, PARSER_NAME);
        putIfPresent(values, KEY_PARSER_VERSION, parserVersion());
        String contentType = mediaType(firstText(metadata.get(Metadata.CONTENT_TYPE), string(values.get("Content-Type"))));
        putIfPresent(values, KEY_CONTENT_TYPE, contentType);
        putIfPresent(values, KEY_MIME_TYPE, contentType);
        String resourceName = firstText(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY),
                string(values.get(KEY_RESOURCE_NAME)));
        putIfPresent(values, KEY_RESOURCE_NAME, resourceName);
        putIfPresent(values, KEY_FILE_NAME, resourceName);
        putIfPresent(values, KEY_TITLE, metadata.get(TikaCoreProperties.TITLE));
        putIfPresent(values, KEY_AUTHOR, metadataValue(metadata, TikaCoreProperties.CREATOR, Office.AUTHOR));
        String createdAt = firstText(metadata.get(TikaCoreProperties.CREATED), metadata.get(Office.CREATION_DATE));
        putIfPresent(values, KEY_CREATED_AT, createdAt);
        putIfPresent(values, KEY_CREATED_TIME, createdAt);
        String modifiedAt = firstText(metadata.get(TikaCoreProperties.MODIFIED), metadata.get(Office.SAVE_DATE));
        putIfPresent(values, KEY_MODIFIED_AT, modifiedAt);
        putIfPresent(values, KEY_MODIFIED_TIME, modifiedAt);
        putIfPresent(values, KEY_LANGUAGE,
                firstText(metadata.get(TikaCoreProperties.LANGUAGE), metadata.get(TikaCoreProperties.TIKA_DETECTED_LANGUAGE)));
        putIfPresent(values, KEY_PAGE_COUNT, pageCount(metadata));
        putIfPresent(values, KEY_WARNINGS, warnings(metadata));
    }

    private Object metadataValue(Metadata metadata, Property... properties) {
        for (Property property : properties) {
            String[] values = metadata.getValues(property);
            String[] presentValues = values == null ? new String[0] : Arrays.stream(values)
                    .filter(this::hasText)
                    .toArray(String[]::new);
            if (presentValues.length == 1) {
                return presentValues[0].trim();
            }
            if (presentValues.length > 1) {
                return java.util.List.of(presentValues);
            }
        }
        return null;
    }

    private Integer pageCount(Metadata metadata) {
        Integer pages = metadata.getInt(PagedText.N_PAGES);
        if (pages != null) {
            return pages;
        }
        return metadata.getInt(Office.PAGE_COUNT);
    }

    private void addParseMetrics(Map<String, Object> metadata, long startNanos, int inputSizeBytes, int textCharCount) {
        // 解析耗时和体量指标用于治理报表，不参与检索过滤。
        long elapsedMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        putIfPresent(metadata, KEY_PARSE_DURATION_MS, elapsedMs);
        putIfPresent(metadata, KEY_INPUT_SIZE_BYTES, inputSizeBytes);
        putIfPresent(metadata, KEY_TEXT_CHAR_COUNT, textCharCount);
        putIfPresent(metadata, KEY_WARNINGS, List.of());
    }

    private List<String> warnings(Metadata metadata) {
        List<String> warnings = new ArrayList<>();
        for (String name : metadata.names()) {
            String lowerName = name.toLowerCase(java.util.Locale.ROOT);
            if (!lowerName.contains("warning") && !lowerName.contains("exception")) {
                continue;
            }
            for (String value : metadata.getValues(name)) {
                if (hasText(value)) {
                    warnings.add(name + ": " + value.trim());
                }
            }
        }
        return List.copyOf(warnings);
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (!present(value) || present(metadata.get(key))) {
            return;
        }
        metadata.put(key, value);
    }

    private String parserVersion() {
        return Objects.requireNonNullElse(Tika.class.getPackage().getImplementationVersion(), "");
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String mediaType(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.split(";", 2)[0].trim().toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean present(Object value) {
        return value != null && !(value instanceof String text && text.isBlank());
    }

    private boolean isPlainText(String mimeType, String fileName) {
        String safeMimeType = Objects.requireNonNullElse(mimeType, "").toLowerCase();
        String normalizedMimeType = safeMimeType.split(";", 2)[0].trim();
        // text/html 需要进入 Tika，才能抽取标题、作者等 HTML 元信息。
        if (normalizedMimeType.equals("text/plain")
                || normalizedMimeType.equals("text/markdown")
                || normalizedMimeType.equals("text/x-markdown")) {
            return true;
        }
        String safeFileName = Objects.requireNonNullElse(fileName, "").toLowerCase();
        return safeFileName.endsWith(".txt") || safeFileName.endsWith(".md") || safeFileName.endsWith(".markdown");
    }

    private String cleanup(String text) {
        String normalized = Objects.requireNonNullElse(text, "").replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (String line : lines) {
            builder.append(HORIZONTAL_SPACES.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        return EXCESSIVE_BLANK_LINES.matcher(builder.toString().strip()).replaceAll("\n\n");
    }
}
