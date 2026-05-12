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
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnhancementPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生文档增强节点。
 */
public class EnhancerNodeFeature implements IngestionNodeFeature {

    public static final String NODE_TYPE = "enhancer";
    private static final String KEY_MODEL_ID = "modelId";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final String KEY_USER_PROMPT_TEMPLATE = "userPromptTemplate";

    private final ChatModelPort chatModelPort;
    private final EnhancementPromptPort promptPort;

    public EnhancerNodeFeature(ChatModelPort chatModelPort, EnhancementPromptPort promptPort) {
        this.chatModelPort = Objects.requireNonNullElse(chatModelPort, ChatModelPort.noop());
        this.promptPort = Objects.requireNonNullElse(promptPort, EnhancementPromptPort.defaults());
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
        List<EnhanceTask> tasks = parseTasks(config);
        if (tasks.isEmpty()) {
            return NodeResult.ok("未配置增强任务");
        }
        ensureMetadata(safeContext);
        String modelId = text(config == null ? null : config.getSettings(), KEY_MODEL_ID);
        for (EnhanceTask task : tasks) {
            runTask(safeContext, modelId, task);
        }
        return NodeResult.ok("增强完成");
    }

    private void runTask(IngestionContext context, String modelId, EnhanceTask task) {
        String input = resolveInputText(context, task.type());
        if (!hasText(input)) {
            return;
        }
        String systemPrompt = hasText(task.systemPrompt())
                ? task.systemPrompt()
                : promptPort.systemPrompt(task.type());
        String userPrompt = hasText(task.userPromptTemplate())
                ? render(task.userPromptTemplate(), variables(input, context))
                : input;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(Objects.requireNonNullElse(systemPrompt, "")),
                        ChatMessage.user(userPrompt)))
                .build();
        applyTaskResult(context, task.type(), chatModelPort.chat(request, modelId));
    }

    private List<EnhanceTask> parseTasks(NodeConfig config) {
        JsonNode settings = config == null ? null : config.getSettings();
        JsonNode tasks = settings == null || settings.isNull() ? null : settings.get(KEY_TASKS);
        if (tasks == null || !tasks.isArray()) {
            return List.of();
        }
        List<EnhanceTask> parsed = new ArrayList<>();
        for (JsonNode task : tasks) {
            String type = normalize(text(task, KEY_TYPE));
            if (hasText(type)) {
                parsed.add(new EnhanceTask(
                        type,
                        text(task, KEY_SYSTEM_PROMPT),
                        text(task, KEY_USER_PROMPT_TEMPLATE)));
            }
        }
        return parsed;
    }

    private String resolveInputText(IngestionContext context, String type) {
        if ("context_enhance".equals(type)) {
            return context.getRawText();
        }
        return hasText(context.getEnhancedText()) ? context.getEnhancedText() : context.getRawText();
    }

    private void applyTaskResult(IngestionContext context, String type, String response) {
        switch (type) {
            case "context_enhance" -> context.setEnhancedText(cleanText(response));
            case "keywords" -> context.setKeywords(IngestionJsonSupport.parseStringList(response));
            case "questions" -> context.setQuestions(IngestionJsonSupport.parseStringList(response));
            case "metadata" -> context.getMetadata().putAll(IngestionJsonSupport.parseObject(response));
            default -> {
            }
        }
    }

    private Map<String, Object> variables(String input, IngestionContext context) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("text", input);
        variables.put("content", input);
        variables.put("mimeType", context.getMimeType());
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

    private void ensureMetadata(IngestionContext context) {
        if (context.getMetadata() == null) {
            context.setMetadata(new LinkedHashMap<>());
        } else if (!(context.getMetadata() instanceof LinkedHashMap)) {
            context.setMetadata(new LinkedHashMap<>(context.getMetadata()));
        }
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

    private record EnhanceTask(String type, String systemPrompt, String userPromptTemplate) {
    }
}
