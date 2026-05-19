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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;

import java.util.List;

/**
 * 流式对话模型端口。
 * <p>
 * 问答主链路通过该端口发起 SSE 增量生成，避免 Kernel 直接依赖具体 Provider SDK 或旧 LLMService 实现。
 */
public interface StreamingChatModelPort {

    /**
     * 执行流式对话。
     *
     * @param request  模型请求
     * @param callback 流式回调
     * @return 可取消句柄
     */
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);

    /**
     * 执行支持工具调用的流式对话。
     *
     * @param request           模型请求
     * @param callback          流式回调
     * @param toolCallCollector 工具调用收集器；适配器必须在 {@code onComplete()} 前调用一次
     * @return 可取消句柄
     */
    default StreamCancellationHandle streamChatWithTools(
            ChatRequest request,
            StreamCallback callback,
            ToolCallCollector toolCallCollector) {
        throw new UnsupportedOperationException("当前模型适配器尚未支持工具调用（function-calling）");
    }

    static StreamingChatModelPort noop() {
        return new StreamingChatModelPort() {
            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
                if (callback != null) {
                    callback.onComplete();
                }
                return () -> {
                };
            }

            @Override
            public StreamCancellationHandle streamChatWithTools(
                    ChatRequest request,
                    StreamCallback callback,
                    ToolCallCollector toolCallCollector) {
                if (toolCallCollector != null) {
                    toolCallCollector.onToolCalls(List.of());
                }
                if (callback != null) {
                    callback.onComplete();
                }
                return () -> {
                };
            }
        };
    }
}
