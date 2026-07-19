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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationRequestAwarePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResultSpillPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reads a bounded character range from an oversized tool result Artifact.
 */
public class ToolResultReadToolPortAdapter implements DescribedToolPort, ToolInvocationRequestAwarePort {

    public static final String TOOL_ID = ToolResultSpillPort.READ_TOOL_ID;
    private static final String ACCESS_DENIED = "Tool result artifact was not found or is not accessible";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Read Tool Result",
            "Read a bounded character range from a previously spilled tool result.",
            "{\"type\":\"object\",\"properties\":{\"artifactId\":{\"type\":\"string\"},"
                    + "\"offset\":{\"type\":\"integer\",\"minimum\":0},"
                    + "\"limit\":{\"type\":\"integer\",\"minimum\":1}},"
                    + "\"required\":[\"artifactId\",\"offset\",\"limit\"]}");

    private final AgentArtifactRepositoryPort artifactRepository;
    private final ObjectStoragePort objectStorage;
    private final ObjectMapper objectMapper;
    private final ToolResultSpillOptions options;

    public ToolResultReadToolPortAdapter(AgentArtifactRepositoryPort artifactRepository,
                                         ObjectStoragePort objectStorage,
                                         ObjectMapper objectMapper,
                                         ToolResultSpillOptions options) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage must not be null");
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.options = Objects.requireNonNullElseGet(options, ToolResultSpillOptions::defaults);
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        return ToolInvocationResult.failed("Full tool invocation context is required");
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        if (request == null || !TOOL_ID.equals(request.toolId())) {
            return ToolInvocationResult.failed("Tool id mismatch");
        }
        try {
            String artifactId = requiredString(request.arguments(), "artifactId");
            long offset = requiredLong(
                    request.arguments(), "offset", 0, Long.MAX_VALUE - options.maxReadChars());
            int limit = (int) requiredLong(request.arguments(), "limit", 1, options.maxReadChars());
            AgentArtifact artifact = artifactRepository.findById(artifactId)
                    .filter(candidate -> canRead(candidate, request))
                    .orElseThrow(() -> new IllegalArgumentException(ACCESS_DENIED));
            return ToolInvocationResult.ok(readRange(artifact, offset, limit));
        } catch (IllegalArgumentException ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        } catch (RuntimeException | IOException ex) {
            return ToolInvocationResult.failed("Tool result artifact could not be read");
        }
    }

    private boolean canRead(AgentArtifact artifact, ToolInvocationRequest request) {
        return Objects.equals(artifact.runId(), request.runId())
                && Objects.equals(artifact.tenantId(), defaultText(request.tenantId(), "default"))
                && Objects.equals(artifact.userId(), defaultText(request.userId(), "system"))
                && artifact.downloadable()
                && artifact.artifactType() == AgentArtifactType.FILE
                && KernelToolResultSpillService.PROVENANCE_KIND.equals(provenanceKind(artifact.provenanceJson()));
    }

    private String provenanceKind(String provenanceJson) {
        try {
            JsonNode root = objectMapper.readTree(provenanceJson);
            JsonNode kind = root == null ? null : root.get("kind");
            return kind == null ? null : kind.asText(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private String readRange(AgentArtifact artifact, long offset, int limit) throws IOException {
        try (InputStream stream = objectStorage.openStream(artifact.storageRef());
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            skipFully(reader, offset);
            char[] buffer = new char[limit + 1];
            int read = readUpTo(reader, buffer);
            int returnedChars = Math.min(read, limit);
            boolean hasMore = read > limit;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("artifactId", artifact.artifactId());
            payload.put("offset", offset);
            payload.put("returnedChars", returnedChars);
            payload.put("nextOffset", offset + returnedChars);
            payload.put("hasMore", hasMore);
            payload.put("content", new String(buffer, 0, returnedChars));
            return objectMapper.writeValueAsString(payload);
        }
    }

    private void skipFully(Reader reader, long offset) throws IOException {
        long remaining = offset;
        char[] discard = new char[4096];
        while (remaining > 0) {
            int read = reader.read(discard, 0, (int) Math.min(discard.length, remaining));
            if (read < 0) {
                return;
            }
            remaining -= read;
        }
    }

    private int readUpTo(Reader reader, char[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = reader.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text;
    }

    private long requiredLong(Map<String, Object> arguments, String key, long min, long max) {
        Object value = arguments == null ? null : arguments.get(key);
        long parsed;
        try {
            parsed = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(value == null ? "" : value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return parsed;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
