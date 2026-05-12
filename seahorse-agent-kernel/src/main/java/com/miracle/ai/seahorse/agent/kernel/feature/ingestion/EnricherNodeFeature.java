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

import com.fasterxml.jackson.databind.JsonNode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnrichmentPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生 Chunk 富化节点。
 */
public class EnricherNodeFeature implements IngestionNodeFeature {

    public static final String NODE_TYPE = "enricher";
    private static final String KEY_MODEL_ID = "modelId";
    private static final String KEY_ATTACH_DOCUMENT_METADATA = "attachDocumentMetadata";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final String KEY_USER_PROMPT_TEMPLATE = "userPromptTemplate";

    private final ChatModelPort chatModelPort;
    private final EnrichmentPromptPort promptPort;

    public EnricherNodeFeature(ChatModelPort chatModelPort, EnrichmentPromptPort promptPort) {
        this.chatModelPort = Objects.requireNonNullElse(chatModelPort, ChatModelPort.noop());
        this.promptPort = Objects.requireNonNullElse(promptPort, EnrichmentPromptPort.defaults());
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
        List<VectorChunk> chunks = Objects.requireNonNullElse(safeContext.getChunks(), List.of());
        if (chunks.isEmpty()) {
            return NodeResult.ok("No chunks to enrich");
        }
        List<EnrichTask> tasks = parseTasks(config);
        if (tasks.isEmpty()) {
            return NodeResult.ok("No enricher tasks configured");
        }
        String modelId = text(config == null ? null : config.getSettings(), KEY_MODEL_ID);
        boolean attachMetadata = bool(config == null ? null : config.getSettings(), KEY_ATTACH_DOCUMENT_METADATA, true);
        for (VectorChunk chunk : chunks) {
            enrichChunk(safeContext, chunk, modelId, attachMetadata, tasks);
        }
        return NodeResult.ok("Enricher completed");
    }

    private void enrichChunk(IngestionContext context,
                             VectorChunk chunk,
                             String modelId,
                             boolean attachMetadata,
                             List<EnrichTask> tasks) {
        if (chunk == null || !hasText(chunk.getContent())) {
            return;
        }
        ensureChunkMetadata(context, chunk, attachMetadata);
        for (EnrichTask task : tasks) {
            String systemPrompt = hasText(task.systemPrompt())
                    ? task.systemPrompt()
                    : promptPort.systemPrompt(task.type());
            String userPrompt = hasText(task.userPromptTemplate())
                    ? render(task.userPromptTemplate(), variables(chunk, context))
                    : chunk.getContent();
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(Objects.requireNonNullElse(systemPrompt, "")),
                            ChatMessage.user(userPrompt)))
                    .build();
            applyResult(chunk, task.type(), chatModelPort.chat(request, modelId));
        }
    }

    private List<EnrichTask> parseTasks(NodeConfig config) {
        JsonNode settings = config == null ? null : config.getSettings();
        JsonNode tasks = settings == null || settings.isNull() ? null : settings.get(KEY_TASKS);
        if (tasks == null || !tasks.isArray()) {
            return List.of();
        }
        List<EnrichTask> parsed = new ArrayList<>();
        for (JsonNode task : tasks) {
            String type = normalize(text(task, KEY_TYPE));
            if (hasText(type)) {
                parsed.add(new EnrichTask(
                        type,
                        text(task, KEY_SYSTEM_PROMPT),
                        text(task, KEY_USER_PROMPT_TEMPLATE)));
            }
        }
        return parsed;
    }

    private void ensureChunkMetadata(IngestionContext context, VectorChunk chunk, boolean attachMetadata) {
        Map<String, Object> metadata = chunk.getMetadata();
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        } else if (!(metadata instanceof LinkedHashMap)) {
            metadata = new LinkedHashMap<>(metadata);
        }
        if (attachMetadata && context.getMetadata() != null) {
            metadata.putAll(context.getMetadata());
        }
        chunk.setMetadata(metadata);
    }

    private void applyResult(VectorChunk chunk, String type, String response) {
        switch (type) {
            case "keywords" -> chunk.getMetadata().put("keywords", IngestionJsonSupport.parseStringList(response));
            case "summary" -> chunk.getMetadata().put("summary", cleanText(response));
            case "metadata" -> chunk.getMetadata().putAll(IngestionJsonSupport.parseObject(response));
            default -> {
            }
        }
    }

    private Map<String, Object> variables(VectorChunk chunk, IngestionContext context) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("text", chunk.getContent());
        variables.put("content", chunk.getContent());
        variables.put("chunkIndex", chunk.getIndex());
        variables.put("taskId", context.getTaskId());
        variables.put("pipelineId", context.getPipelineId());
        return variables;
    }

    private String render(String template, Map<String, Object> variables) {
        String output = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            output = output.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return output;
    }

    private boolean bool(JsonNode node, String key, boolean defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? defaultValue : value.asBoolean(defaultValue);
    }

    private String cleanText(String response) {
        String value = IngestionJsonSupport.stripMarkdownCodeFence(response);
        return hasText(value) ? value.trim() : value;
    }

    private String text(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return "";
        }
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record EnrichTask(String type, String systemPrompt, String userPromptTemplate) {
    }
}
