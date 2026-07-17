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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@ConditionalOnProperty(prefix = "seahorse.agent.sandbox.node-transport", name = "enabled", havingValue = "true")
public class SeahorseSandboxRuntimeTransportController {

    public static final String BASE_PATH = "/internal/sandbox/runtime";
    public static final String SESSION_PATH = BASE_PATH + "/sessions";
    public static final String EXECUTION_PATH = BASE_PATH + "/executions";
    public static final String SESSION_OWNERSHIP_PATH = BASE_PATH + "/session-ownership";
    public static final String CLOSE_PATH = BASE_PATH + "/close";
    public static final String ARTIFACT_PATH = BASE_PATH + "/artifacts";

    private final SandboxRuntimePort localRuntimePort;
    private final SandboxRuntimeTransportAuthenticator authenticator;
    private final ObjectMapper objectMapper;
    private final long maxArtifactBytes;
    private final Map<String, RemoteArtifact> artifacts = new ConcurrentHashMap<>();

    public SeahorseSandboxRuntimeTransportController(SandboxRuntimePort localRuntimePort,
                                                     SandboxRuntimeTransportAuthenticator authenticator,
                                                     ObjectMapper objectMapper,
                                                     @Value("${seahorse.agent.sandbox.node-transport.max-artifact-bytes:67108864}")
                                                     long maxArtifactBytes) {
        this.localRuntimePort = Objects.requireNonNull(localRuntimePort, "localRuntimePort must not be null");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (maxArtifactBytes <= 0L || maxArtifactBytes > 256L * 1024L * 1024L) {
            throw new IllegalArgumentException("maxArtifactBytes must be between 1 byte and 256 MiB");
        }
        this.maxArtifactBytes = maxArtifactBytes;
    }

    @PostMapping(SESSION_PATH)
    public SandboxSession createSession(@RequestBody String body,
                                        @RequestHeader Map<String, String> headers,
                                        HttpServletRequest servletRequest) throws IOException {
        authenticate(servletRequest, body, headers);
        return localRuntimePort.createSession(objectMapper.readValue(body, SandboxSessionRequest.class));
    }

    @PostMapping(EXECUTION_PATH)
    public SandboxRuntimeTransportProtocol.ExecutionResponse execute(
            @RequestBody String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest servletRequest) throws IOException {
        authenticate(servletRequest, body, headers);
        purgeExpiredArtifacts();
        SandboxExecutionResult result = localRuntimePort.execute(
                objectMapper.readValue(body, SandboxExecutionRequest.class));
        for (SandboxArtifact artifact : result.artifacts()) {
            artifacts.put(artifactKey(artifact.sessionId(), artifact.executionId(), artifact.artifactId()),
                    RemoteArtifact.from(artifact));
        }
        return SandboxRuntimeTransportProtocol.ExecutionResponse.from(result);
    }

    @PostMapping(SESSION_OWNERSHIP_PATH)
    public SandboxRuntimeTransportProtocol.SessionOwnershipResponse inspectSessionOwnership(
            @RequestBody String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest servletRequest) throws IOException {
        authenticate(servletRequest, body, headers);
        SandboxRuntimeTransportProtocol.SessionOwnershipRequest request = objectMapper.readValue(
                body,
                SandboxRuntimeTransportProtocol.SessionOwnershipRequest.class);
        return new SandboxRuntimeTransportProtocol.SessionOwnershipResponse(
                request.sessionId(),
                localRuntimePort.inspectSessionOwnership(request.sessionId()));
    }

    @PostMapping(CLOSE_PATH)
    public SandboxSession closeSession(@RequestBody String body,
                                       @RequestHeader Map<String, String> headers,
                                       HttpServletRequest servletRequest) throws IOException {
        authenticate(servletRequest, body, headers);
        SandboxSession session = objectMapper.readValue(body, SandboxSession.class);
        SandboxSession closed = localRuntimePort.closeSession(session);
        artifacts.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(session.sessionId()));
        return closed;
    }

    @PostMapping(ARTIFACT_PATH)
    public ResponseEntity<InputStreamResource> artifact(@RequestBody String body,
                                                        @RequestHeader Map<String, String> headers,
                                                        HttpServletRequest servletRequest) throws IOException {
        authenticate(servletRequest, body, headers);
        purgeExpiredArtifacts();
        SandboxRuntimeTransportProtocol.ArtifactRequest request = objectMapper.readValue(
                body,
                SandboxRuntimeTransportProtocol.ArtifactRequest.class);
        RemoteArtifact artifact = artifacts.get(artifactKey(
                request.sessionId(), request.executionId(), request.artifactId()));
        if (artifact == null) {
            return ResponseEntity.notFound().build();
        }
        long size = Files.size(artifact.path());
        if (size < 0L || size > maxArtifactBytes) {
            return ResponseEntity.status(413).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(Files.newInputStream(artifact.path())));
    }

    private void authenticate(HttpServletRequest request, String body, Map<String, String> headers) {
        authenticator.authenticate(request.getMethod(), request.getRequestURI(), body, headers);
    }

    private void purgeExpiredArtifacts() {
        Instant now = Instant.now();
        artifacts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static String artifactKey(String sessionId, String executionId, String artifactId) {
        return requireText(sessionId) + "|" + requireText(executionId) + "|" + requireText(artifactId);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sandbox transport artifact identity must not be blank");
        }
        return value.trim();
    }

    private record RemoteArtifact(String sessionId, Path path, Instant expiresAt) {

        private static RemoteArtifact from(SandboxArtifact artifact) {
            URI uri = URI.create(artifact.objectUri());
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalStateException("Sandbox transport only supports local runtime artifact files");
            }
            Path path = Path.of(uri).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Sandbox transport artifact file is unavailable");
            }
            return new RemoteArtifact(artifact.sessionId(), path, artifact.createdAt().plusSeconds(3600L));
        }
    }
}
