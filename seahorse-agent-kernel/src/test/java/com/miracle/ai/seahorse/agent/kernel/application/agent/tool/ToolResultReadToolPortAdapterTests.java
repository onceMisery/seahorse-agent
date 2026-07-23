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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultReadToolPortAdapterTests {

    private static final String CONTENT = "012345你好abcdefghij";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadBoundedCharacterRangeFromOwnedSpillArtifact() throws Exception {
        AgentArtifact artifact = artifact(
                "artifact-1", "run-1", "tenant-1", "user-1", spillProvenance());
        ToolResultReadToolPortAdapter adapter = adapter(artifact);

        ToolInvocationResult result = adapter.invoke(request(
                "run-1", "tenant-1", "user-1", "artifact-1", 6, 5));

        assertTrue(result.success());
        JsonNode payload = objectMapper.readTree(result.content());
        assertEquals("artifact-1", payload.path("artifactId").asText());
        assertEquals(6, payload.path("offset").asLong());
        assertEquals(5, payload.path("returnedChars").asInt());
        assertEquals(11, payload.path("nextOffset").asLong());
        assertTrue(payload.path("hasMore").asBoolean());
        assertEquals(CONTENT.substring(6, 11), payload.path("content").asText());
    }

    @Test
    void shouldDenyWrongRunTenantUserAndProvenance() {
        AgentArtifact spill = artifact(
                "artifact-1", "run-1", "tenant-1", "user-1", spillProvenance());
        ToolResultReadToolPortAdapter adapter = adapter(spill);

        assertDenied(adapter.invoke(request("run-2", "tenant-1", "user-1", "artifact-1", 0, 4)));
        assertDenied(adapter.invoke(request("run-1", "tenant-2", "user-1", "artifact-1", 0, 4)));
        assertDenied(adapter.invoke(request("run-1", "tenant-1", "user-2", "artifact-1", 0, 4)));

        ToolResultReadToolPortAdapter wrongProvenanceAdapter = adapter(artifact(
                "artifact-2", "run-1", "tenant-1", "user-1", "{\"kind\":\"generated_file\"}"));
        assertDenied(wrongProvenanceAdapter.invoke(
                request("run-1", "tenant-1", "user-1", "artifact-2", 0, 4)));
    }

    @Test
    void shouldEnforceConfiguredReadLimit() {
        ToolResultReadToolPortAdapter adapter = adapter(artifact(
                "artifact-1", "run-1", "tenant-1", "user-1", spillProvenance()));

        ToolInvocationResult result = adapter.invoke(request(
                "run-1", "tenant-1", "user-1", "artifact-1", 0, 9));

        assertFalse(result.success());
        assertEquals("limit must be between 1 and 8", result.error());
    }

    private void assertDenied(ToolInvocationResult result) {
        assertFalse(result.success());
        assertEquals("Tool result artifact was not found or is not accessible", result.error());
    }

    private ToolResultReadToolPortAdapter adapter(AgentArtifact artifact) {
        AgentArtifactRepositoryPort repository = new SingleArtifactRepository(artifact);
        ObjectStoragePort storage = new ReadOnlyObjectStorage(CONTENT);
        return new ToolResultReadToolPortAdapter(
                repository,
                storage,
                objectMapper,
                new ToolResultSpillOptions(true, 20, 5, 8));
    }

    private AgentArtifact artifact(String artifactId,
                                   String runId,
                                   String tenantId,
                                   String userId,
                                   String provenance) {
        return new AgentArtifact(
                artifactId,
                runId,
                null,
                tenantId,
                userId,
                AgentArtifactType.FILE,
                "Tool result",
                KernelToolResultSpillService.TEXT_CONTENT_TYPE,
                "memory://agent-artifacts/" + artifactId,
                "preview",
                provenance,
                AgentArtifactScanStatus.CLEAN,
                Instant.parse("2026-07-23T00:00:00Z"));
    }

    private String spillProvenance() {
        return "{\"kind\":\"" + KernelToolResultSpillService.PROVENANCE_KIND + "\"}";
    }

    private ToolInvocationRequest request(String runId,
                                          String tenantId,
                                          String userId,
                                          String artifactId,
                                          int offset,
                                          int limit) {
        return new ToolInvocationRequest(
                runId,
                "step-1",
                "call-1",
                "agent-1",
                null,
                null,
                tenantId,
                userId,
                userId,
                ToolResultReadToolPortAdapter.TOOL_ID,
                Map.of("artifactId", artifactId, "offset", offset, "limit", limit),
                Map.of(),
                "read-" + runId + "-" + userId,
                List.of(ToolResultReadToolPortAdapter.TOOL_ID));
    }

    private record SingleArtifactRepository(AgentArtifact artifact) implements AgentArtifactRepositoryPort {

        @Override
        public AgentArtifact save(AgentArtifact savedArtifact) {
            return savedArtifact;
        }

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return Optional.ofNullable(artifact)
                    .filter(candidate -> candidate.artifactId().equals(artifactId));
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            return artifact != null && artifact.runId().equals(runId) ? List.of(artifact) : List.of();
        }
    }

    private record ReadOnlyObjectStorage(String content) implements ObjectStoragePort {

        @Override
        public StoredObject upload(String bucketName,
                                   InputStream content,
                                   long size,
                                   String originalFilename,
                                   String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void deleteByUrl(String url) {
            throw new UnsupportedOperationException();
        }
    }
}
