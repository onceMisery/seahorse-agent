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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactDisposition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

@RestController
public class SeahorseAgentArtifactController {

    private final ObjectProvider<AgentArtifactQueryInboundPort> artifactQueryPortProvider;
    private final ObjectProvider<ObjectStoragePort> objectStoragePortProvider;

    public SeahorseAgentArtifactController(ObjectProvider<AgentArtifactQueryInboundPort> artifactQueryPortProvider,
                                           ObjectProvider<ObjectStoragePort> objectStoragePortProvider) {
        this.artifactQueryPortProvider = artifactQueryPortProvider;
        this.objectStoragePortProvider = objectStoragePortProvider;
    }

    @GetMapping("/api/agent-artifacts/{artifactId}")
    public ApiResponse<AgentArtifactResponse> getById(@PathVariable String artifactId) {
        return ApiResponses.requireService(artifactQueryPortProvider,
                port -> AgentArtifactResponse.from(port.getById(artifactId)));
    }

    @GetMapping("/api/agent-runs/{runId}/artifacts")
    public ApiResponse<List<AgentArtifactResponse>> listByRunId(@PathVariable String runId) {
        return ApiResponses.requireService(artifactQueryPortProvider,
                port -> port.listByRunId(runId).stream()
                        .map(AgentArtifactResponse::from)
                        .toList());
    }

    @GetMapping("/api/agent-artifacts/{artifactId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String artifactId) {
        AgentArtifactQueryInboundPort artifactPort = requireArtifactPort();
        ObjectStoragePort storagePort = requireStoragePort();
        AgentArtifactDownloadDecision decision = artifactPort.downloadDecision(artifactId);
        InputStream stream = storagePort.openStream(decision.storageRef());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(decision.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(decision))
                .body(new InputStreamResource(stream));
    }

    private AgentArtifactQueryInboundPort requireArtifactPort() {
        AgentArtifactQueryInboundPort port = artifactQueryPortProvider == null
                ? null
                : artifactQueryPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }

    private ObjectStoragePort requireStoragePort() {
        ObjectStoragePort port = objectStoragePortProvider == null
                ? null
                : objectStoragePortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE);
        }
        return port;
    }

    private String contentDisposition(AgentArtifactDownloadDecision decision) {
        return decision.disposition().headerValue() + "; filename=\"" + decision.filename() + "\"";
    }

    public record AgentArtifactResponse(String artifactId,
                                        String runId,
                                        String messageId,
                                        String tenantId,
                                        String userId,
                                        AgentArtifactType artifactType,
                                        String title,
                                        String mimeType,
                                        String previewText,
                                        String provenanceJson,
                                        AgentArtifactScanStatus scanStatus,
                                        Instant createdAt,
                                        boolean canPreview,
                                        AgentArtifactDisposition disposition) {

        static AgentArtifactResponse from(AgentArtifact artifact) {
            return new AgentArtifactResponse(
                    artifact.artifactId(),
                    artifact.runId(),
                    artifact.messageId(),
                    artifact.tenantId(),
                    artifact.userId(),
                    artifact.artifactType(),
                    artifact.title(),
                    artifact.mimeType(),
                    artifact.previewText(),
                    artifact.provenanceJson(),
                    artifact.scanStatus(),
                    artifact.createdAt(),
                    artifact.canPreview(),
                    artifact.disposition());
        }
    }
}
