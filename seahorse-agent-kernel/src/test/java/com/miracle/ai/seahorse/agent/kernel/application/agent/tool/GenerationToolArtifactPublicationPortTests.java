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
            String body = readUtf8(content);
            uploads.add(new Upload(bucketName, body, size, originalFilename, contentType));
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

        private static String readUtf8(InputStream input) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                return output.toString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private record Upload(String bucketName, String content, long size, String originalFilename,
                              String contentType) {
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
