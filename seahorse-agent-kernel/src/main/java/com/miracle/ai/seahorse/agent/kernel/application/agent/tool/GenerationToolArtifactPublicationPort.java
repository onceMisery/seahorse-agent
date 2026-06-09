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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolArtifactPublicationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class GenerationToolArtifactPublicationPort implements ToolArtifactPublicationPort {

    static final String ARTIFACT_BUCKET = "agent-artifacts";

    private static final int PREVIEW_LIMIT = 200;
    private static final Set<String> TEXT_GENERATION_TOOL_IDS = Set.of(
            NewsletterGenerationToolPortAdapter.TOOL_ID,
            PptGenerationToolPortAdapter.TOOL_ID,
            ChartVisualizationToolPortAdapter.TOOL_ID,
            FrontendDesignToolPortAdapter.TOOL_ID);

    private final AgentArtifactRepositoryPort artifactRepository;
    private final ObjectStoragePort objectStorage;
    private final AgentRunEventBufferPort eventBuffer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GenerationToolArtifactPublicationPort(AgentArtifactRepositoryPort artifactRepository,
                                                 ObjectStoragePort objectStorage,
                                                 AgentRunEventBufferPort eventBuffer,
                                                 ObjectMapper objectMapper,
                                                 Clock clock) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
        this.objectStorage = objectStorage;
        this.eventBuffer = Objects.requireNonNullElseGet(eventBuffer, AgentRunEventBufferPort::noop);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public void publish(ToolInvocationRequest request, ToolInvocationResult result) {
        if (request == null || result == null || !result.success() || !hasText(request.runId())) {
            return;
        }
        if (ImageGenerationToolPortAdapter.TOOL_ID.equals(request.toolId())) {
            publishImageArtifact(request, result);
            return;
        }
        if (!TEXT_GENERATION_TOOL_IDS.contains(request.toolId())) {
            return;
        }
        Observation observation = parseTextObservation(result.content()).orElse(null);
        if (observation == null || !hasText(observation.content())) {
            return;
        }

        byte[] bytes = observation.content().getBytes(StandardCharsets.UTF_8);
        ArtifactMapping mapping = ArtifactMapping.from(observation);
        saveArtifact(request, bytes, mapping, preview(observation.content()), provenanceJson(request, observation));
    }

    private void publishImageArtifact(ToolInvocationRequest request, ToolInvocationResult result) {
        ImageObservation observation = parseImageObservation(result.content()).orElse(null);
        if (observation == null) {
            return;
        }
        if (!hasText(observation.b64Json()) && !hasText(observation.imageUrl())) {
            return;
        }
        String mimeType = imageMimeType(observation.mimeType());
        ArtifactMapping mapping = new ArtifactMapping(
                AgentArtifactType.IMAGE,
                "Generated image",
                mimeType,
                imageExtension(mimeType),
                "generated-image");
        if (hasText(observation.b64Json())) {
            if (objectStorage == null) {
                return;
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(observation.b64Json());
            } catch (IllegalArgumentException ex) {
                return;
            }
            if (bytes.length == 0) {
                return;
            }
            saveArtifact(request, bytes, mapping, preview(observation.prompt()), provenanceJson(request, observation));
            return;
        }
        saveArtifactReference(request, observation.imageUrl(), mapping, preview(observation.prompt()),
                provenanceJson(request, observation));
    }

    private void saveArtifact(ToolInvocationRequest request,
                              byte[] bytes,
                              ArtifactMapping mapping,
                              String previewText,
                              String provenanceJson) {
        String artifactId = SnowflakeIds.nextIdString();
        String filename = mapping.filenamePrefix() + "-" + artifactId + mapping.extension();
        StoredObject stored = objectStorage.upload(
                ARTIFACT_BUCKET,
                new ByteArrayInputStream(bytes),
                bytes.length,
                filename,
                mapping.mimeType());

        AgentArtifact artifact = new AgentArtifact(
                artifactId,
                request.runId(),
                null,
                defaultText(request.tenantId(), "default"),
                defaultText(request.userId(), "system"),
                mapping.artifactType(),
                mapping.title(),
                mapping.mimeType(),
                stored.url(),
                previewText,
                provenanceJson,
                AgentArtifactScanStatus.CLEAN,
                Instant.now(clock));
        AgentArtifact saved = artifactRepository.save(artifact);
        appendArtifactEvent(request, saved);
    }

    private void saveArtifactReference(ToolInvocationRequest request,
                                       String storageRef,
                                       ArtifactMapping mapping,
                                       String previewText,
                                       String provenanceJson) {
        String artifactId = SnowflakeIds.nextIdString();
        AgentArtifact artifact = new AgentArtifact(
                artifactId,
                request.runId(),
                null,
                defaultText(request.tenantId(), "default"),
                defaultText(request.userId(), "system"),
                mapping.artifactType(),
                mapping.title(),
                mapping.mimeType(),
                storageRef.trim(),
                previewText,
                provenanceJson,
                AgentArtifactScanStatus.CLEAN,
                Instant.now(clock));
        AgentArtifact saved = artifactRepository.save(artifact);
        appendArtifactEvent(request, saved);
    }

    private Optional<Observation> parseTextObservation(String content) {
        if (!hasText(content)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            String artifactType = text(root, "artifactType");
            String format = text(root, "format");
            String artifactContent = text(root, "content");
            if (!hasText(artifactType) || !hasText(format) || !hasText(artifactContent)) {
                return Optional.empty();
            }
            return Optional.of(new Observation(artifactType, format, artifactContent));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private Optional<ImageObservation> parseImageObservation(String content) {
        if (!hasText(content)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            return Optional.of(new ImageObservation(
                    text(root, "status"),
                    text(root, "prompt"),
                    text(root, "model"),
                    text(root, "imageUrl"),
                    text(root, "b64Json"),
                    text(root, "mimeType")));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private void appendArtifactEvent(ToolInvocationRequest request, AgentArtifact artifact) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactId", artifact.artifactId());
        payload.put("runId", artifact.runId());
        payload.put("artifactType", artifact.artifactType().name());
        payload.put("title", artifact.title());
        payload.put("mimeType", artifact.mimeType());
        payload.put("storageRef", artifact.storageRef());
        payload.put("previewText", artifact.previewText());
        payload.put("scanStatus", artifact.scanStatus().name());
        payload.put("canPreview", artifact.canPreview());
        payload.put("disposition", artifact.disposition().headerValue());

        long nextSeq = eventBuffer.getLatestSeq(request.runId()).orElse(0L) + 1L;
        eventBuffer.append(request.runId(), StreamEventEnvelope.of(
                nextSeq,
                StreamEventType.AGENT_ARTIFACT,
                request.runId(),
                request.stepId(),
                payload));
    }

    private String provenanceJson(ToolInvocationRequest request, Observation observation) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("toolId", request.toolId());
        provenance.put("toolCallId", request.toolCallId());
        provenance.put("stepId", request.stepId());
        provenance.put("artifactType", observation.artifactType());
        provenance.put("format", observation.format());
        return writeJson(provenance);
    }

    private String provenanceJson(ToolInvocationRequest request, ImageObservation observation) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("toolId", request.toolId());
        provenance.put("toolCallId", request.toolCallId());
        provenance.put("stepId", request.stepId());
        provenance.put("status", observation.status());
        provenance.put("model", observation.model());
        provenance.put("imageUrl", observation.imageUrl());
        provenance.put("mimeType", observation.mimeType());
        return writeJson(provenance);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        return node == null || node.isNull() ? "" : node.asText("").trim();
    }

    private static String preview(String content) {
        String safe = Objects.requireNonNullElse(content, "");
        return safe.length() > PREVIEW_LIMIT ? safe.substring(0, PREVIEW_LIMIT) + "..." : safe;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String imageMimeType(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("image/") ? normalized : "image/png";
    }

    private static String imageExtension(String mimeType) {
        return switch (imageMimeType(mimeType)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private record Observation(String artifactType, String format, String content) {
    }

    private record ImageObservation(String status,
                                    String prompt,
                                    String model,
                                    String imageUrl,
                                    String b64Json,
                                    String mimeType) {
    }

    private record ArtifactMapping(AgentArtifactType artifactType,
                                   String title,
                                   String mimeType,
                                   String extension,
                                   String filenamePrefix) {

        private static ArtifactMapping from(Observation observation) {
            String artifactType = normalize(observation.artifactType());
            String format = normalize(observation.format());
            return switch (artifactType) {
                case "chart" -> chart(format);
                case "frontend_design" -> new ArtifactMapping(
                        AgentArtifactType.HTML,
                        "Generated frontend design",
                        "text/html",
                        ".html",
                        "generated-frontend-design");
                case "presentation" -> markdown("Generated presentation", "generated-presentation");
                case "newsletter" -> markdown("Generated newsletter", "generated-newsletter");
                default -> new ArtifactMapping(
                        AgentArtifactType.FILE,
                        "Generated artifact",
                        "text/plain",
                        ".txt",
                        "generated-artifact");
            };
        }

        private static ArtifactMapping chart(String format) {
            if ("application/json".equals(format)) {
                return new ArtifactMapping(
                        AgentArtifactType.CHART,
                        "Generated chart",
                        "application/json",
                        ".json",
                        "generated-chart");
            }
            return new ArtifactMapping(
                    AgentArtifactType.CHART,
                    "Generated chart",
                    "text/markdown",
                    ".md",
                    "generated-chart");
        }

        private static ArtifactMapping markdown(String title, String filenamePrefix) {
            return new ArtifactMapping(AgentArtifactType.MARKDOWN, title, "text/markdown", ".md", filenamePrefix);
        }

        private static String normalize(String value) {
            return Objects.requireNonNullElse(value, "")
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replace('-', '_');
        }
    }
}
