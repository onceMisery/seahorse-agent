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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.RefinedMemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * LLM-backed memory refiner.
 *
 * <p>The adapter only returns structured candidate operations. The kernel memory engine still owns schema checks,
 * policy gates, review staging, and persistence.
 */
public class LlmMemoryRefinerAdapter implements MemoryRefinerPort {

    private static final Logger LOG = LoggerFactory.getLogger(LlmMemoryRefinerAdapter.class);
    private static final String PROMPT_PATH = "prompt/memory-refiner.st";
    private static final double TEMPERATURE = 0.1D;
    private static final int MAX_CONTENT_CHARS = 4000;
    private static final int DEFAULT_FEEDBACK_SAMPLE_LIMIT = 3;
    private static final int MAX_FEEDBACK_CONTENT_CHARS = 600;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ObjectMapper objectMapper;
    private final MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort;
    private final int feedbackSampleLimit;

    public LlmMemoryRefinerAdapter(ChatModelPort chatModelPort,
                                   PromptTemplatePort promptTemplatePort,
                                   ObjectMapper objectMapper) {
        this(chatModelPort, promptTemplatePort, objectMapper,
                MemoryReviewFeedbackRepositoryPort.empty(), DEFAULT_FEEDBACK_SAMPLE_LIMIT);
    }

    public LlmMemoryRefinerAdapter(ChatModelPort chatModelPort,
                                   PromptTemplatePort promptTemplatePort,
                                   ObjectMapper objectMapper,
                                   MemoryReviewFeedbackRepositoryPort feedbackRepositoryPort,
                                   int feedbackSampleLimit) {
        this.chatModelPort = Objects.requireNonNull(chatModelPort, "chatModelPort must not be null");
        this.promptTemplatePort = Objects.requireNonNull(promptTemplatePort, "promptTemplatePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.feedbackRepositoryPort = Objects.requireNonNullElseGet(
                feedbackRepositoryPort, MemoryReviewFeedbackRepositoryPort::empty);
        this.feedbackSampleLimit = Math.max(0, feedbackSampleLimit);
    }

