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

public class ChartVisualizationToolPortAdapter extends AbstractChatContentGenerationToolPortAdapter {

    public static final String TOOL_ID = "chart_visualization";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Chart Visualization",
            "Generate a chart specification, preferably Mermaid or compact JSON, from provided facts or data.",
            """
                    {"type":"object","required":["title"],"properties":{"title":{"type":"string","minLength":1},"chartType":{"type":"string"},"data":{"type":"string"},"sourceMaterial":{"type":"string"},"model":{"type":"string"}}}
                    """);

    public ChartVisualizationToolPortAdapter(ChatModelPort chatModelPort,
                                             String defaultModel,
                                             AgentToolJsonSupport jsonSupport) {
        super(DESCRIPTOR, chatModelPort, defaultModel, jsonSupport, "chart", "mermaid/json", "title",
                """
                        You are a chart visualization tool. Create a Mermaid diagram or compact JSON chart spec.
                        Use the requested chart type when it fits. Keep labels short and preserve factual accuracy.
                        Return only the chart specification or a short Markdown block containing it.
                        """);
    }

    @Override
    protected String userPrompt(Map<String, Object> arguments) {
        return String.join("\n",
                labeled("title", value(arguments, "title")),
                labeled("chartType", value(arguments, "chartType")),
                "data:\n" + value(arguments, "data"),
                "sourceMaterial:\n" + value(arguments, "sourceMaterial"));
    }
}
