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

import com.miracle.ai.seahorse.agent.kernel.application.agent.web.WebFetchSafetyDecision;
import com.miracle.ai.seahorse.agent.kernel.application.agent.web.WebFetchSafetyPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class WebFetchToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "web_fetch";
    public static final String UNTRUSTED_CONTENT_LABEL = "UNTRUSTED_EXTERNAL_CONTENT";
    private static final int DEFAULT_MAX_CHARS = 8_000;
    private static final int MAX_CHARS = 20_000;
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Web Fetch",
            "Fetch a public HTTP/HTTPS page through a server-side controlled fetcher with SSRF protection.",
            """
                    {"type":"object","required":["url"],"properties":{"url":{"type":"string","minLength":1},"maxChars":{"type":"integer","minimum":100,"maximum":20000}}}
                    """);

    private final WebFetchPort webFetchPort;
    private final WebFetchSafetyPolicy safetyPolicy;
    private final AgentToolJsonSupport jsonSupport;

    public WebFetchToolPortAdapter(WebFetchPort webFetchPort,
                                   WebFetchSafetyPolicy safetyPolicy,
                                   AgentToolJsonSupport jsonSupport) {
        this.webFetchPort = Objects.requireNonNullElseGet(webFetchPort, WebFetchPort::unsupported);
        this.safetyPolicy = Objects.requireNonNullElseGet(safetyPolicy, WebFetchSafetyPolicy::new);
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String url = jsonSupport.string(arguments, "url");
            if (url.isBlank()) {
                return ToolInvocationResult.failed("url is required");
            }
            WebFetchSafetyDecision decision = safetyPolicy.decide(url);
            if (!decision.allowed()) {
                return ToolInvocationResult.ok(jsonSupport.write(observation(
                        WebFetchResult.rejected(url, decision.reason().name()))));
            }
            int maxChars = jsonSupport.boundedInt(arguments, "maxChars", DEFAULT_MAX_CHARS, 100, MAX_CHARS);
            return ToolInvocationResult.ok(jsonSupport.write(observation(
                    webFetchPort.fetch(new WebFetchRequest(url, maxChars)))));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("web_fetch failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private Map<String, Object> observation(WebFetchResult result) {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("url", result.url());
        observation.put("status", result.status());
        observation.put("title", result.title());
        observation.put("mimeType", result.mimeType());
        observation.put("reasonCode", result.reasonCode());
        observation.put("truncated", result.truncated());
        observation.put("contentWarning", UNTRUSTED_CONTENT_LABEL);
        if (result.status() == WebFetchStatus.FETCHED) {
            observation.put("contentText", result.contentText());
        }
        return observation;
    }
}
