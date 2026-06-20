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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses model responses that encode tool calls as XML-like text blocks.
 */
public class ToolCallParser {

    private static final Pattern TEXT_TOOL_CALL_BLOCK_PATTERN =
            Pattern.compile("(?is)<tool_call\\b[^>]*>(.*?)</tool_call>");
    private static final Pattern TEXT_TOOL_CALL_FUNCTION_PATTERN =
            Pattern.compile("(?is)<function\\s*=\\s*([A-Za-z0-9_-]+)\\s*>(.*?)</function>");
    private static final Pattern TEXT_TOOL_CALL_PARAMETER_PATTERN =
            Pattern.compile("(?is)<parameter\\s*=\\s*([A-Za-z0-9_.-]+)\\s*>(.*?)</parameter>");
    private static final AtomicLong TEXT_TOOL_CALL_SEQUENCE = new AtomicLong();

    public Result parse(String content, Set<String> exposedToolIds) {
        if (content == null || content.isBlank() || exposedToolIds == null || exposedToolIds.isEmpty()) {
            return new Result(Objects.requireNonNullElse(content, ""), List.of());
        }
        Matcher blockMatcher = TEXT_TOOL_CALL_BLOCK_PATTERN.matcher(content);
        List<AgentToolCall> toolCalls = new ArrayList<>();
        while (blockMatcher.find()) {
            AgentToolCall toolCall = parseToolCall(blockMatcher.group(1), exposedToolIds);
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }
        if (toolCalls.isEmpty()) {
            return new Result(content, List.of());
        }
        String strippedContent = TEXT_TOOL_CALL_BLOCK_PATTERN.matcher(content).replaceAll("").trim();
        return new Result(strippedContent, toolCalls);
    }

    private AgentToolCall parseToolCall(String block, Set<String> exposedToolIds) {
        if (block == null || block.isBlank()) {
            return null;
        }
        Matcher functionMatcher = TEXT_TOOL_CALL_FUNCTION_PATTERN.matcher(block);
        if (!functionMatcher.find()) {
            return null;
        }
        String toolId = functionMatcher.group(1).trim();
        if (!exposedToolIds.contains(toolId)) {
            return null;
        }
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        Matcher parameterMatcher = TEXT_TOOL_CALL_PARAMETER_PATTERN.matcher(functionMatcher.group(2));
        while (parameterMatcher.find()) {
            String key = parameterMatcher.group(1).trim();
            if (!key.isBlank()) {
                arguments.put(key, decodeValue(parameterMatcher.group(2).trim()));
            }
        }
        return AgentToolCall.of(nextToolCallId(), toolId, arguments);
    }

    private String nextToolCallId() {
        return "text-tool-call-" + TEXT_TOOL_CALL_SEQUENCE.incrementAndGet();
    }

    private String decodeValue(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    public record Result(String content, List<AgentToolCall> toolCalls) {

        public Result {
            content = Objects.requireNonNullElse(content, "");
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }
}
