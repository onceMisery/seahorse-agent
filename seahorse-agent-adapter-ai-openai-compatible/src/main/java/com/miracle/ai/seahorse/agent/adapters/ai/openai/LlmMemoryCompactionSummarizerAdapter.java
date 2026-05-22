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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionFragment;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummarizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM-backed compaction summarizer.
 *
 * <p>The adapter only proposes the master memory text. The kernel compaction service still owns persistence,
 * lifecycle status changes, and derived index outbox tasks.
 */
public class LlmMemoryCompactionSummarizerAdapter implements MemoryCompactionSummarizerPort {

    private static final Logger LOG = LoggerFactory.getLogger(LlmMemoryCompactionSummarizerAdapter.class);
    private static final String PROMPT_PATH = "prompt/memory-compaction-summarizer.st";
    private static final double TEMPERATURE = 0.1D;
    private static final int MAX_FRAGMENT_CONTENT_CHARS = 1200;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public LlmMemoryCompactionSummarizerAdapter(ChatModelPort chatModelPort,
                                                PromptTemplatePort promptTemplatePort,
                                                ObjectMapper objectMapper) {
        this(chatModelPort, promptTemplatePort, objectMapper, "");
    }

    public LlmMemoryCompactionSummarizerAdapter(ChatModelPort chatModelPort,
                                                PromptTemplatePort promptTemplatePort,
                                                ObjectMapper objectMapper,
                                                String modelId) {
        this.chatModelPort = Objects.requireNonNull(chatModelPort, "chatModelPort must not be null");
        this.promptTemplatePort = Objects.requireNonNull(promptTemplatePort, "promptTemplatePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.modelId = Objects.requireNonNullElse(modelId, "").trim();
    }

    @Override
    public MemoryCompactionSummary summarize(MemoryCompactionCandidate candidate) {
        MemoryCompactionCandidate safeCandidate = Objects.requireNonNull(candidate, "candidate must not be null");
        try {
            String response = chatModelPort.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(buildPrompt(safeCandidate))))
                    .samplingOptions(ChatSamplingOptions.builder()
                            .temperature(TEMPERATURE)
                            .build())
                    .build(), modelId);
            return parseResponse(response);
        } catch (RuntimeException ex) {
            LOG.debug("LLM memory compaction summarizer failed", ex);
            return MemoryCompactionSummary.empty();
        }
    }

    private String buildPrompt(MemoryCompactionCandidate candidate) {
        String template = promptTemplatePort.load(PROMPT_PATH);
        if (template == null || template.isBlank()) {
            return buildFallbackPrompt(candidate);
        }
        return template
                .replace("{tenantId}", candidate.tenantId())
                .replace("{userId}", candidate.userId())
                .replace("{groupKey}", candidate.groupKey())
                .replace("{strategy}", candidate.strategy())
                .replace("{fragments}", fragments(candidate));
    }

    private String buildFallbackPrompt(MemoryCompactionCandidate candidate) {
        return """
                You are Seahorse Agent's memory compaction summarizer. Merge related memory fragments into one durable
                master memory. Return strict JSON only. Do not write explanations outside JSON.

                Context:
                tenantId: %s
                userId: %s
                groupKey: %s
                compactionStrategy: %s

                Fragments:
                %s

                JSON schema:
                {
                  "content": "merged durable memory",
                  "strategy": "llm:fact_compaction",
                  "metadata": {
                    "confidenceLevel": 0.0,
                    "sourceCount": 0
                  }
                }

                Rules:
                - Preserve stable facts only.
                - Do not invent facts that are not present in the fragments.
                - Resolve repetition and obsolete phrasing into a concise master memory.
                - Do not create or name any memory layer.
                - Return empty content only when the fragments cannot be safely merged.
                """.formatted(
                candidate.tenantId(),
                candidate.userId(),
                candidate.groupKey(),
                candidate.strategy(),
                fragments(candidate));
    }

    private String fragments(MemoryCompactionCandidate candidate) {
        List<Map<String, Object>> values = candidate.fragments().stream()
                .map(this::fragment)
                .toList();
        return writeJsonValue(values);
    }

    private Map<String, Object> fragment(MemoryCompactionFragment fragment) {
        return Map.of(
                "memoryId", fragment.memoryId(),
                "layer", fragment.layer(),
                "type", fragment.type(),
                "content", truncate(fragment.content(), MAX_FRAGMENT_CONTENT_CHARS),
                "metadata", fragment.metadata(),
                "updatedAt", fragment.updatedAt().toString());
    }

    private MemoryCompactionSummary parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return MemoryCompactionSummary.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            String content = root.path("content").asText("");
            if (content.isBlank()) {
                return MemoryCompactionSummary.empty();
            }
            return new MemoryCompactionSummary(
                    content,
                    root.path("strategy").asText("llm:fact_compaction"),
                    parseObject(root.path("metadata")));
        } catch (Exception ex) {
            LOG.debug("Failed to parse LLM memory compaction response: {}", response, ex);
            return MemoryCompactionSummary.empty();
        }
    }

    private Map<String, Object> parseObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```") + 3;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String writeJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(value, List.of()));
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLen) {
        String normalized = Objects.requireNonNullElse(value, "");
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }
}
