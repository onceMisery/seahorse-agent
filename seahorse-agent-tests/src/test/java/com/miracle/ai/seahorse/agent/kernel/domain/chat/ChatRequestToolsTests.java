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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A6 契约测试：ChatRequest 增加 tools / toolChoice 可选字段。
 */
class ChatRequestToolsTests {

    @Test
    void legacyBuilderPreservesEmptyToolsAndAutoChoice() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hi")))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.5D).build())
                .build();
        assertNotNull(req.getTools());
        assertTrue(req.getTools().isEmpty());
        assertEquals("auto", req.getToolChoice());
    }

    @Test
    void newBuilderSetsToolsAndToolChoice() {
        ToolDescriptor d = new ToolDescriptor("weather", "Weather", "查天气", "{}");
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hi")))
                .samplingOptions(ChatSamplingOptions.builder().build())
                .tools(List.of(d))
                .toolChoice("required")
                .build();
        assertEquals(1, req.getTools().size());
        assertEquals("weather", req.getTools().get(0).toolId());
        assertEquals("required", req.getToolChoice());
    }

    @Test
    void nullToolsIsNormalizedToEmptyList() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of())
                .samplingOptions(ChatSamplingOptions.builder().build())
                .tools(null)
                .toolChoice(null)
                .build();
        assertNotNull(req.getTools());
        assertTrue(req.getTools().isEmpty());
        assertEquals("auto", req.getToolChoice());
    }
}
