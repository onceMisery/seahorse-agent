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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 LLM 的查询优化器。
 *
 * <p>Phase 3B 实现，通过单次 LLM 调用完成指代消解、多语言保护和术语归一化。
 * 默认关闭，需显式配置 {@code seahorse-agent.query-optimizer.llm-enabled=true}。
 *
 * <p>降级策略：LLM 超时、解析失败或置信度低于阈值时返回 passthrough 结果。
 */
public class LlmQueryOptimizerAdapter implements QueryOptimizerPort {

    private static final Logger LOG = LoggerFactory.getLogger(LlmQueryOptimizerAdapter.class);
    private static final String PROMPT_PATH = "prompt/query-optimizer.st";
    private static final double TEMPERATURE = 0.1D;
    private static final int MAX_HISTORY_CHARS = 500;
    private static final int MAX_MEMORY_CHARS = 300;
    private static final double CONFIDENCE_THRESHOLD = 0.6D;

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ObjectMapper objectMapper;

    public LlmQueryOptimizerAdapter(ChatModelPort chatModelPort,
                                    PromptTemplatePort promptTemplatePort,
                                    ObjectMapper objectMapper) {
        this.chatModelPort = Objects.requireNonNull(chatModelPort, "chatModelPort must not be null");
        this.promptTemplatePort = Objects.requireNonNull(promptTemplatePort, "promptTemplatePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public QueryOptimizationResult optimize(String originalQuestion,
                                            List<ChatMessage> history,
                                            MemoryContext memoryContext) {
        String safeQuestion = Objects.requireNonNullElse(originalQuestion, "");
        if (safeQuestion.isBlank()) {
            return passthrough(safeQuestion);
        }

        try {
            String prompt = buildPrompt(safeQuestion, history, memoryContext);
            String response = chatModelPort.chat(
                    ChatRequest.builder()
                            .messages(List.of(ChatMessage.user(prompt)))
                            .samplingOptions(ChatSamplingOptions.builder()
                                    .temperature(TEMPERATURE)
                                    .build())
                            .build(),
                    null);
            return parseResponse(response, safeQuestion);
        } catch (Exception ex) {
            LOG.warn("LLM 查询优化失败，降级为 passthrough: question={}", safeQuestion, ex);
            return passthrough(safeQuestion);
        }
    }

    private String buildPrompt(String question, List<ChatMessage> history, MemoryContext memoryContext) {
        String template = promptTemplatePort.load(PROMPT_PATH);
        if (template.isBlank()) {
            return buildFallbackPrompt(question, history, memoryContext);
        }
        return template
                .replace("{originalQuestion}", question)
                .replace("{history}", formatHistory(history))
                .replace("{memoryContext}", formatMemoryContext(memoryContext));
    }

    private String buildFallbackPrompt(String question, List<ChatMessage> history, MemoryContext memoryContext) {
        return """
                优化以下查询，进行指代消解和多语言保护，输出 JSON。

                对话历史：
                %s

                用户记忆：
                %s

                当前问题：%s

                输出格式：{"optimizedQuestion":"...","protectedTerms":{},"expandedTerms":[],"confidence":0.8,"changed":true}
                """.formatted(formatHistory(history), formatMemoryContext(memoryContext), question);
    }

    private String formatHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) return "（无历史）";
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            String role = msg.getRole() == null ? "user" : msg.getRole().name().toLowerCase();
            String line = role + ": " + truncate(msg.getContent(), 100) + "\n";
            if (totalChars + line.length() > MAX_HISTORY_CHARS) break;
            sb.insert(0, line);
            totalChars += line.length();
        }
        return sb.toString();
    }

    private String formatMemoryContext(MemoryContext memoryContext) {
        if (memoryContext == null) return "（无记忆）";
        StringBuilder sb = new StringBuilder();
        appendMemoryLayer(sb, "[画像]", memoryContext.getSemanticMemories());
        appendMemoryLayer(sb, "[近期]", memoryContext.getShortTermMemories());
        return sb.isEmpty() ? "（无记忆）" : sb.toString();
    }

    private void appendMemoryLayer(StringBuilder sb, String prefix, List<MemoryItem> items) {
        if (items == null) return;
        for (MemoryItem item : items) {
            sb.append(prefix).append(" ").append(truncate(item.getContent(), 80)).append("\n");
        }
    }

    private QueryOptimizationResult parseResponse(String response, String originalQuestion) {
        if (response == null || response.isBlank()) {
            return passthrough(originalQuestion);
        }
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            String optimized = root.path("optimizedQuestion").asText(originalQuestion);
            double confidence = root.path("confidence").asDouble(1.0D);
            boolean changed = root.path("changed").asBoolean(false);

            if (confidence < CONFIDENCE_THRESHOLD) {
                LOG.debug("LLM 查询优化置信度过低: confidence={}, threshold={}", confidence, CONFIDENCE_THRESHOLD);
                return passthrough(originalQuestion);
            }

            Map<String, String> protections = parseMap(root.path("protectedTerms"));
            List<String> expandedTerms = parseList(root.path("expandedTerms"));
            List<String> appliedRules = new ArrayList<>();
            if (changed) appliedRules.add("llm_optimization");
            if (!protections.isEmpty()) appliedRules.add("proper_noun_protection");
            if (!expandedTerms.isEmpty()) appliedRules.add("term_expansion");
            if (appliedRules.isEmpty()) appliedRules.add("no_change");

            return new QueryOptimizationResult(
                    originalQuestion,
                    optimized.isBlank() ? originalQuestion : optimized,
                    protections,
                    expandedTerms,
                    appliedRules);
        } catch (Exception ex) {
            LOG.debug("解析 LLM 查询优化结果失败: response={}", response, ex);
            return passthrough(originalQuestion);
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.indexOf("```", start);
            if (end > start) return trimmed.substring(start, end).trim();
        }
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```") + 3;
            int end = trimmed.indexOf("```", start);
            if (end > start) return trimmed.substring(start, end).trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private Map<String, String> parseMap(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
        return result;
    }

    private List<String> parseList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText()));
        return result;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }

    private QueryOptimizationResult passthrough(String question) {
        return new QueryOptimizationResult(question, question, Map.of(), List.of(), List.of("passthrough"));
    }
}
