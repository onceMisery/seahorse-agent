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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactDisposition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentArtifactQueryService implements AgentArtifactQueryInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "鏉冮檺涓嶈冻";
    private static final String DOWNLOAD_BLOCKED = "Artifact is not available for download";
    private static final Map<String, String> FILE_EXTENSIONS = Map.ofEntries(
            Map.entry("text/html", ".html"),
            Map.entry("text/markdown", ".md"),
            Map.entry("text/plain", ".txt"),
            Map.entry("text/csv", ".csv"),
            Map.entry("application/json", ".json"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/svg+xml", ".svg"));

    private final AgentArtifactRepositoryPort artifactRepository;
    private final AgentRunRepositoryPort runRepository;
    private final CurrentUserPort currentUserPort;

    public KernelAgentArtifactQueryService(AgentArtifactRepositoryPort artifactRepository,
                                           AgentRunRepositoryPort runRepository,
                                           CurrentUserPort currentUserPort) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository,
                "artifactRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public Optional<AgentArtifact> findById(String artifactId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        return artifactRepository.findById(requireText(artifactId, "artifactId must not be blank"))
                .map(artifact -> requireReadable(artifact, currentUser));
    }

    @Override
    public List<AgentArtifact> listByRunId(String runId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeRunId = requireText(runId, "runId must not be blank");
        AgentRun run = runRepository.findRunById(safeRunId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
        requireReadable(run, currentUser);
        return artifactRepository.listByRunId(safeRunId).stream()
                .filter(artifact -> isAdmin(currentUser) || currentUser.userId().equals(artifact.userId()))
                .toList();
    }

    @Override
    public AgentArtifactDownloadDecision downloadDecision(String artifactId) {
        AgentArtifact artifact = getById(artifactId);
        if (!artifact.downloadable()) {
            throw new IllegalStateException(DOWNLOAD_BLOCKED);
        }
        AgentArtifactDisposition disposition = artifact.disposition();
        return new AgentArtifactDownloadDecision(
                artifact,
                disposition,
                artifact.mimeType(),
                filename(artifact),
                artifact.storageRef(),
                disposition == AgentArtifactDisposition.INLINE_PREVIEW);
    }

    private AgentArtifact requireReadable(AgentArtifact artifact, CurrentUser currentUser) {
        if (isAdmin(currentUser) || currentUser.userId().equals(artifact.userId())) {
            return artifact;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private AgentRun requireReadable(AgentRun run, CurrentUser currentUser) {
        if (isAdmin(currentUser) || currentUser.userId().equals(run.userId())) {
            return run;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private String filename(AgentArtifact artifact) {
        String safeBase = artifact.artifactId().replaceAll("[^A-Za-z0-9._-]", "_");
        String extension = FILE_EXTENSIONS.getOrDefault(normalizedMimeType(artifact.mimeType()), ".bin");
        if (safeBase.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return safeBase;
        }
        return safeBase + extension;
    }

    private String normalizedMimeType(String mimeType) {
        int separator = mimeType.indexOf(';');
        String base = separator >= 0 ? mimeType.substring(0, separator) : mimeType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
