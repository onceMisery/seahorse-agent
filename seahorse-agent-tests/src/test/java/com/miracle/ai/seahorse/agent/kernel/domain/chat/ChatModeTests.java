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

import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Task A1 契约测试：ChatMode 枚举与 StreamChatCommand 兼容字段。
 */
class ChatModeTests {

    @Test
    void chatModeEnumExposesRagAndAgent() {
        ChatMode[] values = ChatMode.values();
        assertEquals(2, values.length, "ChatMode 仅支持 RAG / AGENT 两种");
        assertNotNull(ChatMode.valueOf("RAG"));
        assertNotNull(ChatMode.valueOf("AGENT"));
    }

    @Test
    void legacyFiveArgConstructorDefaultsToRagMode() {
        StreamChatCommand command = new StreamChatCommand("hello", "conv-1", "task-1", "user-1", false);
        assertEquals(ChatMode.RAG, command.chatMode(), "旧 5 参构造必须等价于 ChatMode.RAG");
    }

    @Test
    void newConstructorPersistsExplicitChatMode() {
        StreamChatCommand command = new StreamChatCommand(
                "hello", "conv-1", "task-1", "user-1", false, ChatMode.AGENT);
        assertEquals(ChatMode.AGENT, command.chatMode());
    }

    @Test
    void nullChatModeIsNormalizedToRag() {
        StreamChatCommand command = new StreamChatCommand(
                "hello", "conv-1", "task-1", "user-1", false, null);
        assertEquals(ChatMode.RAG, command.chatMode(), "null 入参必须归一化为 RAG");
    }
}
