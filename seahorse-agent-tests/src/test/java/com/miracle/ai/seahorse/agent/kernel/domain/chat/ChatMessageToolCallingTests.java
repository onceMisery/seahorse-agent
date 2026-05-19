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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A7.5 契约测试：扩展 ChatRole / ChatMessage 支持 OpenAI 兼容 function-calling。
 */
class ChatMessageToolCallingTests {

    @Test
    void chatRoleEnumIncludesToolValue() {
        ChatRole tool = ChatRole.valueOf("TOOL");
        assertNotNull(tool);
        // 4 个角色：SYSTEM/USER/ASSISTANT/TOOL
        assertEquals(4, ChatRole.values().length);
    }

    @Test
    void assistantToolCallsFactoryProducesAssistantRoleAndCopiesToolCalls() {
        AgentToolCall call = new AgentToolCall("c1", "weather", Map.of("city", "SH"));
        List<AgentToolCall> source = new ArrayList<>();
        source.add(call);

        ChatMessage msg = ChatMessage.assistantToolCalls("我来查一下", source);

        assertEquals(ChatRole.ASSISTANT, msg.getRole());
        assertEquals("我来查一下", msg.getContent());
        assertNotNull(msg.getToolCalls());
        assertEquals(1, msg.getToolCalls().size());
        assertSame(call, msg.getToolCalls().get(0));

        // 防御性拷贝：修改入参 List 不应影响 message
        source.clear();
        assertEquals(1, msg.getToolCalls().size(), "构造时必须做防御性拷贝");

        // 不可变：直接修改返回 List 应抛 UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class,
                () -> msg.getToolCalls().add(call));
    }

    @Test
    void assistantToolCallsAcceptsBlankContent() {
        AgentToolCall call = new AgentToolCall("c1", "weather", Map.of());
        // OpenAI 协议允许 assistant.content 为空（仅 tool_calls）
        ChatMessage msg = ChatMessage.assistantToolCalls("", List.of(call));
        assertEquals(ChatRole.ASSISTANT, msg.getRole());
        assertEquals("", msg.getContent());
        assertEquals(1, msg.getToolCalls().size());
    }

    @Test
    void toolFactoryProducesToolRoleAndRequiresToolCallId() {
        ChatMessage msg = ChatMessage.tool("c1", "{\"temp\":21}");
        assertEquals(ChatRole.TOOL, msg.getRole());
        assertEquals("c1", msg.getToolCallId());
        assertEquals("{\"temp\":21}", msg.getContent());
        assertNull(msg.getToolCalls(), "TOOL role 不应携带 toolCalls 字段");

        assertThrows(IllegalArgumentException.class, () -> ChatMessage.tool(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> ChatMessage.tool(" ", "x"));
    }

    @Test
    void legacyFactoriesDoNotPopulateToolFields() {
        ChatMessage user = ChatMessage.user("hi");
        assertNull(user.getToolCallId());
        assertNull(user.getToolCalls());

        ChatMessage system = ChatMessage.system("rules");
        assertNull(system.getToolCallId());
        assertNull(system.getToolCalls());

        ChatMessage assistant = ChatMessage.assistant("answer");
        assertEquals(ChatRole.ASSISTANT, assistant.getRole());
        assertNull(assistant.getToolCallId());
        assertNull(assistant.getToolCalls());
    }

    @Test
    void assistantToolCallsRejectsNullToolCallsList() {
        assertThrows(NullPointerException.class,
                () -> ChatMessage.assistantToolCalls("", null));
    }

    @Test
    void toolFactoryAcceptsNullContent() {
        // tool 执行结果允许空（虽然不常见），不强制非空
        ChatMessage msg = ChatMessage.tool("c1", null);
        assertEquals(ChatRole.TOOL, msg.getRole());
        assertEquals("c1", msg.getToolCallId());
        assertNull(msg.getContent());
    }
}
