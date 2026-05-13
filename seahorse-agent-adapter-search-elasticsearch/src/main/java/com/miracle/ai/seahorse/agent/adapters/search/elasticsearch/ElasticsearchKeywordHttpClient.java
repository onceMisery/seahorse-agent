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

package com.miracle.ai.seahorse.agent.adapters.search.elasticsearch;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Elasticsearch REST 调用封装。
 *
 * <p>适配层统一处理认证、URL 拼接和错误响应，业务 adapter 只负责构造领域相关 JSON。
 */
final class ElasticsearchKeywordHttpClient {

    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    static final MediaType NDJSON = MediaType.parse("application/x-ndjson; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ElasticsearchKeywordProperties properties;

    ElasticsearchKeywordHttpClient(OkHttpClient httpClient, ElasticsearchKeywordProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null")
                .newBuilder()
                .callTimeout(this.properties.requestTimeout())
                .build();
    }

    String postJson(String pathSuffix, String body) {
        return execute(post(pathSuffix, body, JSON));
    }

    String postNdjson(String pathSuffix, String body) {
        return execute(post(pathSuffix, body, NDJSON));
    }

    String putJson(String pathSuffix, String body) {
        Request.Builder builder = new Request.Builder()
                .url(url(pathSuffix))
                .put(RequestBody.create(Objects.requireNonNullElse(body, ""), JSON));
        addAuth(builder);
        return execute(builder.build());
    }

    Request post(String pathSuffix, String body, MediaType mediaType) {
        Request.Builder builder = new Request.Builder()
                .url(url(pathSuffix))
                .post(RequestBody.create(Objects.requireNonNullElse(body, ""), mediaType));
        addAuth(builder);
        return builder.build();
    }

    private String execute(Request request) {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseText = responseBody(response);
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Elasticsearch request failed: status="
                        + response.code() + ", body=" + responseText);
            }
            return responseText;
        } catch (IOException ex) {
            throw new IllegalStateException("Elasticsearch request failed: " + request.url(), ex);
        }
    }

    private HttpUrl url(String pathSuffix) {
        HttpUrl base = HttpUrl.parse(properties.baseUrl());
        if (base == null) {
            throw new IllegalArgumentException("invalid Elasticsearch baseUrl: " + properties.baseUrl());
        }
        HttpUrl.Builder builder = base.newBuilder().addPathSegment(properties.indexName());
        for (String segment : pathSuffix.split("/")) {
            if (!segment.isBlank()) {
                builder.addPathSegment(segment);
            }
        }
        return builder.build();
    }

    private void addAuth(Request.Builder builder) {
        if (!properties.apiKey().isBlank()) {
            builder.header("Authorization", "ApiKey " + properties.apiKey());
            return;
        }
        if (!properties.username().isBlank()) {
            String token = properties.username() + ":" + properties.password();
            builder.header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private String responseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }
}
