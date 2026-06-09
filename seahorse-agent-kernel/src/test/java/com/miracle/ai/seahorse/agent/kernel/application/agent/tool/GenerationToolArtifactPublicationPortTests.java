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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationToolArtifactPublicationPortTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void publishesTextGenerationObservationAsPersistedArtifactAndEvent() {
        MemoryArtifactRepository artifacts = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        MemoryEventBuffer events = new MemoryEventBuffer();
        GenerationToolArtifactPublicationPort publisher = new GenerationToolArtifactPublicationPort(
                artifacts,
                storage,
                events,
                new ObjectMapper(),
                CLOCK);

        publisher.publish(request(NewsletterGenerationToolPortAdapter.TOOL_ID), ToolInvocationResult.ok("""
                {"artifactType":"newsletter","format":"markdown","content":"# Issue\\nBody"}
                """));

        assertEquals(1, storage.uploads.size());
        MemoryObjectStorage.Upload upload = storage.uploads.get(0);
        assertEquals("agent-artifacts", upload.bucketName());
        assertEquals("# Issue\nBody", upload.content());
        assertTrue(upload.originalFilename().endsWith(".md"));
        assertEquals("text/markdown", upload.contentType());

        assertEquals(1, artifacts.saved.size());
        AgentArtifact artifact = artifacts.saved.get(0);
        assertEquals("run-1", artifact.runId());
        assertNull(artifact.messageId());
        assertEquals("tenant-1", artifact.tenantId());
        assertEquals("user-1", artifact.userId());
        assertEquals(AgentArtifactType.MARKDOWN, artifact.artifactType());
        assertEquals("Generated newsletter", artifact.title());
        assertEquals("text/markdown", artifact.mimeType());
        assertTrue(artifact.storageRef().startsWith("memory://"));
        assertEquals("# Issue\nBody", artifact.previewText());
        assertEquals(AgentArtifactScanStatus.CLEAN, artifact.scanStatus());
        assertEquals(CLOCK.instant(), artifact.createdAt());

        assertEquals(1, events.appended.size());
        StreamEventEnvelope event = events.appended.get(0);
        assertEquals(1L, event.eventSeq());
        assertEquals(StreamEventType.AGENT_ARTIFACT, event.eventType());
        assertEquals("run-1", event.runId());
        assertEquals("step-1", event.stepId());
        Map<?, ?> payload = assertInstanceOf(Map.class, event.typedPayload());
        assertEquals("MARKDOWN", payload.get("artifactType"));
        assertEquals("Generated newsletter", payload.get("title"));
        assertEquals("text/markdown", payload.get("mimeType"));
        assertEquals("# Issue\nBody", payload.get("previewText"));
        assertEquals("CLEAN", payload.get("scanStatus"));
        assertEquals(true, payload.get("canPreview"));
    }

    @Test
    void usesJsonFilenameForJsonChartArtifacts() {
        MemoryArtifactRepository artifacts = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        GenerationToolArtifactPublicationPort publisher = new GenerationToolArtifactPublicationPort(
                artifacts,
                storage,
                new MemoryEventBuffer(),
                new ObjectMapper(),
                CLOCK);

        publisher.publish(request(ChartVisualizationToolPortAdapter.TOOL_ID), ToolInvocationResult.ok("""
                {"artifactType":"chart","format":"application/json","content":"{\\"type\\":\\"bar\\"}"}
                """));

        assertEquals(1, storage.uploads.size());
        MemoryObjectStorage.Upload upload = storage.uploads.get(0);
        assertTrue(upload.originalFilename().endsWith(".json"));
        assertEquals("application/json", upload.contentType());
        assertEquals("{\"type\":\"bar\"}", upload.content());

        assertEquals(1, artifacts.saved.size());
        AgentArtifact artifact = artifacts.saved.get(0);
        assertEquals(AgentArtifactType.CHART, artifact.artifactType());
        assertEquals("application/json", artifact.mimeType());
    }

    @Test
    void publishesPresentationObservationAsMarkdownArtifact() {
        MemoryArtifactRepository artifacts = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        GenerationToolArtifactPublicationPort publisher = new GenerationToolArtifactPublicationPort(
                artifacts,
                storage,
                new MemoryEventBuffer(),
                new ObjectMapper(),
                CLOCK);

        publisher.publish(request(PptGenerationToolPortAdapter.TOOL_ID), ToolInvocationResult.ok("""
                {"artifactType":"presentation","format":"markdown","content":"# Slide 1"}
                """));

        assertEquals(1, storage.uploads.size());
        MemoryObjectStorage.Upload upload = storage.uploads.get(0);
        assertTrue(upload.originalFilename().endsWith(".md"));
        assertEquals("text/markdown", upload.contentType());

        assertEquals(1, artifacts.saved.size());
        AgentArtifact artifact = artifacts.saved.get(0);
        assertEquals(AgentArtifactType.MARKDOWN, artifact.artifactType());
        assertEquals("Generated presentation", artifact.title());
        assertEquals("# Slide 1", artifact.previewText());
    }

    @Test
    void publishesFrontendDesignObservationAsHtmlArtifact() {
        MemoryArtifactRepository artifacts = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        GenerationToolArtifactPublicationPort publisher = new GenerationToolArtifactPublicationPort(
                artifacts,
                storage,
                new MemoryEventBuffer(),
                new ObjectMapper(),
                CLOCK);

        publisher.publish(request(FrontendDesignToolPortAdapter.TOOL_ID), ToolInvocationResult.ok("""
                {"artifactType":"frontend_design","format":"html","content":"<main>Preview</main>"}
                """));

        assertEquals(1, storage.uploads.size());
        MemoryObjectStorage.Upload upload = storage.uploads.get(0);
        assertTrue(upload.originalFilename().endsWith(".html"));
        assertEquals("text/html", upload.contentType());

        assertEquals(1, artifacts.saved.size());
        AgentArtifact artifact = artifacts.saved.get(0);
        assertEquals(AgentArtifactType.HTML, artifact.artifactType());
        assertEquals("Generated frontend design", artifact.title());
        assertEquals("<main>Preview</main>", artifact.previewText());
    }

    @Test
    void publishesBase64ImageGenerationObservationAsImageArtifact() {
        MemoryArtifactRepository artifacts = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        MemoryEventBuffer events = new MemoryEventBuffer();
        GenerationToolArtifactPublicationPort publisher = new GenerationToolArtifactPublicationPort(
                artifacts,
                storage,
                events,
                new ObjectMapper(),
                CLOCK);
        byte[] pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};
        String b64 = Base64.getEncoder().encodeToString(pngBytes);

        publisher.publish(request(ImageGenerationToolPortAdapter.TOOL_ID), ToolInvocationResult.ok("""
                {"status":"GENERATED","prompt":"Draw a seahorse","model":"image-model","imageUrl":"","b64Json":"%s","mimeType":"image/png"}
                """.formatted(b64)));

        assertEquals(1, storage.uploads.size());
        MemoryObjectStorage.Upload upload = storage.uploads.get(0);
        assertEquals("agent-artifacts", upload.bucketName());
        assertEquals(pngBytes.length, upload.size());
        assertTrue(upload.originalFilename().endsWith(".png"));
        assertEquals("image/png", upload.contentType());
        assertEquals(List.of((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47), upload.bytes());

        assertEquals(1, artifacts.saved.size());
        AgentArtifact artifact = artifacts.saved.get(0);
        assertEquals(AgentArtifactType.IMAGE, artifact.artifactType());
        assertEquals("Generated image", artifact.title());
        assertEquals("image/png", artifact.mimeType());
        assertEquals("Draw a seahorse", artifact.previewText());
        assertEquals(AgentArtifactScanStatus.CLEAN, artifact.scanStatus());

        assertEquals(1, events.appended.size());
        StreamEventEnvelope event = events.appended.get(0);
        Map<?, ?> payload = assertInstanceOf(Map.class, event.typedPayload());
        assertEquals("IMAGE", payload.get("artifactType"));
        assertEquals("Generated image", payload.get("title"));
        assertEquals("image/png", payload.get("mimeType"));
        assertEquals("Draw a seahorse", payload.get("previewText"));
        assertEquals(false, payload.get("canPreview"));
        assertEquals("attachment", payload.get("disposition"));
    }

    @Test
    void ignoresNonGenerationToolsEvenWhenResultLooksLikeArtifactObservation() {
        MemoryArtifactRepository artifacts = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        MemoryEventBuffer events = new MemoryEventBuffer();
        GenerationToolArtifactPublicationPort publisher = new GenerationToolArtifactPublicationPort(
                artifacts,
                storage,
                events,
                new ObjectMapper(),
                CLOCK);

        publisher.publish(request("memory-write"), ToolInvocationResult.ok("""
                {"artifactType":"newsletter","format":"markdown","content":"# Issue"}
                """));

        assertEquals(0, storage.uploads.size());
        assertEquals(0, artifacts.saved.size());
        assertEquals(0, events.appended.size());
    }

    @Test
    void usesSystemUserFallbackWhenToolRequestHasNoUserId() {
        MemoryArtifactRepository artifacts = new MemoryArtifactRepository();
        GenerationToolArtifactPublicationPort publisher = new GenerationToolArtifactPublicationPort(
                artifacts,
                new MemoryObjectStorage(),
                new MemoryEventBuffer(),
                new ObjectMapper(),
                CLOCK);

        publisher.publish(request(NewsletterGenerationToolPortAdapter.TOOL_ID, null), ToolInvocationResult.ok("""
                {"artifactType":"newsletter","format":"markdown","content":"# Issue"}
                """));

        assertEquals(1, artifacts.saved.size());
        assertEquals("system", artifacts.saved.get(0).userId());
    }

    private static ToolInvocationRequest request(String toolId) {
        return request(toolId, "user-1");
    }

    private static ToolInvocationRequest request(String toolId, String userId) {
        return new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                userId,
                "agent-identity-1",
                toolId,
                Map.of(),
                Map.of(),
                "run-1:call-1",
                List.of(toolId));
    }

    private static final class MemoryArtifactRepository implements AgentArtifactRepositoryPort {

        private final List<AgentArtifact> saved = new ArrayList<>();

        @Override
        public AgentArtifact save(AgentArtifact artifact) {
            saved.add(artifact);
            return artifact;
        }

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return saved.stream()
                    .filter(artifact -> artifact.artifactId().equals(artifactId))
                    .findFirst();
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            return saved.stream()
                    .filter(artifact -> runId.equals(artifact.runId()))
                    .toList();
        }
    }

    private static final class MemoryObjectStorage implements ObjectStoragePort {

        private final List<Upload> uploads = new ArrayList<>();

        @Override
        public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                                   String contentType) {
            byte[] bytes = readBytes(content);
            uploads.add(new Upload(bucketName, bytes, size, originalFilename, contentType));
            return new StoredObject("memory://" + originalFilename, contentType, size, originalFilename);
        }

        @Override
        public InputStream openStream(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByUrl(String url) {
            throw new UnsupportedOperationException();
        }

        private static byte[] readBytes(InputStream input) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                return output.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private record Upload(String bucketName, byte[] rawContent, long size, String originalFilename,
                              String contentType) {

            String content() {
                return new String(rawContent, StandardCharsets.UTF_8);
            }

            List<Byte> bytes() {
                List<Byte> result = new ArrayList<>();
                for (byte b : rawContent) {
                    result.add(b);
                }
                return result;
            }
        }
    }

    private static final class MemoryEventBuffer implements AgentRunEventBufferPort {

        private final List<StreamEventEnvelope> appended = new ArrayList<>();

        @Override
        public void append(String runId, StreamEventEnvelope event) {
            appended.add(event);
        }

        @Override
        public List<StreamEventEnvelope> getAfter(String runId, long afterSeq) {
            return appended.stream()
                    .filter(event -> event.runId().equals(runId))
                    .filter(event -> event.eventSeq() > afterSeq)
                    .toList();
        }

        @Override
        public Optional<Long> getLatestSeq(String runId) {
            return appended.stream()
                    .filter(event -> event.runId().equals(runId))
                    .map(StreamEventEnvelope::eventSeq)
                    .max(Long::compareTo);
        }

        @Override
        public void expire(String runId) {
            appended.removeIf(event -> event.runId().equals(runId));
        }
    }
}
