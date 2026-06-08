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

package com.miracle.ai.seahorse.agent.adapters.ai.openai;

import java.util.List;
import java.util.Objects;

/**
 * OpenAI-compatible adapter 配置契约。
 *
 * @param baseUrl               API 基础地址，例如 https://api.openai.com/v1
 * @param apiKey                API Key，可为空以支持网关侧鉴权
 * @param defaultChatModel      默认对话模型
 * @param defaultEmbeddingModel 默认 Embedding 模型
 * @param defaultRerankModel    默认 Rerank 模型
 * @param supportedModels       本 adapter 声明支持的模型列表，为空时允许任意非空模型 ID
 */
public record OpenAiCompatibleModelProperties(
        String baseUrl,
        String apiKey,
        String defaultChatModel,
        String defaultEmbeddingModel,
        String defaultRerankModel,
        String defaultImageModel,
        List<String> supportedModels
) {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    public OpenAiCompatibleModelProperties {
        baseUrl = normalizeBaseUrl(Objects.requireNonNullElse(baseUrl, DEFAULT_BASE_URL));
        apiKey = Objects.requireNonNullElse(apiKey, "");
        defaultChatModel = Objects.requireNonNullElse(defaultChatModel, "");
        defaultEmbeddingModel = Objects.requireNonNullElse(defaultEmbeddingModel, "");
        defaultRerankModel = Objects.requireNonNullElse(defaultRerankModel, "");
        defaultImageModel = Objects.requireNonNullElse(defaultImageModel, "");
        supportedModels = List.copyOf(Objects.requireNonNullElse(supportedModels, List.of()));
    }

    public OpenAiCompatibleModelProperties(String baseUrl,
                                           String apiKey,
                                           String defaultChatModel,
                                           String defaultEmbeddingModel,
                                           String defaultRerankModel,
                                           List<String> supportedModels) {
        this(baseUrl, apiKey, defaultChatModel, defaultEmbeddingModel, defaultRerankModel, "", supportedModels);
    }

    public OpenAiCompatibleModelProperties(String baseUrl,
                                           String apiKey,
                                           String defaultChatModel,
                                           String defaultEmbeddingModel,
                                           List<String> supportedModels) {
        this(baseUrl, apiKey, defaultChatModel, defaultEmbeddingModel, "", "", supportedModels);
    }

    private static String normalizeBaseUrl(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed;
        }
        return trimmed + "/v1";
    }
}
