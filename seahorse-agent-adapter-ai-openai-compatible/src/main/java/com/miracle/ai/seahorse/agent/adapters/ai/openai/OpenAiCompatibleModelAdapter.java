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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * OpenAI-compatible HTTP 模型 adapter。
 *
 * <p>该 adapter 覆盖 Chat、Streaming Chat、Embedding 和模型发现端口，适配 OpenAI、百炼、
 * SiliconFlow 等兼容 /chat/completions 与 /embeddings 协议的供应商。
 */
public class OpenAiCompatibleModelAdapter implements ChatModelPort, StreamingChatModelPort,
        EmbeddingModelPort, RerankModelPort, ModelProviderPort, TokenCounterPort, ModelHealthPort,
        ImageGenerationPort {

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
    private final Executor streamingExecutor;

    public OpenAiCompatibleModelAdapter(
            OkHttpClient httpClient, ObjectMapper objectMapper, OpenAiCompatibleModelProperties properties) {
        this(httpClient, objectMapper, properties, ForkJoinPool.commonPool());
    }

    public OpenAiCompatibleModelAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            OpenAiCompatibleModelProperties properties,
            Executor streamingExecutor) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.streamingExecutor = Objects.requireNonNull(streamingExecutor, "streamingExecutor must not be null");
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
        Map<String, Object> payload = chatPayload(request, request == null ? null : request.getModelId(), true);
        Call call = httpClient.newCall(httpRequest("/chat/completions", payload));
        // SSE readLine 是阻塞 I/O，必须交给可治理的专用 executor，避免占用公共 ForkJoinPool。
        // 捕获当前线程的租户上下文，在异步线程中恢复，防止跨租户数据泄漏
        String capturedCtx = TenantContext.capture();
        CompletableFuture.runAsync(() -> {
            TenantContext.restore(capturedCtx);
            try {
                consumeStream(call, safeCallback);
            } finally {
                TenantContext.clear();
            }
        }, streamingExecutor);
        return call::cancel;
    }

    @Override
    public StreamCancellationHandle streamChatWithTools(
            ChatRequest request,
            StreamCallback callback,
            ToolCallCollector toolCallCollector) {
        StreamCallback safeCallback = Objects.requireNonNull(callback, "callback must not be null");
        ToolCallCollector safeCollector = Objects.requireNonNullElseGet(toolCallCollector, ToolCallCollector::noop);
        Map<String, Object> payload = chatPayload(request, request == null ? null : request.getModelId(), true);
        Call call = httpClient.newCall(httpRequest("/chat/completions", payload));
        String capturedCtx2 = TenantContext.capture();
        CompletableFuture.runAsync(() -> {
            TenantContext.restore(capturedCtx2);
            try {
                consumeStreamWithTools(call, safeCallback, safeCollector);
            } finally {
                TenantContext.clear();
            }
        }, streamingExecutor);
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
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        ImageGenerationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String model = resolveImageModel(safeRequest.model());
        JsonNode response = executeImageGeneration(safeRequest, model);
        JsonNode first = response.path("data").path(0);
        String imageUrl = first.path("url").asText("");
        String b64Json = first.path("b64_json").asText("");
        return ImageGenerationResult.generated(safeRequest.prompt(), model, imageUrl, b64Json, "image/png");
    }

    private JsonNode executeImageGeneration(ImageGenerationRequest request, String model) {
        Map<String, Object> payload = imageGenerationPayload(request, model, request.responseFormat());
        try {
            return executeJson("/images/generations", payload);
        } catch (IllegalStateException ex) {
            if (!shouldRetryWithoutImageResponseFormat(request.responseFormat(), ex)) {
                throw ex;
            }
            return executeJson("/images/generations", imageGenerationPayload(request, model, "url"));
        }
    }

    private Map<String, Object> imageGenerationPayload(ImageGenerationRequest request, String model,
                                                       String responseFormat) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("prompt", request.prompt());
        payload.put("size", request.size());
        putImageResponseFormat(payload, responseFormat);
        if (request.style() != null && !request.style().isBlank()) {
            payload.put("style", request.style());
        }
        return payload;
    }

    private void putImageResponseFormat(Map<String, Object> payload, String responseFormat) {
        String normalized = Objects.requireNonNullElse(responseFormat, "").trim();
        if ("b64_json".equals(normalized)) {
            payload.put("response_format", normalized);
        }
    }

    private boolean shouldRetryWithoutImageResponseFormat(String responseFormat, RuntimeException error) {
        if (!"b64_json".equals(Objects.requireNonNullElse(responseFormat, "").trim())) {
            return false;
        }
        String message = Objects.requireNonNullElse(error.getMessage(), "").toLowerCase(Locale.ROOT);
        return message.contains("response_format") && message.contains("unsupported");
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
        validateToolChoice(safeRequest);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveChatModel(modelId));
        payload.put("messages", messages(safeRequest.getMessages()));
        payload.put("stream", stream);
        if (stream) {
            payload.put("stream_options", Map.of("include_usage", true));
        }
        putIfPresent(payload, "temperature", safeRequest.getTemperature());
        putIfPresent(payload, "top_p", safeRequest.getTopP());
        putIfPresent(payload, "top_k", safeRequest.getTopK());
        putIfPresent(payload, "max_tokens", safeRequest.getMaxTokens());
        putIfTrue(payload, "thinking", safeRequest.getThinking());
        if (safeRequest.getTools() != null && !safeRequest.getTools().isEmpty()) {
            payload.put("tools", tools(safeRequest.getTools()));
            payload.put("tool_choice", safeRequest.getToolChoice());
        }
        return payload;
    }

    private void validateToolChoice(ChatRequest request) {
        if ("required".equalsIgnoreCase(request.getToolChoice())
                && (request.getTools() == null || request.getTools().isEmpty())) {
            throw new IllegalArgumentException("tool_choice=required requires at least one tool");
        }
    }

    private List<Map<String, Object>> messages(List<ChatMessage> messages) {
        List<Map<String, Object>> payload = new ArrayList<>();
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

    private Map<String, Object> message(ChatMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.getRole() == null ? "user" : message.getRole().name().toLowerCase(Locale.ROOT));
        payload.put("content", Objects.requireNonNullElse(message.getContent(), ""));
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            payload.put("tool_calls", toolCalls(message.getToolCalls()));
        }
        if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
            payload.put("tool_call_id", message.getToolCallId());
        }
        return payload;
    }

    private List<Map<String, Object>> tools(List<ToolDescriptor> tools) {
        return tools.stream()
                .filter(Objects::nonNull)
                .map(this::tool)
                .toList();
    }

    private Map<String, Object> tool(ToolDescriptor descriptor) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", descriptor.toolId());
        function.put("description", descriptor.description());
        function.put("parameters", jsonObject(descriptor.jsonSchema()));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private List<Map<String, Object>> toolCalls(List<AgentToolCall> toolCalls) {
        return toolCalls.stream()
                .filter(Objects::nonNull)
                .map(this::toolCall)
                .toList();
    }

    private Map<String, Object> toolCall(AgentToolCall toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.toolId());
        function.put("arguments", serialize(toolCall.arguments()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", toolCall.id());
        payload.put("type", "function");
        payload.put("function", function);
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

    private void consumeStreamWithTools(Call call, StreamCallback callback, ToolCallCollector toolCallCollector) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                callback.onError(new IllegalStateException("OpenAI-compatible stream failed: " + response.code()));
                return;
            }
            consumeStreamBodyWithTools(response.body(), callback, toolCallCollector);
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
        boolean done = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (consumeStreamLine(line, callback)) {
                    done = true;
                    break;
                }
            }
        }
        if (!done) {
            callback.onError(new IOException("OpenAI-compatible stream ended before [DONE]"));
        }
    }

    private void consumeStreamBodyWithTools(
            ResponseBody responseBody,
            StreamCallback callback,
            ToolCallCollector toolCallCollector) throws IOException {
        if (responseBody == null) {
            toolCallCollector.onToolCalls(List.of());
            callback.onComplete();
            return;
        }
        ToolCallAggregation aggregation = new ToolCallAggregation();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (consumeStreamLineWithTools(line, callback, toolCallCollector, aggregation)) {
                    return;
                }
            }
        }
        callback.onError(new IOException("OpenAI-compatible stream ended before [DONE]"));
    }

    private boolean consumeStreamLine(String line, StreamCallback callback) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(SSE_DATA_PREFIX)) {
            return false;
        }
        String data = trimmed.substring(SSE_DATA_PREFIX.length()).trim();
        if (SSE_DONE.equals(data)) {
            callback.onComplete();
            return true;
        }
        consumeStreamDelta(data, callback);
        return false;
    }

    private boolean consumeStreamLineWithTools(
            String line,
            StreamCallback callback,
            ToolCallCollector toolCallCollector,
            ToolCallAggregation aggregation) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(SSE_DATA_PREFIX)) {
            return false;
        }
        String data = trimmed.substring(SSE_DATA_PREFIX.length()).trim();
        if (SSE_DONE.equals(data)) {
            toolCallCollector.onToolCalls(aggregation.toToolCalls());
            callback.onComplete();
            return true;
        }
        return consumeStreamDeltaWithTools(data, callback, aggregation);
    }

    private void consumeStreamDelta(String data, StreamCallback callback) {
        try {
            JsonNode chunk = objectMapper.readTree(data);
            emitUsage(chunk, callback);
            JsonNode delta = chunk.at("/choices/0/delta");
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

    private boolean consumeStreamDeltaWithTools(String data, StreamCallback callback, ToolCallAggregation aggregation) {
        try {
            JsonNode chunk = objectMapper.readTree(data);
            emitUsage(chunk, callback);
            JsonNode delta = chunk.at("/choices/0/delta");
            String content = delta.path("content").asText("");
            if (!content.isBlank()) {
                callback.onContent(content);
            }
            String thinking = firstText(delta, "reasoning_content", "thinking_content");
            if (!thinking.isBlank()) {
                callback.onThinking(thinking);
            }
            JsonNode toolCalls = delta.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode toolCall : toolCalls) {
                    aggregation.accept(toolCall);
                }
            }
            return false;
        } catch (JsonProcessingException ex) {
            callback.onError(ex);
            return true;
        }
    }

    private void emitUsage(JsonNode chunk, StreamCallback callback) {
        JsonNode usage = chunk.path("usage");
        if (!usage.isObject()) {
            return;
        }
        long inputTokens = usage.path("prompt_tokens").asLong(-1L);
        long outputTokens = usage.path("completion_tokens").asLong(-1L);
        if (inputTokens < 0L || outputTokens < 0L) {
            return;
        }
        callback.onUsage(new ChatTokenUsage(inputTokens, outputTokens));
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

    private JsonNode jsonObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(Objects.requireNonNullElse(json, "{}"));
            if (!node.isObject()) {
                throw new IllegalArgumentException("OpenAI tool parameters must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("parse OpenAI tool parameters failed", ex);
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

    private String resolveImageModel(String modelId) {
        String model = Objects.requireNonNullElse(modelId, "").trim();
        if (!model.isBlank()) {
            return model;
        }
        return requireText(properties.defaultImageModel(), "defaultImageModel");
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private void putIfTrue(Map<String, Object> payload, String key, Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            payload.put(key, true);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private final class ToolCallAggregation {
        private final Map<Integer, ToolCallAccumulator> calls = new LinkedHashMap<>();

        private void accept(JsonNode node) {
            int index = node.path("index").asInt(calls.size());
            ToolCallAccumulator call = calls.computeIfAbsent(index, ignored -> new ToolCallAccumulator());
            if (node.hasNonNull("id")) {
                call.id = node.path("id").asText();
            }
            JsonNode function = node.path("function");
            if (function.hasNonNull("name")) {
                call.name = function.path("name").asText();
            }
            if (function.hasNonNull("arguments")) {
                call.arguments.append(function.path("arguments").asText());
            }
        }

        private List<AgentToolCall> toToolCalls() {
            return calls.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .filter(ToolCallAccumulator::complete)
                    .map(ToolCallAccumulator::toToolCall)
                    .toList();
        }
    }

    private final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private boolean complete() {
            return id != null && !id.isBlank() && name != null && !name.isBlank();
        }

        private AgentToolCall toToolCall() {
            return AgentToolCall.of(id, name, arguments(arguments.toString()));
        }
    }

    private Map<String, Object> arguments(String arguments) {
        String safeArguments = Objects.requireNonNullElse(arguments, "");
        if (safeArguments.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(safeArguments);
            if (node.isObject()) {
                return objectMapper.convertValue(node, new TypeReference<>() {
                });
            }
            return Map.of("_raw", safeArguments);
        } catch (JsonProcessingException ex) {
            return Map.of("_raw", safeArguments);
        }
    }
}
