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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class AbstractChatContentGenerationToolPortAdapter implements DescribedToolPort {

    private static final int DEFAULT_MAX_TOKENS = 2_048;
    private static final double DEFAULT_TEMPERATURE = 0.2d;

    private final ToolDescriptor descriptor;
    private final ChatModelPort chatModelPort;
    private final String defaultModel;
    private final AgentToolJsonSupport jsonSupport;
    private final String artifactType;
    private final String format;
    private final String requiredField;
    private final String systemPrompt;

    protected AbstractChatContentGenerationToolPortAdapter(ToolDescriptor descriptor,
                                                           ChatModelPort chatModelPort,
                                                           String defaultModel,
                                                           AgentToolJsonSupport jsonSupport,
                                                           String artifactType,
                                                           String format,
                                                           String requiredField,
                                                           String systemPrompt) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.chatModelPort = Objects.requireNonNullElseGet(chatModelPort, ChatModelPort::noop);
        this.defaultModel = Objects.requireNonNullElse(defaultModel, "").trim();
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
        this.artifactType = requireText(artifactType, "artifactType");
        this.format = requireText(format, "format");
        this.requiredField = requireText(requiredField, "requiredField");
        this.systemPrompt = requireText(systemPrompt, "systemPrompt");
    }

    @Override
    public ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String requiredValue = jsonSupport.string(arguments, requiredField);
            if (requiredValue.isBlank()) {
                return ToolInvocationResult.failed(requiredField + " is required");
            }
            String content = chatModelPort.chat(request(arguments), model(arguments));
            return ToolInvocationResult.ok(jsonSupport.write(observation(content)));
        } catch (Exception ex) {
            return ToolInvocationResult.failed(descriptor.toolId() + " failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private ChatRequest request(Map<String, Object> arguments) {
        return ChatRequest.builder()
                .messages(List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt(arguments))))
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(DEFAULT_TEMPERATURE)
                        .maxTokens(DEFAULT_MAX_TOKENS)
                        .thinking(false)
                        .build())
                .build();
    }

    protected abstract String userPrompt(Map<String, Object> arguments);

    protected String value(Map<String, Object> arguments, String field) {
        return jsonSupport.string(arguments, field);
    }

    protected int intValue(Map<String, Object> arguments, String field, int defaultValue, int min, int max) {
        return jsonSupport.boundedInt(arguments, field, defaultValue, min, max);
    }

    protected String labeled(String label, String value) {
        return label + "=" + Objects.requireNonNullElse(value, "").trim();
    }

    private String model(Map<String, Object> arguments) {
        String requested = jsonSupport.string(arguments, "model");
        return requested.isBlank() ? defaultModel : requested;
    }

    private Map<String, Object> observation(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactType", artifactType);
        payload.put("format", format);
        payload.put("content", Objects.requireNonNullElse(content, ""));
        return payload;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
