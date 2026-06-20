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

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallParserTests {

    @Test
    void parsesTextEncodedToolCallsAndStripsThemFromContent() {
        ToolCallParser parser = new ToolCallParser();

        ToolCallParser.Result result = parser.parse("""
                Before.
                <tool_call>
                  <function=web_search>
                    <parameter=query>AgentScope &amp; Nacos</parameter>
                  </function>
                </tool_call>
                After.
                """, Set.of("web_search"));

        assertEquals("Before.\n\nAfter.", result.content());
        assertEquals(1, result.toolCalls().size());
        assertEquals("web_search", result.toolCalls().get(0).toolId());
        assertEquals("AgentScope & Nacos", result.toolCalls().get(0).arguments().get("query"));
    }

    @Test
    void ignoresUnknownToolsAndKeepsOriginalContent() {
        ToolCallParser parser = new ToolCallParser();
        String content = "<tool_call><function=unknown><parameter=q>x</parameter></function></tool_call>";

        ToolCallParser.Result result = parser.parse(content, Set.of("web_search"));

        assertEquals(content, result.content());
        assertTrue(result.toolCalls().isEmpty());
    }
}
