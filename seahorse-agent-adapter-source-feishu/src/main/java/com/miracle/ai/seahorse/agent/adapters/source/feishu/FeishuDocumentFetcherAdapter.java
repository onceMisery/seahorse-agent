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

package com.miracle.ai.seahorse.agent.adapters.source.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Feishu/Lark 云文档文件下载适配器。
 */
public class FeishuDocumentFetcherAdapter implements DocumentFetcherPort {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_CONTENT_DISPOSITION = "content-disposition";
    private static final String TYPE_FEISHU = "feishu";
    private static final String TYPE_LARK = "lark";
    private static final String TYPE_FEISHU_DRIVE = "feishu_drive";
    private static final String TYPE_LARK_DRIVE = "lark_drive";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FeishuDocumentSourceProperties properties;

    public FeishuDocumentFetcherAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            FeishuDocumentSourceProperties properties) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public boolean supports(String sourceType) {
        String type = normalize(sourceType);
        return TYPE_FEISHU.equals(type)
                || TYPE_LARK.equals(type)
                || TYPE_FEISHU_DRIVE.equals(type)
                || TYPE_LARK_DRIVE.equals(type);
    }

    @Override
    public DocumentFetchResult fetch(DocumentFetchRequest request) {
        DocumentFetchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (!supports(safeRequest.sourceType())) {
            throw new IllegalArgumentException("unsupported Feishu source type: " + safeRequest.sourceType());
        }
        String accessToken = resolveAccessToken(safeRequest.credentials());
        Request httpRequest = new Request.Builder()
                .url(resolveDownloadUrl(safeRequest))
                .header(HEADER_AUTHORIZATION, "Bearer " + accessToken)
                .get()
                .build();
        return executeDownload(httpRequest, safeRequest);
    }

    private DocumentFetchResult executeDownload(Request httpRequest, DocumentFetchRequest sourceRequest) {
        try (Response response = executeWithRetry(httpRequest)) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("Feishu download failed: HTTP " + response.code());
            }
            String mimeType = response.header(HEADER_CONTENT_TYPE, "");
            String fileName = resolveFileName(sourceRequest, response.header(HEADER_CONTENT_DISPOSITION, ""));
            return new DocumentFetchResult(body.bytes(), mimeType, fileName);
        } catch (IOException ex) {
            throw new IllegalStateException("Feishu download failed: " + sourceRequest.location(), ex);
        }
    }

    private Response executeWithRetry(Request request) throws IOException {
        int attempts = Math.max(1, properties.getMaxAttempts());
        IOException lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Response response = httpClient.newCall(request).execute();
                if (!shouldRetry(response.code()) || attempt == attempts) {
                    return response;
                }
                response.close();
            } catch (IOException ex) {
                lastException = ex;
                if (attempt == attempts) {
                    throw ex;
                }
            }
            backoff();
        }
        throw lastException == null ? new IOException("Feishu request failed") : lastException;
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void backoff() {
        long millis = Math.max(0, properties.getRetryBackoffMillis());
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Feishu retry interrupted", ex);
        }
    }

    private String resolveAccessToken(Map<String, String> credentials) {
        Map<String, String> safeCredentials = new LinkedHashMap<>(Objects.requireNonNullElse(credentials, Map.of()));
        String token = firstText(safeCredentials, "tenant_access_token", "access_token", "tenantAccessToken", "accessToken");
        if (!hasText(token)) {
            token = properties.getTenantAccessToken();
        }
        if (hasText(token)) {
            return stripBearer(token);
        }
        return fetchTenantAccessToken(safeCredentials);
    }

    private String fetchTenantAccessToken(Map<String, String> credentials) {
        String appId = firstText(credentials, "app_id", "appId");
        String appSecret = firstText(credentials, "app_secret", "appSecret");
        if (!hasText(appId)) {
            appId = properties.getAppId();
        }
        if (!hasText(appSecret)) {
            appSecret = properties.getAppSecret();
        }
        if (!hasText(appId) || !hasText(appSecret)) {
            throw new IllegalArgumentException("Feishu app_id/app_secret or tenant_access_token must be configured");
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of("app_id", appId, "app_secret", appSecret));
            Request request = new Request.Builder()
                    .url(resolveUrl(properties.getTenantAccessTokenPath()))
                    .post(RequestBody.create(payload, JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("Feishu token request failed: HTTP " + response.code());
                }
                JsonNode root = objectMapper.readTree(body.string());
                int code = root.path("code").asInt(0);
                if (code != 0) {
                    throw new IllegalStateException("Feishu token request failed: code " + code);
                }
                String token = root.path("tenant_access_token").asText("");
                if (!hasText(token)) {
                    token = root.path("app_access_token").asText("");
                }
                if (!hasText(token)) {
                    throw new IllegalStateException("Feishu token response does not contain access token");
                }
                return token;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Feishu token request failed", ex);
        }
    }

    private String resolveDownloadUrl(DocumentFetchRequest request) {
        String location = requireText(request.location(), "Feishu file token or download URL must not be blank");
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        String encodedToken = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String path = properties.getDownloadPathTemplate().replace("{fileToken}", encodedToken);
        return resolveUrl(path);
    }

    private String resolveUrl(String path) {
        String baseUrl = stripTrailingSlash(requireText(properties.getBaseUrl(), "Feishu baseUrl must not be blank"));
        String safePath = Objects.requireNonNullElse(path, "");
        if (safePath.startsWith("http://") || safePath.startsWith("https://")) {
            return safePath;
        }
        return baseUrl + "/" + trimLeadingSlash(safePath);
    }

    private String resolveFileName(DocumentFetchRequest request, String contentDisposition) {
        if (hasText(request.fileName())) {
            return request.fileName().trim();
        }
        String fromHeader = parseContentDispositionFileName(contentDisposition);
        if (hasText(fromHeader)) {
            return fromHeader;
        }
        return "feishu-document.bin";
    }

    private String parseContentDispositionFileName(String value) {
        if (!hasText(value)) {
            return "";
        }
        String[] parts = value.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("filename=")) {
                return trimmed.substring("filename=".length()).replace("\"", "").trim();
            }
        }
        return "";
    }

    private String firstText(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String stripBearer(String value) {
        String token = value.trim();
        if (token.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return token.substring("bearer ".length()).trim();
        }
        return token;
    }

    private String stripTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trimLeadingSlash(String value) {
        String result = Objects.requireNonNullElse(value, "").trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String sourceType) {
        return Objects.requireNonNullElse(sourceType, "").trim().toLowerCase(Locale.ROOT).replace("-", "_");
    }
}
