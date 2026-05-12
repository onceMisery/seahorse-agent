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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible HTTP 模型 adapter。
 *
 * <p>该 adapter 覆盖 Chat、Streaming Chat、Embedding 和模型发现端口，适配 OpenAI、百炼、
 * SiliconFlow 等兼容 /chat/completions 与 /embeddings 协议的供应商。
 */
public class OpenAiCompatibleModelAdapter implements ChatModelPort, StreamingChatModelPort,
        EmbeddingModelPort, RerankModelPort, ModelProviderPort, TokenCounterPort, ModelHealthPort {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String VALUE_APPLICATION_JSON = "application/json";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";
    private static final String CAPABILITY_EMBEDDING = "embedding";
    private static final String CAPABILITY_CHAT = "chat";
    private static final String CAPABILITY_RERANK = "rerank";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleModelProperties properties;

    public OpenAiCompatibleModelAdapter(
            OkHttpClient httpClient, ObjectMapper objectMapper, OpenAiCompatibleModelProperties properties) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String chat(ChatRequest request, String modelId) {
        Map<String, Object> payload = chatPayload(request, modelId, false);
        JsonNode response = executeJson("/chat/completions", payload);
        return response.at("/choices/0/message/content").asText("");
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        StreamCallback safeCallback = Objects.requireNonNull(callback, "callback must not be null");
        Map<String, Object> payload = chatPayload(request, null, true);
        Call call = httpClient.newCall(httpRequest("/chat/completions", payload));
        CompletableFuture.runAsync(() -> consumeStream(call, safeCallback));
        return call::cancel;
    }

    @Override
    public List<Float> embed(String modelId, String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveEmbeddingModel(modelId));
        payload.put("input", Objects.requireNonNullElse(text, ""));
        JsonNode response = executeJson("/embeddings", payload);
        return embeddingValues(response.at("/data/0/embedding"));
    }

    @Override
    public List<com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk> rerank(
            String modelId,
            String query,
            List<com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk> chunks) {
        List<com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk> safeChunks =
                Objects.requireNonNullElse(chunks, List.of());
        if (safeChunks.isEmpty()) {
            return List.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveRerankModel(modelId));
        payload.put("query", Objects.requireNonNullElse(query, ""));
        payload.put("documents", safeChunks.stream()
                .map(chunk -> Objects.requireNonNullElse(chunk.getText(), ""))
                .toList());
        JsonNode response = executeJson("/rerank", payload);
        return applyRerankScores(safeChunks, response.path("results"));
    }

    @Override
    public boolean available(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        List<String> supportedModels = properties.supportedModels();
        return supportedModels.isEmpty() || supportedModels.contains(modelId);
    }

    @Override
    public List<String> listModels(String capability) {
        List<String> supportedModels = properties.supportedModels();
        if (!supportedModels.isEmpty()) {
            return supportedModels;
        }
        String normalized = Objects.requireNonNullElse(capability, "").toLowerCase(Locale.ROOT);
        if (normalized.contains(CAPABILITY_EMBEDDING) && !properties.defaultEmbeddingModel().isBlank()) {
            return List.of(properties.defaultEmbeddingModel());
        }
        if (normalized.contains(CAPABILITY_RERANK) && !properties.defaultRerankModel().isBlank()) {
            return List.of(properties.defaultRerankModel());
        }
        if (normalized.contains(CAPABILITY_CHAT) && !properties.defaultChatModel().isBlank()) {
            return List.of(properties.defaultChatModel());
        }
        return List.of();
    }

    @Override
    public int countTextTokens(String modelId, String text) {
        return TokenCounterPort.approximate().countTextTokens(modelId, text);
    }

    @Override
    public boolean isHealthy(String modelId) {
        return modelId == null || modelId.isBlank() || available(modelId);
    }

    @Override
    public void recordSuccess(String modelId) {
    }

    @Override
    public void recordFailure(String modelId, Throwable error) {
    }

    private Map<String, Object> chatPayload(ChatRequest request, String modelId, boolean stream) {
        ChatRequest safeRequest = Objects.requireNonNullElseGet(request, ChatRequest::new);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveChatModel(modelId));
        payload.put("messages", messages(safeRequest.getMessages()));
        payload.put("stream", stream);
        putIfPresent(payload, "temperature", safeRequest.getTemperature());
        putIfPresent(payload, "top_p", safeRequest.getTopP());
        putIfPresent(payload, "max_tokens", safeRequest.getMaxTokens());
        return payload;
    }

    private List<Map<String, String>> messages(List<ChatMessage> messages) {
        List<Map<String, String>> payload = new ArrayList<>();
        if (messages == null) {
            return payload;
        }
        for (ChatMessage message : messages) {
            if (message != null) {
                payload.add(message(message));
            }
        }
        return payload;
    }

    private Map<String, String> message(ChatMessage message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("role", message.getRole() == null ? "user" : message.getRole().name().toLowerCase(Locale.ROOT));
        payload.put("content", Objects.requireNonNullElse(message.getContent(), ""));
        return payload;
    }

    private JsonNode executeJson(String path, Map<String, Object> payload) {
        try (Response response = httpClient.newCall(httpRequest(path, payload)).execute()) {
            String body = responseBody(response);
            if (!response.isSuccessful()) {
                throw new IllegalStateException("OpenAI-compatible request failed: " + response.code() + ", body=" + body);
            }
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalStateException("OpenAI-compatible request error: " + path, ex);
        }
    }

    private Request httpRequest(String path, Map<String, Object> payload) {
        RequestBody requestBody = RequestBody.create(serialize(payload), JSON);
        Request.Builder builder = new Request.Builder()
                .url(properties.baseUrl() + path)
                .post(requestBody)
                .header(HEADER_CONTENT_TYPE, VALUE_APPLICATION_JSON);
        if (!properties.apiKey().isBlank()) {
            builder.header(HEADER_AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        return builder.build();
    }

    private void consumeStream(Call call, StreamCallback callback) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                callback.onError(new IllegalStateException("OpenAI-compatible stream failed: " + response.code()));
                return;
            }
            consumeStreamBody(response.body(), callback);
        } catch (IOException ex) {
            if (!call.isCanceled()) {
                callback.onError(ex);
            }
        }
    }

    private void consumeStreamBody(ResponseBody responseBody, StreamCallback callback) throws IOException {
        if (responseBody == null) {
            callback.onComplete();
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumeStreamLine(line, callback);
            }
        }
    }

    private void consumeStreamLine(String line, StreamCallback callback) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(SSE_DATA_PREFIX)) {
            return;
        }
        String data = trimmed.substring(SSE_DATA_PREFIX.length()).trim();
        if (SSE_DONE.equals(data)) {
            callback.onComplete();
            return;
        }
        consumeStreamDelta(data, callback);
    }

    private void consumeStreamDelta(String data, StreamCallback callback) {
        try {
            JsonNode delta = objectMapper.readTree(data).at("/choices/0/delta");
            String content = delta.path("content").asText("");
            if (!content.isBlank()) {
                callback.onContent(content);
            }
            String thinking = firstText(delta, "reasoning_content", "thinking_content");
            if (!thinking.isBlank()) {
                callback.onThinking(thinking);
            }
        } catch (JsonProcessingException ex) {
            callback.onError(ex);
        }
    }

    private List<Float> embeddingValues(JsonNode embedding) {
        List<Float> values = new ArrayList<>();
        if (!embedding.isArray()) {
            return values;
        }
        for (JsonNode item : embedding) {
            values.add((float) item.asDouble());
        }
        return values;
    }

    private List<com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk> applyRerankScores(
            List<com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk> chunks,
            JsonNode results) {
        if (!results.isArray()) {
            return List.copyOf(chunks);
        }
        List<com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk> reranked = new ArrayList<>();
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= chunks.size()) {
                continue;
            }
            com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk source = chunks.get(index);
            reranked.add(com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk.builder()
                    .id(source.getId())
                    .text(source.getText())
                    .score((float) item.path("relevance_score").asDouble(
                            item.path("score").asDouble(source.getScore() == null ? 0.0D : source.getScore())))
                    .build());
        }
        return reranked.isEmpty() ? List.copyOf(chunks) : reranked;
    }

    private String responseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            return "";
        }
        return body.string();
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize OpenAI-compatible payload failed", ex);
        }
    }

    private String firstText(JsonNode node, String firstField, String secondField) {
        String first = node.path(firstField).asText("");
        if (!first.isBlank()) {
            return first;
        }
        return node.path(secondField).asText("");
    }

    private String resolveChatModel(String modelId) {
        String model = Objects.requireNonNullElse(modelId, "").trim();
        if (!model.isBlank()) {
            return model;
        }
        return requireText(properties.defaultChatModel(), "defaultChatModel");
    }

    private String resolveEmbeddingModel(String modelId) {
        String model = Objects.requireNonNullElse(modelId, "").trim();
        if (!model.isBlank()) {
            return model;
        }
        return requireText(properties.defaultEmbeddingModel(), "defaultEmbeddingModel");
    }

    private String resolveRerankModel(String modelId) {
        String model = Objects.requireNonNullElse(modelId, "").trim();
        if (!model.isBlank()) {
            return model;
        }
        return requireText(properties.defaultRerankModel(), "defaultRerankModel");
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
