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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.web.WebFetchSafetyPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchHit;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebResearchToolPortAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(objectMapper);

    @Test
    void webSearchShouldCapMaxResultsAndReturnSourceCards() throws Exception {
        WebSearchToolPortAdapter tool = new WebSearchToolPortAdapter(request -> {
            assertEquals("AI infra", request.query());
            assertEquals(10, request.maxResults());
            return new WebSearchResult(request.query(), List.of(
                    new WebSearchHit("A", "https://example.com/a", "Snippet", WebSourceType.SEARCH_RESULT,
                            null, 0.9D)));
        }, jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", WebSearchToolPortAdapter.TOOL_ID,
                Map.of("query", "AI infra", "maxResults", 50));

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("AI infra", root.path("query").asText());
        assertEquals(10, root.path("maxResults").asInt());
        assertEquals("https://example.com/a", root.path("sources").get(0).path("url").asText());
    }

    @Test
    void webFetchShouldRejectUnsafeUrlsBeforeCallingFetcher() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WebFetchToolPortAdapter tool = new WebFetchToolPortAdapter(request -> {
            calls.incrementAndGet();
            return WebFetchResult.fetched(request.url(), "ignored", "ignored", "text/html", false);
        }, new WebFetchSafetyPolicy(), jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", WebFetchToolPortAdapter.TOOL_ID,
                Map.of("url", "http://localhost:8080/private"));

        assertTrue(result.success());
        assertEquals(0, calls.get());
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("REJECTED", root.path("status").asText());
        assertEquals("LOCALHOST_BLOCKED", root.path("reasonCode").asText());
    }

    @Test
    void webFetchShouldMarkFetchedContentAsUntrustedExternalContent() throws Exception {
        WebFetchToolPortAdapter tool = new WebFetchToolPortAdapter(request -> {
            assertEquals(8000, request.maxChars());
            return WebFetchResult.fetched(
                    request.url(),
                    "Example",
                    "External page body",
                    "text/html",
                    false);
        }, new WebFetchSafetyPolicy(), jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", WebFetchToolPortAdapter.TOOL_ID,
                Map.of("url", "https://example.com/research"));

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("FETCHED", root.path("status").asText());
        assertEquals(WebFetchToolPortAdapter.UNTRUSTED_CONTENT_LABEL, root.path("contentWarning").asText());
        assertEquals("External page body", root.path("contentText").asText());
        assertFalse(root.path("truncated").asBoolean());
    }
}
