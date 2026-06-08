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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ImageGenerationToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "image_generation";
    private static final String DEFAULT_SIZE = "1024x1024";
    private static final String DEFAULT_RESPONSE_FORMAT = "url";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Image Generation",
            "Generate an image using the configured image model and return a reusable image reference.",
            """
                    {"type":"object","required":["prompt"],"properties":{"prompt":{"type":"string","minLength":1},"model":{"type":"string"},"size":{"type":"string","enum":["512x512","768x768","1024x1024","1024x1792","1792x1024"]},"style":{"type":"string"},"responseFormat":{"type":"string","enum":["url","b64_json"]}}}
                    """);

    private final ImageGenerationPort imageGenerationPort;
    private final String defaultModel;
    private final AgentToolJsonSupport jsonSupport;

    public ImageGenerationToolPortAdapter(ImageGenerationPort imageGenerationPort,
                                          String defaultModel,
                                          AgentToolJsonSupport jsonSupport) {
        this.imageGenerationPort = Objects.requireNonNullElseGet(imageGenerationPort,
                ImageGenerationPort::unsupported);
        this.defaultModel = Objects.requireNonNullElse(defaultModel, "").trim();
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String prompt = jsonSupport.string(arguments, "prompt");
            if (prompt.isBlank()) {
                return ToolInvocationResult.failed("prompt is required");
            }
            ImageGenerationResult result = imageGenerationPort.generate(new ImageGenerationRequest(
                    prompt,
                    model(arguments),
                    text(arguments, "size", DEFAULT_SIZE),
                    null,
                    text(arguments, "responseFormat", DEFAULT_RESPONSE_FORMAT)));
            return ToolInvocationResult.ok(jsonSupport.write(observation(result)));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("image_generation failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private String model(Map<String, Object> arguments) {
        String requested = jsonSupport.string(arguments, "model");
        return isDefaultModelPlaceholder(requested) ? defaultModel : requested;
    }

    private String text(Map<String, Object> arguments, String field, String fallback) {
        String value = jsonSupport.string(arguments, field);
        return value.isBlank() ? fallback : value;
    }

    private Map<String, Object> observation(ImageGenerationResult result) {
        ImageGenerationResult safeResult = Objects.requireNonNull(result,
                "Image generation result must not be null");
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("status", safeResult.status());
        observation.put("prompt", safeResult.prompt());
        observation.put("model", safeResult.model());
        observation.put("imageUrl", safeResult.imageUrl());
        observation.put("b64Json", safeResult.b64Json());
        observation.put("mimeType", safeResult.mimeType());
        return observation;
    }

    private static boolean isDefaultModelPlaceholder(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isBlank() || "default".equalsIgnoreCase(normalized);
    }
}
