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

package com.miracle.ai.seahorse.agent.kernel.domain.chat;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Seahorse 内核模型请求契约。
 */
@Data
@NoArgsConstructor
public class ChatRequest {

    private static final String DEFAULT_TOOL_CHOICE = "auto";

    private List<ChatMessage> messages = new ArrayList<>();

    private ChatSamplingOptions samplingOptions;

    private Boolean enableTools;

    /** OpenAI 兼容 function-calling 工具列表；空列表表示禁用工具协议。 */
    private List<ToolDescriptor> tools = new ArrayList<>();

    /** OpenAI 兼容 tool_choice：auto / required / none / 工具名。 */
    private String toolChoice = DEFAULT_TOOL_CHOICE;

    @Builder
    public ChatRequest(List<ChatMessage> messages,
                       ChatSamplingOptions samplingOptions,
                       Boolean enableTools,
                       List<ToolDescriptor> tools,
                       String toolChoice) {
        this.messages = messages == null ? new ArrayList<>() : messages;
        this.samplingOptions = samplingOptions;
        this.enableTools = enableTools;
        this.tools = tools == null ? new ArrayList<>() : tools;
        this.toolChoice = toolChoice == null || toolChoice.isBlank() ? DEFAULT_TOOL_CHOICE : toolChoice;
    }

    public Double getTemperature() {
        return samplingOptions == null ? null : samplingOptions.getTemperature();
    }

    public Double getTopP() {
        return samplingOptions == null ? null : samplingOptions.getTopP();
    }

    public Integer getTopK() {
        return samplingOptions == null ? null : samplingOptions.getTopK();
    }

    public Integer getMaxTokens() {
        return samplingOptions == null ? null : samplingOptions.getMaxTokens();
    }

    public Boolean getThinking() {
        return samplingOptions == null ? null : samplingOptions.getThinking();
    }
}
