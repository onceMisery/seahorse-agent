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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;

import java.util.Objects;

/**
 * 问答响应阶段端口集合。
 */
public record ChatResponsePorts(RagPromptPort ragPromptPort,
                                PromptTemplatePort promptTemplatePort,
                                StreamingChatModelPort streamingChatModelPort,
                                StreamTaskPort streamTaskPort) {

    public ChatResponsePorts {
        Objects.requireNonNull(ragPromptPort, "RAG Prompt 端口不能为空");
        Objects.requireNonNull(promptTemplatePort, "Prompt 模板端口不能为空");
        Objects.requireNonNull(streamingChatModelPort, "流式模型端口不能为空");
        Objects.requireNonNull(streamTaskPort, "流式任务端口不能为空");
    }
}
