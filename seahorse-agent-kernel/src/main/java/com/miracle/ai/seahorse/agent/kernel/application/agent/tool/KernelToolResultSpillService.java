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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResultSpillPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Converts oversized, redacted tool output into a governed Artifact pointer.
 */
public class KernelToolResultSpillService implements ToolResultSpillPort {

    public static final String ARTIFACT_BUCKET = "agent-artifacts";
    public static final String PROVENANCE_KIND = "tool_result_spill";
    public static final String TEXT_CONTENT_TYPE = "text/plain; charset=utf-8";

    private static final String SHA_256 = "SHA-256";

    private static final Set<String> EXISTING_ARTIFACT_TOOL_IDS = Set.of(
            ImageGenerationToolPortAdapter.TOOL_ID,
            NewsletterGenerationToolPortAdapter.TOOL_ID,
            PptGenerationToolPortAdapter.TOOL_ID,
            ChartVisualizationToolPortAdapter.TOOL_ID,
            FrontendDesignToolPortAdapter.TOOL_ID);
    private static final String SPILL_FAILED = "Tool result exceeded the context limit and could not be persisted";

    private final AgentArtifactRepositoryPort artifactRepository;
    private final ObjectStoragePort objectStorage;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ToolResultSpillOptions options;

    public KernelToolResultSpillService(AgentArtifactRepositoryPort artifactRepository,
                                        ObjectStoragePort objectStorage,
                                        ObjectMapper objectMapper,
                                        Clock clock,
                                        ToolResultSpillOptions options) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage must not be null");
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.options = Objects.requireNonNullElseGet(options, ToolResultSpillOptions::defaults);
    }

    @Override
    public ToolInvocationResult spill(ToolInvocationRequest request, ToolInvocationResult result) {
        if (!options.enabled() || request == null || result == null || !result.success()
                || result.content() == null || result.content().length() <= options.thresholdChars()
                || ToolResultSpillPort.READ_TOOL_ID.equals(request.toolId())
                || EXISTING_ARTIFACT_TOOL_IDS.contains(request.toolId())) {
            return result;
        }
        if (request.runId() == null || request.runId().isBlank()) {
            return ToolInvocationResult.failed(SPILL_FAILED);
        }

        String artifactId = SnowflakeIds.nextIdString();
        byte[] bytes = result.content().getBytes(StandardCharsets.UTF_8);
        String contentSha256 = sha256(bytes);
        StoredObject stored = null;
        try {
            objectStorage.ensureBucket(ARTIFACT_BUCKET);
            stored = objectStorage.upload(
                    ARTIFACT_BUCKET,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    "tool-result-" + artifactId + ".txt",
                    TEXT_CONTENT_TYPE);
            AgentArtifact artifact = new AgentArtifact(
                    artifactId,
                    request.runId(),
                    null,
                    defaultText(request.tenantId(), "default"),
                    defaultText(request.userId(), "system"),
                    AgentArtifactType.FILE,
                    "Tool result: " + boundedToolId(request.toolId()),
                    TEXT_CONTENT_TYPE,
                    stored.url(),
                    preview(result.content()),
                    provenanceJson(request, result.content().length(), bytes.length, contentSha256),
                    AgentArtifactScanStatus.CLEAN,
                    Instant.now(clock));
            artifactRepository.save(artifact);
            return ToolInvocationResult.ok(pointerJson(
                    request,
                    artifactId,
                    result.content(),
                    result.content().length(),
                    bytes.length,
                    contentSha256));
        } catch (RuntimeException ex) {
            if (stored != null) {
                try {
                    objectStorage.deleteByUrl(stored.url());
                } catch (RuntimeException ignored) {
                    // The primary operation remains fail-closed; cleanup is best effort.
                }
            }
            return ToolInvocationResult.failed(SPILL_FAILED);
        }
    }

    private String preview(String content) {
        return content.length() > options.previewChars()
                ? content.substring(0, options.previewChars()) + "..."
                : content;
    }

    private String pointerJson(ToolInvocationRequest request,
                               String artifactId,
                               String content,
                               int contentChars,
                               int contentBytes,
                               String contentSha256) {
        Map<String, Object> pointer = new LinkedHashMap<>();
        pointer.put("kind", PROVENANCE_KIND);
        pointer.put("artifactId", artifactId);
        pointer.put("toolId", request.toolId());
        pointer.put("contentChars", contentChars);
        pointer.put("contentBytes", contentBytes);
        pointer.put("contentSha256", contentSha256);
        pointer.put("contentType", TEXT_CONTENT_TYPE);
        pointer.put("preview", preview(content));
        pointer.put("readToolId", ToolResultSpillPort.READ_TOOL_ID);
        pointer.put("readInstruction", "Use read_tool_result with artifactId, offset, and limit to read more.");
        return writeJson(pointer);
    }

    private String provenanceJson(ToolInvocationRequest request,
                                  int contentChars,
                                  int contentBytes,
                                  String contentSha256) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("kind", PROVENANCE_KIND);
        provenance.put("toolId", request.toolId());
        provenance.put("toolCallId", request.toolCallId());
        provenance.put("stepId", request.stepId());
        provenance.put("contentChars", contentChars);
        provenance.put("contentBytes", contentBytes);
        provenance.put("contentSha256", contentSha256);
        provenance.put("contentType", TEXT_CONTENT_TYPE);
        return writeJson(provenance);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(SHA_256).digest(content));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("tool result spill metadata serialization failed", ex);
        }
    }

    private String boundedToolId(String toolId) {
        String safe = Objects.requireNonNullElse(toolId, "unknown").trim();
        return safe.length() <= 200 ? safe : safe.substring(0, 200);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
