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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.util.Map;

public class PptGenerationToolPortAdapter extends AbstractChatContentGenerationToolPortAdapter {

    public static final String TOOL_ID = "ppt_generation";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "PPT Generation",
            "Generate a Markdown presentation deck outline with slide titles, speaker notes, and visual prompts.",
            """
                    {"type":"object","required":["topic"],"properties":{"topic":{"type":"string","minLength":1},"slideCount":{"type":"integer","minimum":1,"maximum":20},"audience":{"type":"string"},"sourceMaterial":{"type":"string"},"model":{"type":"string"}}}
                    """);

    public PptGenerationToolPortAdapter(ChatModelPort chatModelPort,
                                        String defaultModel,
                                        AgentToolJsonSupport jsonSupport) {
        super(DESCRIPTOR, chatModelPort, defaultModel, jsonSupport, "presentation", "markdown", "topic",
                """
                        You are a presentation deck generation tool. Produce a presentation deck in Markdown.
                        Each slide must have a title, key bullets, speaker notes, and an image prompt.
                        Stay grounded in the provided source material and avoid unsupported claims.
                        """);
    }

    @Override
    protected String userPrompt(Map<String, Object> arguments) {
        int slideCount = intValue(arguments, "slideCount", 6, 1, 20);
        return String.join("\n",
                labeled("topic", value(arguments, "topic")),
                "slideCount=" + slideCount,
                labeled("audience", value(arguments, "audience")),
                "sourceMaterial:\n" + value(arguments, "sourceMaterial"));
    }
}
