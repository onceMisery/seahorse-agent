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

package com.miracle.ai.seahorse.agent.kernel.application.agent.artifact;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactUpdateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KernelAgentArtifactUpdateServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldResetScanStatusWhenCleanArtifactContentChanges() {
        MemoryArtifactRepository artifactRepository = new MemoryArtifactRepository(List.of(
                artifact("artifact-1", "user-1", AgentArtifactScanStatus.CLEAN)));
        CapturingObjectStoragePort storagePort = new CapturingObjectStoragePort();
        KernelAgentArtifactUpdateService service = new KernelAgentArtifactUpdateService(
                artifactRepository,
                storagePort,
                currentUser(1L, "user"));

        AgentArtifact updated = service.updateContent(
                "artifact-1",
                new AgentArtifactUpdateCommand("new report content"));

        assertEquals(AgentArtifactScanStatus.PENDING, updated.scanStatus());
        assertFalse(updated.downloadable());
        assertEquals("new report content", updated.previewText());
        assertEquals("new report content", storagePort.uploadedContent);
        assertEquals("s3://agent-artifacts/artifact-1", storagePort.deletedUrl);
    }

    private static AgentArtifact artifact(String artifactId, String userId, AgentArtifactScanStatus scanStatus) {
        return new AgentArtifact(
                artifactId,
                "run-1",
                "message-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                AgentArtifactType.MARKDOWN,
                "Research report",
                "text/markdown",
                "s3://agent-artifacts/" + artifactId,
                "old report content",
                "{}",
                scanStatus,
                NOW);
    }

    private static CurrentUserPort currentUser(Long userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, role + "-" + userId, role, null));
    }

    private static final class MemoryArtifactRepository implements AgentArtifactRepositoryPort {

        private final Map<String, AgentArtifact> artifacts = new LinkedHashMap<>();

        private MemoryArtifactRepository(List<AgentArtifact> artifacts) {
            artifacts.forEach(artifact -> this.artifacts.put(artifact.artifactId(), artifact));
        }

        @Override
        public AgentArtifact save(AgentArtifact artifact) {
            artifacts.put(artifact.artifactId(), artifact);
            return artifact;
        }

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            return artifacts.values().stream()
                    .filter(artifact -> runId.equals(artifact.runId()))
                    .toList();
        }
    }

    private static final class CapturingObjectStoragePort implements ObjectStoragePort {

        private String uploadedContent;
        private String deletedUrl;

        @Override
        public StoredObject upload(String bucketName,
                                   InputStream content,
                                   long size,
                                   String originalFilename,
                                   String contentType) {
            try {
                uploadedContent = new String(content.readAllBytes());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            return new StoredObject(
                    "memory://" + bucketName + "/" + originalFilename,
                    contentType,
                    size,
                    originalFilename);
        }

        @Override
        public InputStream openStream(String url) {
            return InputStream.nullInputStream();
        }

        @Override
        public void deleteByUrl(String url) {
            deletedUrl = url;
        }
    }
}
