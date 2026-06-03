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
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactUpdateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactUpdateInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class KernelAgentArtifactUpdateService implements AgentArtifactUpdateInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelAgentArtifactUpdateService.class);
    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "Access denied";
    private static final String ARTIFACT_BUCKET = "agent-artifacts";

    private final AgentArtifactRepositoryPort artifactRepository;
    private final ObjectStoragePort objectStoragePort;
    private final CurrentUserPort currentUserPort;

    public KernelAgentArtifactUpdateService(AgentArtifactRepositoryPort artifactRepository,
                                            ObjectStoragePort objectStoragePort,
                                            CurrentUserPort currentUserPort) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository,
                "artifactRepository must not be null");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public AgentArtifact updateContent(String artifactId, AgentArtifactUpdateCommand command) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        AgentArtifact current = artifactRepository.findById(requireText(artifactId, "artifactId must not be blank"))
                .map(artifact -> requireWritable(artifact, currentUser))
                .orElseThrow(() -> new IllegalArgumentException("Agent artifact not found"));
        String content = Objects.requireNonNull(command, "command must not be null").content();
        byte[] bytes = Objects.requireNonNullElse(content, "").getBytes(StandardCharsets.UTF_8);
        StoredObject stored = objectStoragePort.upload(
                ARTIFACT_BUCKET,
                new ByteArrayInputStream(bytes),
                bytes.length,
                filename(current),
                current.mimeType());
        AgentArtifact updated = new AgentArtifact(
                current.artifactId(),
                current.runId(),
                current.messageId(),
                current.tenantId(),
                current.userId(),
                current.artifactType(),
                current.title(),
                current.mimeType(),
                stored.url(),
                content,
                current.provenanceJson(),
                AgentArtifactScanStatus.PENDING,
                current.createdAt());
        try {
            AgentArtifact saved = artifactRepository.save(updated);
            if (current.storageRef() != null && !current.storageRef().equals(stored.url())) {
                objectStoragePort.deleteByUrl(current.storageRef());
            }
            return saved;
        } catch (RuntimeException e) {
            try {
                objectStoragePort.deleteByUrl(stored.url());
            } catch (RuntimeException cleanup) {
                LOG.warn("Failed to clean up orphaned object after DB save failure: {}", stored.url(), cleanup);
            }
            throw e;
        }
    }

    private AgentArtifact requireWritable(AgentArtifact artifact, CurrentUser currentUser) {
        if (isAdmin(currentUser) || Objects.equals(currentUserId(currentUser), artifact.userId())) {
            return artifact;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private String filename(AgentArtifact artifact) {
        String title = artifact.title() == null ? artifact.artifactId() : artifact.title();
        return title.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String currentUserId(CurrentUser currentUser) {
        return currentUser == null || currentUser.userId() == null ? null : String.valueOf(currentUser.userId());
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
