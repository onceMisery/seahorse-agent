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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 原生文档拉取适配器。
 *
 * <p>默认支持 text、file、url/http/https 与 object_storage/s3。对象存储类型需要应用侧提供
 * {@link ObjectStoragePort}。
 */
public class LocalDocumentFetcherAdapter implements DocumentFetcherPort {

    private static final String TYPE_TEXT = "text";
    private static final String TYPE_FILE = "file";
    private static final String TYPE_URL = "url";
    private static final String TYPE_HTTP = "http";
    private static final String TYPE_HTTPS = "https";
    private static final String TYPE_S3 = "s3";
    private static final String TYPE_OBJECT_STORAGE = "object_storage";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectStoragePort objectStoragePort;

    public LocalDocumentFetcherAdapter(ObjectStoragePort objectStoragePort) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        this.objectStoragePort = objectStoragePort;
    }

    @Override
    public boolean supports(String sourceType) {
        String type = normalize(sourceType);
        return TYPE_TEXT.equals(type)
                || TYPE_FILE.equals(type)
                || TYPE_URL.equals(type)
                || TYPE_HTTP.equals(type)
                || TYPE_HTTPS.equals(type)
                || TYPE_S3.equals(type)
                || TYPE_OBJECT_STORAGE.equals(type);
    }

    @Override
    public DocumentFetchResult fetch(DocumentFetchRequest request) {
        DocumentFetchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String type = normalize(safeRequest.sourceType());
        if (TYPE_TEXT.equals(type)) {
            return fetchText(safeRequest);
        }
        if (TYPE_FILE.equals(type)) {
            return fetchFile(safeRequest);
        }
        if (TYPE_URL.equals(type) || TYPE_HTTP.equals(type) || TYPE_HTTPS.equals(type)) {
            return fetchHttp(safeRequest);
        }
        return fetchObjectStorage(safeRequest);
    }

    private DocumentFetchResult fetchText(DocumentFetchRequest request) {
        return new DocumentFetchResult(
                request.location().getBytes(StandardCharsets.UTF_8),
                "text/plain",
                resolveFileName(request, "inline.txt"));
    }

    private DocumentFetchResult fetchFile(DocumentFetchRequest request) {
        try {
            Path path = Path.of(requireLocation(request));
            return new DocumentFetchResult(
                    Files.readAllBytes(path),
                    detectMimeType(request, path.getFileName().toString()),
                    resolveFileName(request, path.getFileName().toString()));
        } catch (Exception ex) {
            throw new IllegalStateException("读取本地文件失败：" + request.location(), ex);
        }
    }

    private DocumentFetchResult fetchHttp(DocumentFetchRequest request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requireLocation(request)))
                    .timeout(REQUEST_TIMEOUT)
                    .GET();
            request.credentials().forEach(builder::header);
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP status " + response.statusCode());
            }
            String mimeType = response.headers().firstValue(HEADER_CONTENT_TYPE).orElse("");
            return new DocumentFetchResult(response.body(), mimeType, resolveFileName(request, "download.bin"));
        } catch (Exception ex) {
            throw new IllegalStateException("读取远程文档失败：" + request.location(), ex);
        }
    }

    private DocumentFetchResult fetchObjectStorage(DocumentFetchRequest request) {
        if (objectStoragePort == null) {
            throw new IllegalStateException("未配置对象存储端口，无法读取：" + request.location());
        }
        try (InputStream inputStream = objectStoragePort.openStream(requireLocation(request))) {
            return new DocumentFetchResult(
                    inputStream.readAllBytes(),
                    detectMimeType(request, request.fileName()),
                    resolveFileName(request, "object.bin"));
        } catch (Exception ex) {
            throw new IllegalStateException("读取对象存储文档失败：" + request.location(), ex);
        }
    }

    private String detectMimeType(DocumentFetchRequest request, String fileName) {
        if (hasText(request.fileName())) {
            String byName = java.net.URLConnection.guessContentTypeFromName(request.fileName());
            if (hasText(byName)) {
                return byName;
            }
        }
        String resolved = java.net.URLConnection.guessContentTypeFromName(fileName);
        return hasText(resolved) ? resolved : DEFAULT_MIME_TYPE;
    }

    private String resolveFileName(DocumentFetchRequest request, String fallback) {
        return hasText(request.fileName()) ? request.fileName().trim() : fallback;
    }

    private String requireLocation(DocumentFetchRequest request) {
        if (!hasText(request.location())) {
            throw new IllegalArgumentException("source location must not be blank");
        }
        return request.location().trim();
    }

    private String normalize(String sourceType) {
        return Objects.requireNonNullElse(sourceType, "").trim().toLowerCase(Locale.ROOT).replace("-", "_");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
