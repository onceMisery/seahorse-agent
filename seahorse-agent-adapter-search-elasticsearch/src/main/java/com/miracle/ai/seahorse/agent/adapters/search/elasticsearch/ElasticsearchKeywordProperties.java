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

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Elasticsearch 关键词检索适配器配置。
 *
 * <p>这里保留 REST 连接必需的最小配置，不把 Elasticsearch SDK 类型暴露到 kernel。
 */
public record ElasticsearchKeywordProperties(
        String baseUrl,
        String indexName,
        List<String> searchFields,
        String analyzer,
        String minimumShouldMatch,
        String apiKey,
        String username,
        String password,
        Duration requestTimeout
) {

    public ElasticsearchKeywordProperties(String baseUrl,
                                          String indexName,
                                          List<String> searchFields,
                                          String apiKey,
                                          String username,
                                          String password,
                                          Duration requestTimeout) {
        this(baseUrl, indexName, searchFields, "", "", apiKey, username, password, requestTimeout);
    }

    public ElasticsearchKeywordProperties {
        baseUrl = hasText(baseUrl) ? trimTrailingSlash(baseUrl.trim()) : "http://localhost:9200";
        indexName = hasText(indexName) ? indexName.trim() : "seahorse_keyword_chunk";
        searchFields = List.copyOf(Objects.requireNonNullElse(searchFields, List.of("content^3")));
        if (searchFields.isEmpty()) {
            searchFields = List.of("content^3");
        }
        analyzer = Objects.requireNonNullElse(analyzer, "").trim();
        minimumShouldMatch = Objects.requireNonNullElse(minimumShouldMatch, "").trim();
        apiKey = Objects.requireNonNullElse(apiKey, "").trim();
        username = Objects.requireNonNullElse(username, "").trim();
        password = Objects.requireNonNullElse(password, "");
        requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? Duration.ofSeconds(10)
                : requestTimeout;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
