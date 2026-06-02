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

package com.miracle.ai.seahorse.agent.adapters.web;

import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生 RAG 设置查询 Web adapter。
 *
 * <p>该接口保持旧 `/rag/settings` 返回结构，直接从 Spring Environment 读取 Seahorse 原生配置键，
 *
 */
@RestController
public class SeahorseRagSettingsController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final Environment environment;

    public SeahorseRagSettingsController(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @GetMapping("/rag/settings")
    public Map<String, Object> settings() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, Map.of(
                "upload", uploadSettings(),
                "rag", ragSettings(),
                "ai", bindAiSettings()));
    }

    private Map<String, Object> uploadSettings() {
        return Map.of(
                "maxFileSize", dataSizeBytes("spring.servlet.multipart.max-file-size", "50MB"),
                "maxRequestSize", dataSizeBytes("spring.servlet.multipart.max-request-size", "100MB"));
    }

    private Map<String, Object> ragSettings() {
        return Map.of(
                "defaultConfig", Map.of(
                        "collectionName", stringValue(
                                "seahorse-agent.adapters.vector.collection-name", ""),
                        "dimension", intValue("seahorse-agent.adapters.vector.dimension", 1024),
                        "metricType", stringValue(
                                "seahorse-agent.adapters.vector.metric-type", "COSINE")),
                "queryRewrite", Map.of(
                        "enabled", booleanValue(
                                "seahorse-agent.plugins.query-rewrite.enabled", true)),
                "rateLimit", Map.of(
                        "global", Map.of(
                                "enabled", booleanValue("seahorse-agent.web.chat-rate-limit.enabled", true),
                                "maxConcurrent", intValue("seahorse-agent.web.chat-rate-limit.permits", 50),
                                "maxWaitSeconds", intValue("seahorse-agent.web.chat-rate-limit.max-wait-seconds", 20),
                                "leaseSeconds", intValue("seahorse-agent.web.chat-rate-limit.lease-seconds", 600),
                                "pollIntervalMs", intValue("seahorse-agent.web.chat-rate-limit.poll-interval-ms", 200))),
                "memory", Map.of(
                        "historyKeepTurns", intValue("seahorse-agent.plugins.memory.history-keep-turns",
                                8),
                        "summaryEnabled", booleanValue("seahorse-agent.plugins.memory.summary-enabled",
                                false),
                        "summaryStartTurns", intValue("seahorse-agent.plugins.memory.summary-start-turns",
                                9),
                        "summaryMaxChars", intValue("seahorse-agent.plugins.memory.summary-max-chars",
                                200),
                        "titleMaxLength", intValue("seahorse-agent.plugins.memory.title-max-length",
                                30)));
    }

    private AiSettings bindAiSettings() {
        AiSettings settings = Binder.get(environment)
                .bind("seahorse-agent.adapters.ai", AiSettings.class)
                .orElseGet(AiSettings::new);
        if (settings.getProviders() == null) {
            settings.setProviders(Map.of());
        }
        if (settings.getChat() == null) {
            settings.setChat(new ModelGroup());
        }
        if (settings.getEmbedding() == null) {
            settings.setEmbedding(new ModelGroup());
        }
        if (settings.getRerank() == null) {
            settings.setRerank(new ModelGroup());
        }
        if (settings.getSelection() == null) {
            Selection selection = new Selection();
            selection.setFailureThreshold(3);
            selection.setOpenDurationMs(30_000L);
            settings.setSelection(selection);
        }
        if (settings.getStream() == null) {
            StreamSettings stream = new StreamSettings();
            stream.setMessageChunkSize(20);
            settings.setStream(stream);
        }
        return settings;
    }

    private long dataSizeBytes(String key, String defaultValue) {
        return DataSize.parse(stringValue(key, defaultValue)).toBytes();
    }

    private String stringValue(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    private boolean booleanValue(String key, boolean defaultValue) {
        return environment.getProperty(key, Boolean.class, defaultValue);
    }

    private int intValue(String key, int defaultValue) {
        return environment.getProperty(key, Integer.class, defaultValue);
    }

    private long longValue(String key, long defaultValue) {
        return environment.getProperty(key, Long.class, defaultValue);
    }

    public static class AiSettings {

        private Map<String, ProviderConfig> providers;
        private ModelGroup chat;
        private ModelGroup embedding;
        private ModelGroup rerank;
        private Selection selection;
        private StreamSettings stream;

        public Map<String, ProviderConfig> getProviders() {
            return providers;
        }

        public void setProviders(Map<String, ProviderConfig> providers) {
            this.providers = providers;
        }

        public ModelGroup getChat() {
            return chat;
        }

        public void setChat(ModelGroup chat) {
            this.chat = chat;
        }

        public ModelGroup getEmbedding() {
            return embedding;
        }

        public void setEmbedding(ModelGroup embedding) {
            this.embedding = embedding;
        }

        public ModelGroup getRerank() {
            return rerank;
        }

        public void setRerank(ModelGroup rerank) {
            this.rerank = rerank;
        }

        public Selection getSelection() {
            return selection;
        }

        public void setSelection(Selection selection) {
            this.selection = selection;
        }

        public StreamSettings getStream() {
            return stream;
        }

        public void setStream(StreamSettings stream) {
            this.stream = stream;
        }
    }

    public static class ProviderConfig {

        private String url;
        private String apiKey;
        private Map<String, String> endpoints;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public boolean isApiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        public Map<String, String> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(Map<String, String> endpoints) {
            this.endpoints = endpoints;
        }
    }

    public static class ModelGroup {

        private String defaultModel;
        private String deepThinkingModel;
        private java.util.List<ModelCandidate> candidates = java.util.List.of();

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public String getDeepThinkingModel() {
            return deepThinkingModel;
        }

        public void setDeepThinkingModel(String deepThinkingModel) {
            this.deepThinkingModel = deepThinkingModel;
        }

        public java.util.List<ModelCandidate> getCandidates() {
            return candidates;
        }

        public void setCandidates(java.util.List<ModelCandidate> candidates) {
            this.candidates = candidates == null ? java.util.List.of() : candidates;
        }
    }

    public static class ModelCandidate {

        private String id;
        private String provider;
        private String model;
        private String url;
        private Integer dimension;
        private Integer priority;
        private Boolean enabled;
        private Boolean supportsThinking;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getSupportsThinking() {
            return supportsThinking;
        }

        public void setSupportsThinking(Boolean supportsThinking) {
            this.supportsThinking = supportsThinking;
        }
    }

    public static class Selection {

        private Integer failureThreshold;
        private Long openDurationMs;

        public Integer getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(Integer failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public Long getOpenDurationMs() {
            return openDurationMs;
        }

        public void setOpenDurationMs(Long openDurationMs) {
            this.openDurationMs = openDurationMs;
        }
    }

    public static class StreamSettings {

        private Integer messageChunkSize;

        public Integer getMessageChunkSize() {
            return messageChunkSize;
        }

        public void setMessageChunkSize(Integer messageChunkSize) {
            this.messageChunkSize = messageChunkSize;
        }
    }
}
