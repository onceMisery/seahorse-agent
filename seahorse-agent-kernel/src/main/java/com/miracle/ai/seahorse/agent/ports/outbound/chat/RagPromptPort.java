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

package com.miracle.ai.seahorse.agent.ports.outbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;

import java.util.List;

/**
 * RAG Prompt 组装端口。
 */
public interface RagPromptPort {

    /**
     * 构造发送给模型的消息列表。
     *
     * @param context      Prompt 上下文
     * @param history      会话历史
     * @param question     当前问题
     * @param subQuestions 子问题列表
     * @return 模型消息列表
     */
    List<ChatMessage> buildStructuredMessages(PromptContext context,
                                              List<ChatMessage> history,
                                              String question,
                                              List<String> subQuestions);

    static RagPromptPort simple() {
        return (context, history, question, subQuestions) -> List.of(
                ChatMessage.user(java.util.Objects.requireNonNullElse(question, "")));
    }
}
