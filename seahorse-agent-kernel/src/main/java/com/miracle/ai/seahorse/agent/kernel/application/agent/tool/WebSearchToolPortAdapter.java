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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchHit;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WebSearchToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "web_search";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int MAX_RESULTS = 10;
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Web Search",
            "Search public Web sources through a server-side controlled search provider.",
            """
                    {"type":"object","required":["query"],"properties":{"query":{"type":"string","minLength":1},"locale":{"type":"string"},"timeRange":{"type":"string"},"maxResults":{"type":"integer","minimum":1,"maximum":10}}}
                    """);

    private final WebSearchPort webSearchPort;
    private final AgentToolJsonSupport jsonSupport;

    public WebSearchToolPortAdapter(WebSearchPort webSearchPort, AgentToolJsonSupport jsonSupport) {
        this.webSearchPort = Objects.requireNonNullElseGet(webSearchPort, WebSearchPort::empty);
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String query = jsonSupport.string(arguments, "query");
            if (query.isBlank()) {
                return ToolInvocationResult.failed("query is required");
            }
            int maxResults = jsonSupport.boundedInt(arguments, "maxResults",
                    DEFAULT_MAX_RESULTS, 1, MAX_RESULTS);
            WebSearchResult result = webSearchPort.search(new WebSearchRequest(
                    query,
                    jsonSupport.string(arguments, "locale"),
                    jsonSupport.string(arguments, "timeRange"),
                    maxResults));
            return ToolInvocationResult.ok(jsonSupport.write(observation(query, maxResults, result)));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("web_search failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private Map<String, Object> observation(String query, int maxResults, WebSearchResult result) {
        List<WebSearchHit> hits = result == null ? List.of() : result.hits();
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("query", query);
        observation.put("maxResults", maxResults);
        observation.put("resultCount", hits.size());
        observation.put("sources", hits.stream().limit(maxResults).map(this::source).toList());
        return observation;
    }

    private Map<String, Object> source(WebSearchHit hit) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("title", hit.title());
        source.put("url", hit.url());
        source.put("snippet", hit.snippet());
        source.put("sourceType", hit.sourceType());
        source.put("publishedAt", hit.publishedAt());
        source.put("score", hit.score());
        return source;
    }
}
