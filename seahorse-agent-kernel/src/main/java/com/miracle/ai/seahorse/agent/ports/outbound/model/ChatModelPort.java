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

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;

import java.util.List;

/**
 * 对话模型端口。
 * <p>
 * 该端口隔离百炼、Ollama、SiliconFlow 等 Provider 的具体 SDK 和协议。
 */
public interface ChatModelPort {

    /**
     * 执行非流式对话。
     *
     * @param request 模型请求
     * @param modelId 模型 ID，可为空
     * @return 模型输出文本
     */
    String chat(ChatRequest request, String modelId);

    /**
     * 执行非流式对话。
     *
     * @param modelId  模型 ID
     * @param messages 对话消息
     * @return 模型输出文本
     */
    default String chat(String modelId, List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();
        return chat(request, modelId);
    }

    static ChatModelPort noop() {
        return (request, modelId) -> "";
    }
}
