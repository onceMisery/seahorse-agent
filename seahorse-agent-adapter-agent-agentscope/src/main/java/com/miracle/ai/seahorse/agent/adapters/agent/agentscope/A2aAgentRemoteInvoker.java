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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentCard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class A2aAgentRemoteInvoker implements AgentScopeRemoteAgentInvoker {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String JSONRPC_TRANSPORT = "JSONRPC";

    private final Duration timeout;
    private final HttpClient httpClient;
    private final String authHeaderName;
    private final String sharedSecret;
    private final A2aAuthMode authMode;
    private final Clock clock;
    private final Supplier<String> nonceSupplier;
    private final AgentScopeObservationSupport observationSupport;

    public A2aAgentRemoteInvoker(Duration timeout) {
        this(timeout, "", "");
    }

    public A2aAgentRemoteInvoker(Duration timeout, String authHeaderName, String sharedSecret) {
        this(timeout, A2aAuthMode.SHARED_SECRET, authHeaderName, sharedSecret);
    }

    public A2aAgentRemoteInvoker(
            Duration timeout,
            A2aAuthMode authMode,
            String authHeaderName,
            String sharedSecret) {
        this(timeout, authMode, authHeaderName, sharedSecret, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    public A2aAgentRemoteInvoker(
            Duration timeout,
            A2aAuthMode authMode,
            String authHeaderName,
            String sharedSecret,
            AgentScopeObservationSupport observationSupport) {
        this(timeout, authMode, authHeaderName, sharedSecret, Clock.systemUTC(), () -> UUID.randomUUID().toString(),
                observationSupport);
    }

    A2aAgentRemoteInvoker(
            Duration timeout,
            A2aAuthMode authMode,
            String authHeaderName,
            String sharedSecret,
            Clock clock,
            Supplier<String> nonceSupplier) {
        this(timeout, authMode, authHeaderName, sharedSecret, clock, nonceSupplier, AgentScopeObservationSupport.noop());
    }

    A2aAgentRemoteInvoker(
            Duration timeout,
            A2aAuthMode authMode,
            String authHeaderName,
            String sharedSecret,
            Clock clock,
            Supplier<String> nonceSupplier,
            AgentScopeObservationSupport observationSupport) {
        this.timeout = Objects.requireNonNullElse(timeout, Duration.ofMinutes(2));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
        this.authMode = Objects.requireNonNullElse(authMode, A2aAuthMode.SHARED_SECRET);
        this.authHeaderName = trimToNull(authHeaderName);
        this.sharedSecret = trimToNull(sharedSecret);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "nonceSupplier must not be null");
        this.observationSupport = Objects.requireNonNullElseGet(observationSupport, AgentScopeObservationSupport::noop);
    }

    @Override
    public String invoke(AgentCard agentCard, A2AAgentRequest request) {
        URI endpoint = URI.create(endpointUrl(agentCard));
        Map<String, Object> payload = messageSendPayload(request);
        try (ObservationScope ignored = observationSupport.start("a2a.invoke", request.tenantId(),
                observationSupport.attributes(
                        "remoteAgent", request.agentName(),
                        "a2a.authMode", authMode.name(),
                        "endpoint", endpoint.toString()))) {
            String body = OBJECT_MAPPER.writeValueAsString(payload);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            applyAuthentication(requestBuilder, request, body);
            HttpRequest httpRequest = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("A2A agent invocation failed with HTTP " + response.statusCode());
            }
            return textContent(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("A2A agent invocation failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A2A agent invocation interrupted", ex);
        }
    }

    private void applyAuthentication(HttpRequest.Builder requestBuilder, A2AAgentRequest request, String body) {
        if (authMode == A2aAuthMode.NONE || sharedSecret == null) {
            return;
        }
        if (authMode == A2aAuthMode.TENANT_SIGNED) {
            new A2aRequestSigner(sharedSecret, clock, nonceSupplier)
                    .sign(request.tenantId(), request.agentName(), body)
                    .forEach(requestBuilder::header);
            return;
        }
        if (authHeaderName != null) {
            requestBuilder.header(authHeaderName, sharedSecret);
        }
    }

    private String endpointUrl(AgentCard agentCard) {
        Objects.requireNonNull(agentCard, "agentCard must not be null");
        if (agentCard.additionalInterfaces() != null) {
            for (AgentInterface agentInterface : agentCard.additionalInterfaces()) {
                if (agentInterface != null
                        && JSONRPC_TRANSPORT.equalsIgnoreCase(agentInterface.transport())
                        && hasText(agentInterface.url())) {
                    return agentInterface.url().trim();
                }
            }
        }
        if (hasText(agentCard.url())) {
            return agentCard.url().trim();
        }
        throw new IllegalStateException("A2A agent card missing endpoint URL: " + agentCard.name());
    }

    private Map<String, Object> messageSendPayload(A2AAgentRequest request) {
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("kind", "text");
        textPart.put("text", request.prompt());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("parts", List.of(textPart));
        message.put("messageId", UUID.randomUUID().toString());
        message.put("metadata", Maps.stringMetadata(request.metadata()));
        message.put("kind", "message");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message", message);
        params.put("metadata", Maps.stringMetadata(request.metadata()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", UUID.randomUUID().toString());
        payload.put("method", "message/send");
        payload.put("params", params);
        return payload;
    }

    private String textContent(String body) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new IllegalStateException("A2A agent invocation failed: " + error.toString());
        }
        JsonNode result = root.path("result");
        List<String> texts = new ArrayList<>();
        collectPartText(result.path("parts"), texts);
        collectPartText(result.path("status").path("message").path("parts"), texts);
        JsonNode artifacts = result.path("artifacts");
        if (artifacts.isArray()) {
            for (JsonNode artifact : artifacts) {
                collectPartText(artifact.path("parts"), texts);
            }
        }
        return String.join("\n", texts);
    }

    private void collectPartText(JsonNode parts, List<String> texts) {
        if (!parts.isArray()) {
            return;
        }
        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (hasText(text)) {
                texts.add(text);
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
