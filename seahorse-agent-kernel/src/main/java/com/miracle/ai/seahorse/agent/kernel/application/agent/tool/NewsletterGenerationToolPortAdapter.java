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

public class NewsletterGenerationToolPortAdapter extends AbstractChatContentGenerationToolPortAdapter {

    public static final String TOOL_ID = "newsletter_generation";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Newsletter Generation",
            "Generate a polished Markdown newsletter or long-form illustrated text draft from source material.",
            """
                    {"type":"object","required":["topic"],"properties":{"topic":{"type":"string","minLength":1},"audience":{"type":"string"},"tone":{"type":"string"},"sourceMaterial":{"type":"string"},"model":{"type":"string"}}}
                    """);

    public NewsletterGenerationToolPortAdapter(ChatModelPort chatModelPort,
                                               String defaultModel,
                                               AgentToolJsonSupport jsonSupport) {
        super(DESCRIPTOR, chatModelPort, defaultModel, jsonSupport, "newsletter", "markdown", "topic",
                """
                        You are a Newsletter generation tool. Generate concise, evidence-based Markdown.
                        Use only the provided source material. Prefer Chinese when the user content is Chinese.
                        Include headings, bullets, and clear editorial structure. Do not invent unsupported facts.
                        """);
    }

    @Override
    protected String userPrompt(Map<String, Object> arguments) {
        return String.join("\n",
                labeled("topic", value(arguments, "topic")),
                labeled("audience", value(arguments, "audience")),
                labeled("tone", value(arguments, "tone")),
                "sourceMaterial:\n" + value(arguments, "sourceMaterial"));
    }
}
