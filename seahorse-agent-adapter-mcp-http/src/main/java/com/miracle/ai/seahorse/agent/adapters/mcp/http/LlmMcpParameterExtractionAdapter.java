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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Seahorse 模型端口的 MCP 参数抽取 adapter。
 *
 * <p>该实现复用内核模型路由端口，不再依赖旧 {@code MCPParameterExtractor} 或旧 {@code LLMService}。
 * 模型不可用或输出不可解析时返回工具默认参数，保证 MCP 失败不会打断 RAG 主链路。
 */
public class LlmMcpParameterExtractionAdapter implements McpParameterExtractionPort {

    private static final String DEFAULT_PROMPT = """
            你是 MCP 工具参数抽取器。请只输出 JSON 对象，不要输出解释、Markdown 或代码块。
            根据工具说明和用户问题，抽取工具参数。无法确定的非必填参数不要输出，有默认值的参数可省略。
            """;

    private static final String MODEL_ID = "";

    private final ObjectProvider<ChatModelPort> chatModelProvider;
    private final ObjectMapper objectMapper;

    public LlmMcpParameterExtractionAdapter(ObjectProvider<ChatModelPort> chatModelProvider,
                                            ObjectMapper objectMapper) {
        this.chatModelProvider = Objects.requireNonNull(chatModelProvider, "ChatModelPort Provider 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public Map<String, Object> extract(String toolId, String question) {
        return Map.of();
    }

    @Override
    public Map<String, Object> extract(McpParameterExtractionRequest request) {
        Objects.requireNonNull(request, "MCP 参数抽取请求不能为空");
        if (request.tool().parameters().isEmpty()) {
            return Map.of();
        }
        ChatModelPort chatModelPort = chatModelProvider.getIfAvailable();
        if (chatModelPort == null) {
            return defaultParameters(request.tool());
        }
        return extractWithModel(chatModelPort, request);
    }

    private Map<String, Object> extractWithModel(ChatModelPort chatModelPort, McpParameterExtractionRequest request) {
        String raw = chatModelPort.chat(buildChatRequest(request), MODEL_ID);
        Map<String, Object> parsed = parseModelResponse(raw, request.tool());
        Map<String, Object> result = new LinkedHashMap<>(parsed);
        fillDefaults(result, request.tool());
        return Map.copyOf(result);
    }

    private ChatRequest buildChatRequest(McpParameterExtractionRequest request) {
        return ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(resolvePrompt(request.customPromptTemplate())),
                        ChatMessage.user(buildToolDefinition(request.tool())),
                        ChatMessage.user("用户问题：\n" + request.userQuestion())))
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(0.1D)
                        .topP(0.3D)
                        .thinking(false)
                        .build())
                .build();
    }

    private String resolvePrompt(String customPromptTemplate) {
        if (customPromptTemplate == null || customPromptTemplate.isBlank()) {
            return DEFAULT_PROMPT;
        }
        return customPromptTemplate;
    }

    private String buildToolDefinition(McpToolDescriptor tool) {
        StringBuilder builder = new StringBuilder();
        builder.append("工具ID: ").append(tool.toolId()).append('\n');
        builder.append("工具说明: ").append(tool.description()).append('\n');
        builder.append("参数定义:\n");
        tool.parameters().forEach((name, parameter) -> appendParameter(builder, name, parameter));
        return builder.toString();
    }

    private void appendParameter(StringBuilder builder, String name, McpToolDescriptor.Parameter parameter) {
        builder.append("- ").append(name)
                .append(", type=").append(parameter.type())
                .append(", required=").append(parameter.required())
                .append(", description=").append(parameter.description());
        if (parameter.defaultValue() != null) {
            builder.append(", default=").append(parameter.defaultValue());
        }
        if (!parameter.enumValues().isEmpty()) {
            builder.append(", enum=").append(parameter.enumValues());
        }
        builder.append('\n');
    }

    private Map<String, Object> parseModelResponse(String raw, McpToolDescriptor tool) {
        if (raw == null || raw.isBlank()) {
            return defaultParameters(tool);
        }
        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFence(raw));
            if (!root.isObject()) {
                return defaultParameters(tool);
            }
            return readDeclaredParameters(root, tool);
        } catch (JsonProcessingException ex) {
            return defaultParameters(tool);
        }
    }

    private Map<String, Object> readDeclaredParameters(JsonNode root, McpToolDescriptor tool) {
        Map<String, Object> result = new LinkedHashMap<>();
        tool.parameters().keySet().forEach(name -> putIfPresent(result, name, root.get(name)));
        return result;
    }

    private void putIfPresent(Map<String, Object> result, String name, JsonNode value) {
        if (value != null && !value.isNull()) {
            result.put(name, objectMapper.convertValue(value, Object.class));
        }
    }

    private Map<String, Object> defaultParameters(McpToolDescriptor tool) {
        Map<String, Object> result = new LinkedHashMap<>();
        fillDefaults(result, tool);
        return Map.copyOf(result);
    }

    private void fillDefaults(Map<String, Object> result, McpToolDescriptor tool) {
        tool.parameters().forEach((name, parameter) -> {
            if (!result.containsKey(name) && parameter.defaultValue() != null) {
                result.put(name, parameter.defaultValue());
            }
        });
    }

    private String stripMarkdownFence(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFenceStart = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFenceStart <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, lastFenceStart).trim();
    }
}
