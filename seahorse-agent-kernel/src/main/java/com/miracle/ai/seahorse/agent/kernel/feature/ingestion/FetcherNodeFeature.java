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

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionDocumentSource;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 原生文档获取节点。
 *
 * <p>上传链路会提前写入 {@link IngestionContext#getRawBytes()}；非上传链路由
 * {@link DocumentFetcherPort} 拉取内容。这样 Fetcher 节点只依赖端口，不绑定具体来源实现。
 */
public class FetcherNodeFeature implements IngestionNodeFeature {

    private static final String NODE_TYPE = "fetcher";
    private static final String KEY_FILE_NAME = "fileName";
    private static final String KEY_SOURCE = "source";
    private static final Map<String, String> EXTENSION_MIME_TYPES = Map.ofEntries(
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".md", "text/markdown"),
            Map.entry(".markdown", "text/markdown"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".doc", "application/msword"),
            Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(".xls", "application/vnd.ms-excel"),
            Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry(".ppt", "application/vnd.ms-powerpoint"),
            Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private final DocumentFetcherPort documentFetcherPort;

    public FetcherNodeFeature() {
        this(DocumentFetcherPort.unsupported());
    }

    public FetcherNodeFeature(DocumentFetcherPort documentFetcherPort) {
        this.documentFetcherPort = Objects.requireNonNullElse(documentFetcherPort, DocumentFetcherPort.unsupported());
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
        try {
            ensureRawBytes(safeContext);
            if (!hasText(safeContext.getMimeType())) {
                safeContext.setMimeType(detectMimeType(resolveFileName(safeContext)));
            }
            return NodeResult.ok("fetched " + safeContext.getRawBytes().length + " bytes");
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    private void ensureRawBytes(IngestionContext context) {
        byte[] rawBytes = context.getRawBytes();
        if (rawBytes != null && rawBytes.length > 0) {
            return;
        }
        DocumentFetchRequest request = toFetchRequest(context);
        if (!documentFetcherPort.supports(request.sourceType())) {
            throw new IllegalArgumentException("unsupported document source type: " + request.sourceType());
        }
        DocumentFetchResult result = documentFetcherPort.fetch(request);
        byte[] fetched = result.content();
        if (fetched.length == 0) {
            throw new IllegalArgumentException("fetched content must not be empty");
        }
        context.setRawBytes(fetched);
        if (hasText(result.mimeType())) {
            context.setMimeType(result.mimeType());
        }
        if (hasText(result.fileName())) {
            putMetadata(context, KEY_FILE_NAME, result.fileName());
        }
    }

    private String resolveFileName(IngestionContext context) {
        Map<String, Object> metadata = context.getMetadata();
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get(KEY_FILE_NAME);
        return value == null ? "" : String.valueOf(value);
    }

    private DocumentFetchRequest toFetchRequest(IngestionContext context) {
        Object source = context.getSource();
        if (source == null && context.getMetadata() != null) {
            source = context.getMetadata().get(KEY_SOURCE);
        }
        if (source instanceof IngestionDocumentSource documentSource) {
            return new DocumentFetchRequest(
                    documentSource.type(),
                    documentSource.location(),
                    documentSource.fileName(),
                    documentSource.credentials());
        }
        if (source instanceof Map<?, ?> sourceMap) {
            return fromMap(sourceMap);
        }
        throw new IllegalArgumentException("document source must not be empty");
    }

    private DocumentFetchRequest fromMap(Map<?, ?> sourceMap) {
        return new DocumentFetchRequest(
                stringValue(sourceMap.get("type")),
                stringValue(sourceMap.get("location")),
                stringValue(sourceMap.get("fileName")),
                toCredentials(sourceMap.get("credentials")));
    }

    private Map<String, String> toCredentials(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> credentials = new LinkedHashMap<>();
        rawMap.forEach((key, rawValue) -> {
            if (key != null && rawValue != null) {
                credentials.put(String.valueOf(key), String.valueOf(rawValue));
            }
        });
        return credentials;
    }

    private void putMetadata(IngestionContext context, String key, Object value) {
        Map<String, Object> metadata = context.getMetadata();
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        } else {
            metadata = new LinkedHashMap<>(metadata);
        }
        metadata.put(key, value);
        context.setMetadata(metadata);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String detectMimeType(String fileName) {
        String lower = Objects.requireNonNullElse(fileName, "").toLowerCase(Locale.ROOT);
        return EXTENSION_MIME_TYPES.entrySet()
                .stream()
                .filter(entry -> lower.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("application/octet-stream");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
