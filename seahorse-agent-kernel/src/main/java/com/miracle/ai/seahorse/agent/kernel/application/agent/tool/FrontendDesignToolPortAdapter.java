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

public class FrontendDesignToolPortAdapter extends AbstractChatContentGenerationToolPortAdapter {

    public static final String TOOL_ID = "frontend_design";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Frontend Design",
            "Generate a polished HTML/CSS or component layout draft for visual project introductions.",
            """
                    {"type":"object","required":["brief"],"properties":{"brief":{"type":"string","minLength":1},"style":{"type":"string"},"sourceMaterial":{"type":"string"},"model":{"type":"string"}}}
                    """);

    public FrontendDesignToolPortAdapter(ChatModelPort chatModelPort,
                                         String defaultModel,
                                         AgentToolJsonSupport jsonSupport) {
        super(DESCRIPTOR, chatModelPort, defaultModel, jsonSupport, "frontend_design", "html", "brief",
                """
                        You are a frontend design tool. Generate compact, production-minded HTML/CSS or component markup.
                        The design should support a visual project introduction with clear hierarchy and responsive layout.
                        Use only source-supported project facts. Do not include scripts or external tracking.
                        """);
    }

    @Override
    protected String userPrompt(Map<String, Object> arguments) {
        return String.join("\n",
                labeled("brief", value(arguments, "brief")),
                labeled("style", value(arguments, "style")),
                "sourceMaterial:\n" + value(arguments, "sourceMaterial"));
    }
}
