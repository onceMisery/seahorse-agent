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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ChartVisualizationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.FrontendDesignToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.NewsletterGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.PptGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 把工具观察结果渲染成"回灌给模型的文本"。
 *
 * <p>大产物类工具（图表 / PPT / Newsletter / 前端设计）的完整内容可能远超上下文预算，
 * 这里统一降级成结构化摘要：模型只需要知道"产物已生成、类型是什么、预览多少字"，
 * 真实内容留在产物存储里按需读取，不占用 ReAct 循环的上下文。
 */
final class AgentObservationText {

    private static final int PREVIEW_LIMIT = 800;
    private static final Set<String> LARGE_ARTIFACT_TOOL_IDS = Set.of(
            ChartVisualizationToolPortAdapter.TOOL_ID,
            NewsletterGenerationToolPortAdapter.TOOL_ID,
            PptGenerationToolPortAdapter.TOOL_ID,
            FrontendDesignToolPortAdapter.TOOL_ID);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgentObservationText() {
    }

    static String render(AgentObservation observation, String toolId) {
        if (!observation.success()) {
            return observation.error();
        }
        String content = observation.content();
        if (!LARGE_ARTIFACT_TOOL_IDS.contains(toolId) || content == null || content.isBlank()) {
            return content;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("status", "ARTIFACT_GENERATED");
            summary.put("toolId", toolId);
            summary.put("artifactType", root.path("artifactType").asText(""));
            summary.put("format", root.path("format").asText(""));
            String artifactContent = root.path("content").asText("");
            summary.put("contentPreview", trim(artifactContent));
            summary.put("contentChars", artifactContent.length());
            summary.put("truncatedForModelContext", artifactContent.length() > PREVIEW_LIMIT);
            return OBJECT_MAPPER.writeValueAsString(summary);
        } catch (Exception ignored) {
            return trim(content);
        }
    }

    static String toolIdFor(List<AgentToolCall> toolCalls, AgentObservation observation) {
        if (toolCalls == null || observation == null) {
            return "";
        }
        for (AgentToolCall toolCall : toolCalls) {
            if (toolCall != null && observation.toolCallId().equals(toolCall.id())) {
                return toolCall.toolId();
            }
        }
        return "";
    }

    private static String trim(String value) {
        String safe = Objects.requireNonNullElse(value, "");
        return safe.length() <= PREVIEW_LIMIT ? safe : safe.substring(0, PREVIEW_LIMIT);
    }
}