    @Override
    public MemoryRefinementResult refine(MemoryRefinementRequest request) {
        MemoryRefinementRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try {
            String response = chatModelPort.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(buildPrompt(safeRequest))))
                    .samplingOptions(ChatSamplingOptions.builder()
                            .temperature(TEMPERATURE)
                            .build())
                    .build(), null);
            return parseResponse(response);
        } catch (RuntimeException ex) {
            LOG.debug("LLM memory refiner failed", ex);
            return MemoryRefinementResult.empty("llm_refiner_failed");
        }
    }

    private String buildPrompt(MemoryRefinementRequest request) {
        String template = promptTemplatePort.load(PROMPT_PATH);
        if (template == null || template.isBlank()) {
            return buildFallbackPrompt(request);
        }
        return template
                .replace("{operationId}", request.operationId())
                .replace("{tenantId}", request.tenantId())
                .replace("{source}", request.source())
                .replace("{userId}", request.userId())
                .replace("{conversationId}", request.conversationId())
                .replace("{messageId}", request.messageId())
                .replace("{sanitizedContent}", truncate(request.sanitizedContent(), MAX_CONTENT_CHARS))
                .replace("{baselineAction}", request.baselineAction().name())
                .replace("{baselineMemoryType}", request.baselineMemoryType())
                .replace("{baselineReason}", request.baselineReason())
                .replace("{baselineDetails}", writeJson(request.baselineDetails()))
                .replace("{existingMemories}", existingMemories(request))
                .replace("{stickyAnchors}", stickyAnchors(request))
                .replace("{referenceZone}", zoneOrEmpty(request.referenceZone()))
                .replace("{targetZone}", zoneOrEmpty(request.targetZone()))
                .replace("{feedbackSamples}", feedbackSamples(request));
    }

    private String buildFallbackPrompt(MemoryRefinementRequest request) {
        return """
                You are Seahorse Agent's memory refiner. Convert the user message into structured memory operations.
                Return strict JSON only. Do not write explanations outside JSON.

                Context:
                operationId: %s
                tenantId: %s
                source: %s
                userId: %s
                conversationId: %s
                messageId: %s
                baselineAction: %s
                baselineMemoryType: %s
                baselineReason: %s
                baselineDetails: %s

                Current existing memories:
                %s

                Must-hold constraints:
                %s

                Reference Zone:
                %s

                Target Zone:
                %s

                Human review feedback examples:
                %s

                Sanitized user content:
                %s

                JSON schema:
                {
                  "refined": true,
                  "reason": "short reason",
                  "operations": [
                    {
                      "action": "ADD|UPDATE|DELETE|IGNORE|REVIEW",
                      "targetKind": "FACT|PREFERENCE|PROFILE|CORRECTION|...",
                      "targetKey": "stable semantic key if known",
                      "content": "memory content",
                      "confidence": 0.0,
                      "importance": 0.0,
                      "valueScore": 0.0,
                      "riskScore": 0.0,
                      "sourceMessageIds": ["message id"],
                      "signals": ["explicit_preference"],
                      "metadata": {
                        "targetLayer": "SHORT_TERM|LONG_TERM|SEMANTIC"
                      }
                    }
                  ],
                  "metadata": {}
                }
                Return one operation per durable memory delta. A single context block may produce multiple ADD operations.
                Use metadata.targetLayer only when routing is clear. Omit it to use the default SHORT_TERM layer.
                Never invent a new layer.
                Use REVIEW for sensitive, conflicting, or uncertain profile changes.
                Use IGNORE only as an advisory signal when no durable memory should be written; Seahorse may still fall back to the baseline classifier.
                """.formatted(
                request.operationId(),
                request.tenantId(),
                request.source(),
                request.userId(),
                request.conversationId(),
                request.messageId(),
                request.baselineAction().name(),
                request.baselineMemoryType(),
                request.baselineReason(),
                writeJson(request.baselineDetails()),
                existingMemories(request),
                stickyAnchors(request),
                zoneOrEmpty(request.referenceZone()),
                zoneOrEmpty(request.targetZone()),
                feedbackSamples(request),
                truncate(request.sanitizedContent(), MAX_CONTENT_CHARS));
    }

    private String existingMemories(MemoryRefinementRequest request) {
        if (request.existingMemories().isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> snapshots = request.existingMemories().stream()
                .map(this::existingMemorySnapshot)
                .toList();
        return writeJsonValue(snapshots);
    }

    private String stickyAnchors(MemoryRefinementRequest request) {
        if (request.stickyAnchors().isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> snapshots = request.stickyAnchors().stream()
                .map(this::existingMemorySnapshot)
                .toList();
        return writeJsonValue(snapshots);
    }

    private String zoneOrEmpty(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isBlank() ? "[]" : truncate(normalized, MAX_CONTENT_CHARS);
    }

    private Map<String, Object> existingMemorySnapshot(MemoryRefinementMemory memory) {
        return Map.of(
                "memoryId", memory.memoryId(),
                "layer", memory.layer(),
                "type", memory.type(),
                "content", truncate(memory.content(), MAX_FEEDBACK_CONTENT_CHARS),
                "targetKind", memory.targetKind(),
                "targetKey", memory.targetKey(),
                "generationId", memory.generationId(),
                "status", memory.status());
    }

    private String feedbackSamples(MemoryRefinementRequest request) {
        if (feedbackSampleLimit <= 0) {
            return "[]";
        }
        try {
            List<MemoryReviewFeedbackSample> samples = feedbackRepositoryPort.listSamples(
                    new MemoryReviewFeedbackQuery(
                            request.tenantId(),
                            request.userId(),
                            null,
                            "",
                            "",
                            feedbackSampleLimit));
            if (samples.isEmpty()) {
                return "[]";
            }
            List<Map<String, Object>> examples = samples.stream()
                    .map(this::feedbackExample)
                    .toList();
            return writeJsonValue(examples);
        } catch (Exception ex) {
            LOG.debug("Failed to load memory review feedback samples", ex);
            return "[]";
        }
    }

    private Map<String, Object> feedbackExample(MemoryReviewFeedbackSample sample) {
        return Map.of(
                "reviewStatus", sample.reviewStatus().name(),
                "targetKind", sample.targetKind(),
                "targetKey", sample.targetKey(),
                "rejectedContent", truncate(sample.rejectedContent(), MAX_FEEDBACK_CONTENT_CHARS),
                "chosenContent", truncate(sample.chosenContent(), MAX_FEEDBACK_CONTENT_CHARS),
                "reviewComment", truncate(sample.reviewComment(), MAX_FEEDBACK_CONTENT_CHARS),
                "sourceMessageIds", sample.sourceMessageIds());
    }

    private MemoryRefinementResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return MemoryRefinementResult.empty("llm_refiner_empty_response");
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            List<RefinedMemoryOperation> operations = parseOperations(root.path("operations"));
            if (operations.isEmpty()) {
                return MemoryRefinementResult.empty("llm_refiner_no_supported_operations");
            }
            return new MemoryRefinementResult(
                    root.path("refined").asBoolean(true),
                    root.path("reason").asText("llm_refined"),
                    operations,
                    parseObject(root.path("metadata")));
        } catch (Exception ex) {
            LOG.debug("Failed to parse LLM memory refiner response: {}", response, ex);
            return MemoryRefinementResult.empty("llm_refiner_parse_failed");
        }
    }

    private List<RefinedMemoryOperation> parseOperations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<RefinedMemoryOperation> operations = new ArrayList<>();
        for (JsonNode item : node) {
            MemoryIngestionAction action = parseAction(item.path("action").asText(""));
            if (action == null) {
                continue;
            }
            operations.add(new RefinedMemoryOperation(
                    action,
                    item.path("targetKind").asText(""),
                    item.path("targetKey").asText(""),
                    item.path("content").asText(""),
                    item.path("confidence").asDouble(0D),
                    item.path("importance").asDouble(0D),
                    item.path("valueScore").asDouble(0D),
                    item.path("riskScore").asDouble(0D),
                    parseStringList(item.path("sourceMessageIds")),
                    parseStringList(item.path("signals")),
                    parseObject(item.path("metadata"))));
        }
        return List.copyOf(operations);
    }

    private MemoryIngestionAction parseAction(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return MemoryIngestionAction.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            LOG.debug("Unsupported LLM memory action: {}", value);
            return null;
        }
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
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

    private String writeJson(Map<String, Object> values) {
        return writeJsonValue(Objects.requireNonNullElse(values, Map.of()));
    }

    private String writeJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(value, Map.of()));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String truncate(String value, int maxLen) {
        String normalized = Objects.requireNonNullElse(value, "");
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }
}
