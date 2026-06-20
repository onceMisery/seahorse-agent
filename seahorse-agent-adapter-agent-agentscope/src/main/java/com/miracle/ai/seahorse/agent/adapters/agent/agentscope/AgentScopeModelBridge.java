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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AgentScopeModelBridge implements Model {

    private final StreamingChatModelPort modelPort;
    private final String modelName;
    private final ChatSamplingOptions samplingOptions;
    private final ObjectMapper objectMapper;

    public AgentScopeModelBridge(StreamingChatModelPort modelPort, String modelName) {
        this(modelPort, modelName, null, new ObjectMapper());
    }

    public AgentScopeModelBridge(StreamingChatModelPort modelPort, String modelName, ObjectMapper objectMapper) {
        this(modelPort, modelName, null, objectMapper);
    }

    private AgentScopeModelBridge(
            StreamingChatModelPort modelPort,
            String modelName,
            ChatSamplingOptions samplingOptions,
            ObjectMapper objectMapper) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        this.modelName = textOrDefault(modelName, "seahorse-model");
        this.samplingOptions = samplingOptions;
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    public AgentScopeModelBridge forRequest(AgentLoopRequest request) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return new AgentScopeModelBridge(modelPort, textOrDefault(safeRequest.modelId(), modelName),
                safeRequest.samplingOptions(), objectMapper);
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        List<ToolDescriptor> seahorseTools = toToolDescriptors(tools);
        ChatRequest request = ChatRequest.builder()
                .messages(toChatMessages(messages))
                .modelId(modelId(options))
                .samplingOptions(samplingOptions(options))
                .tools(seahorseTools)
                .toolChoice(seahorseTools.isEmpty() ? "none" : "auto")
                .build();
        return Flux.create(sink -> {
            StreamCallback callback = new StreamCallback() {
                @Override
                public void onContent(String content) {
                    if (!sink.isCancelled()) {
                        sink.next(response(List.of(TextBlock.builder()
                                .text(Objects.requireNonNullElse(content, ""))
                                .build())));
                    }
                }

                @Override
                public void onComplete() {
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            };
            StreamCancellationHandle handle = seahorseTools.isEmpty()
                    ? modelPort.streamChat(request, callback)
                    : modelPort.streamChatWithTools(request, callback, toolCalls -> {
                        List<ContentBlock> blocks = toToolUseBlocks(toolCalls);
                        if (!blocks.isEmpty() && !sink.isCancelled()) {
                            sink.next(response(blocks));
                        }
                    });
            sink.onCancel(handle::cancel);
        });
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    private List<ChatMessage> toChatMessages(List<Msg> messages) {
        List<ChatMessage> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (Msg message : messages) {
            if (message != null) {
                result.add(new ChatMessage(toChatRole(message.getRole()), message.getTextContent()));
            }
        }
        return result;
    }

    private List<ToolDescriptor> toToolDescriptors(List<ToolSchema> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .filter(Objects::nonNull)
                .map(tool -> new ToolDescriptor(
                        tool.getName(),
                        tool.getName(),
                        tool.getDescription(),
                        toJson(tool.getParameters())))
                .toList();
    }

    private ChatResponse response(List<ContentBlock> content) {
        return ChatResponse.builder()
                .content(content == null ? List.of() : content)
                .build();
    }

    private List<ContentBlock> toToolUseBlocks(List<AgentToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .filter(Objects::nonNull)
                .map(toolCall -> (ContentBlock) ToolUseBlock.builder()
                        .id(toolCall.id())
                        .name(toolCall.toolId())
                        .input(toolCall.arguments())
                        .build())
                .toList();
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private ChatRole toChatRole(MsgRole role) {
        if (role == null) {
            return ChatRole.USER;
        }
        return switch (role) {
            case SYSTEM -> ChatRole.SYSTEM;
            case ASSISTANT -> ChatRole.ASSISTANT;
            case TOOL -> ChatRole.TOOL;
            case USER -> ChatRole.USER;
        };
    }

    private String modelId(GenerateOptions options) {
        return options == null ? modelName : textOrDefault(options.getModelName(), modelName);
    }

    private ChatSamplingOptions samplingOptions(GenerateOptions options) {
        if (options == null) {
            return samplingOptions;
        }
        return ChatSamplingOptions.builder()
                .temperature(first(options.getTemperature(), samplingOptions == null ? null : samplingOptions.getTemperature()))
                .topP(first(options.getTopP(), samplingOptions == null ? null : samplingOptions.getTopP()))
                .topK(first(options.getTopK(), samplingOptions == null ? null : samplingOptions.getTopK()))
                .maxTokens(first(options.getMaxTokens(), samplingOptions == null ? null : samplingOptions.getMaxTokens()))
                .thinking(samplingOptions == null ? null : samplingOptions.getThinking())
                .build();
    }

    private static <T> T first(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
