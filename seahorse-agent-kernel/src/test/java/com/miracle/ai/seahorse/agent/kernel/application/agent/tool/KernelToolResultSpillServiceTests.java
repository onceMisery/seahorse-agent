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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelToolResultSpillServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldLeaveResultsAtOrBelowThresholdUnchanged() {
        MemoryArtifactRepository repository = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        KernelToolResultSpillService service = service(repository, storage);
        ToolInvocationResult original = ToolInvocationResult.ok("1234567890");

        ToolInvocationResult result = service.spill(request("web_fetch"), original);

        assertSame(original, result);
        assertNull(repository.saved);
        assertNull(storage.uploadedBytes);
    }

    @Test
    void shouldPersistUtf8ContentWithIntegrityMetadata() throws Exception {
        MemoryArtifactRepository repository = new MemoryArtifactRepository();
        MemoryObjectStorage storage = new MemoryObjectStorage();
        KernelToolResultSpillService service = service(repository, storage);
        String content = "head-你好-" + "x".repeat(18);
        byte[] expectedBytes = content.getBytes(StandardCharsets.UTF_8);
        String expectedSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(expectedBytes));

        ToolInvocationResult result = service.spill(request("web_fetch"), ToolInvocationResult.ok(content));

        assertTrue(result.success());
        JsonNode pointer = objectMapper.readTree(result.content());
        assertEquals(KernelToolResultSpillService.PROVENANCE_KIND, pointer.path("kind").asText());
        assertEquals("web_fetch", pointer.path("toolId").asText());
        assertEquals(content.length(), pointer.path("contentChars").asInt());
        assertEquals(expectedBytes.length, pointer.path("contentBytes").asInt());
        assertEquals(expectedSha256, pointer.path("contentSha256").asText());
        assertEquals(KernelToolResultSpillService.TEXT_CONTENT_TYPE, pointer.path("contentType").asText());
        assertEquals("head-...", pointer.path("preview").asText());
        assertEquals(ToolResultReadToolPortAdapter.TOOL_ID, pointer.path("readToolId").asText());

        assertEquals(KernelToolResultSpillService.ARTIFACT_BUCKET, storage.bucketName);
        assertEquals(KernelToolResultSpillService.TEXT_CONTENT_TYPE, storage.contentType);
        assertEquals(expectedBytes.length, storage.size);
        assertEquals(content, new String(storage.uploadedBytes, StandardCharsets.UTF_8));
        assertEquals(NOW, repository.saved.createdAt());
        assertEquals("run-1", repository.saved.runId());
        assertEquals("tenant-1", repository.saved.tenantId());
        assertEquals("user-1", repository.saved.userId());
        assertEquals(KernelToolResultSpillService.TEXT_CONTENT_TYPE, repository.saved.mimeType());
        assertEquals("head-...", repository.saved.previewText());

        JsonNode provenance = objectMapper.readTree(repository.saved.provenanceJson());
        assertEquals(KernelToolResultSpillService.PROVENANCE_KIND, provenance.path("kind").asText());
        assertEquals("call-1", provenance.path("toolCallId").asText());
        assertEquals("step-1", provenance.path("stepId").asText());
        assertEquals(content.length(), provenance.path("contentChars").asInt());
        assertEquals(expectedBytes.length, provenance.path("contentBytes").asInt());
        assertEquals(expectedSha256, provenance.path("contentSha256").asText());
        assertEquals(KernelToolResultSpillService.TEXT_CONTENT_TYPE, provenance.path("contentType").asText());
    }

    @Test
    void shouldFailClosedAndDeleteUploadedObjectWhenArtifactSaveFails() {
        MemoryArtifactRepository repository = new MemoryArtifactRepository();
        repository.failOnSave = true;
        MemoryObjectStorage storage = new MemoryObjectStorage();
        KernelToolResultSpillService service = service(repository, storage);

        ToolInvocationResult result = service.spill(
                request("web_fetch"),
                ToolInvocationResult.ok("oversized-result"));

        assertFalse(result.success());
        assertEquals("Tool result exceeded the context limit and could not be persisted", result.error());
        assertEquals(storage.uploadedUrl, storage.deletedUrl);
    }

    private KernelToolResultSpillService service(MemoryArtifactRepository repository,
                                                  MemoryObjectStorage storage) {
        return new KernelToolResultSpillService(
                repository,
                storage,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ToolResultSpillOptions(true, 10, 5, 6));
    }

    private ToolInvocationRequest request(String toolId) {
        return new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "rollout-1",
                "tenant-1",
                "user-1",
                "identity-1",
                toolId,
                Map.of(),
                Map.of(),
                "run-1:call-1",
                List.of(toolId));
    }

    private static final class MemoryArtifactRepository implements AgentArtifactRepositoryPort {

        private AgentArtifact saved;
        private boolean failOnSave;

        @Override
        public AgentArtifact save(AgentArtifact artifact) {
            if (failOnSave) {
                throw new IllegalStateException("database unavailable");
            }
            saved = artifact;
            return artifact;
        }

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return Optional.ofNullable(saved)
                    .filter(artifact -> artifact.artifactId().equals(artifactId));
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            return saved != null && saved.runId().equals(runId) ? List.of(saved) : List.of();
        }
    }

    private static final class MemoryObjectStorage implements ObjectStoragePort {

        private String bucketName;
        private byte[] uploadedBytes;
        private long size;
        private String contentType;
        private String uploadedUrl;
        private String deletedUrl;

        @Override
        public StoredObject upload(String bucketName,
                                   InputStream content,
                                   long size,
                                   String originalFilename,
                                   String contentType) {
            try {
                this.uploadedBytes = content.readAllBytes();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            this.bucketName = bucketName;
            this.size = size;
            this.contentType = contentType;
            this.uploadedUrl = "memory://" + bucketName + "/" + originalFilename;
            return new StoredObject(uploadedUrl, contentType, size, originalFilename);
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream(uploadedBytes == null ? new byte[0] : uploadedBytes);
        }

        @Override
        public void deleteByUrl(String url) {
            deletedUrl = url;
        }
    }
}
