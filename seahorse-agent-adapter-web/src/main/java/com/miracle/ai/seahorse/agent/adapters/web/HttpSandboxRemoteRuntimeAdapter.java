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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeEndpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRemoteRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class HttpSandboxRemoteRuntimeAdapter implements SandboxRemoteRuntimePort {

    private static final int BUFFER_BYTES = 16 * 1024;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SandboxRuntimeTransportSigner signer;
    private final Duration requestTimeout;
    private final long maxArtifactBytes;
    private final Map<String, Path> sessionTempDirectories = new ConcurrentHashMap<>();

    public HttpSandboxRemoteRuntimeAdapter(ObjectMapper objectMapper,
                                           String sharedSecret,
                                           Duration connectTimeout,
                                           Duration requestTimeout,
                                           long maxArtifactBytes) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.signer = new SandboxRuntimeTransportSigner(sharedSecret);
        this.requestTimeout = positiveDuration(requestTimeout, Duration.ofSeconds(60), "requestTimeout");
        this.maxArtifactBytes = requireMaxArtifactBytes(maxArtifactBytes);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(positiveDuration(connectTimeout, Duration.ofSeconds(5), "connectTimeout"))
                .build();
    }

    @Override
    public SandboxSession createSession(SandboxRuntimeNodeEndpoint endpoint, SandboxSessionRequest request) {
        return invokeJson(endpoint, SeahorseSandboxRuntimeTransportController.SESSION_PATH, request, SandboxSession.class);
    }

    @Override
    public SandboxExecutionResult execute(SandboxRuntimeNodeEndpoint endpoint, SandboxExecutionRequest request) {
        SandboxRuntimeTransportProtocol.ExecutionResponse response = invokeJson(
                endpoint,
                SeahorseSandboxRuntimeTransportController.EXECUTION_PATH,
                request,
                SandboxRuntimeTransportProtocol.ExecutionResponse.class);
        if (response.artifacts().isEmpty()) {
            return new SandboxExecutionResult(response.execution(), List.of(), response.reasonCode());
        }
        Path tempDirectory = sessionTempDirectories.computeIfAbsent(
                request.session().sessionId(),
                ignored -> createTempDirectory());
        List<SandboxArtifact> materialized = new ArrayList<>();
        long remainingBytes = maxArtifactBytes;
        try {
            for (SandboxRuntimeTransportProtocol.ArtifactDescriptor descriptor : response.artifacts()) {
                Path target = Files.createTempFile(tempDirectory, "artifact-", ".bin");
                long copied = downloadArtifact(endpoint, descriptor, target, remainingBytes);
                remainingBytes -= copied;
                materialized.add(descriptor.materialize(target.toUri().toString()));
            }
            return new SandboxExecutionResult(response.execution(), materialized, response.reasonCode());
        } catch (RuntimeException | IOException ex) {
            deleteRecursively(tempDirectory);
            sessionTempDirectories.remove(request.session().sessionId(), tempDirectory);
            throw new IllegalStateException("Sandbox remote artifact transfer failed", ex);
        }
    }

    @Override
    public SandboxSession closeSession(SandboxRuntimeNodeEndpoint endpoint, SandboxSession session) {
        try {
            return invokeJson(endpoint, SeahorseSandboxRuntimeTransportController.CLOSE_PATH, session, SandboxSession.class);
        } finally {
            Path tempDirectory = sessionTempDirectories.remove(session.sessionId());
            deleteRecursively(tempDirectory);
        }
    }

    private long downloadArtifact(SandboxRuntimeNodeEndpoint endpoint,
                                  SandboxRuntimeTransportProtocol.ArtifactDescriptor descriptor,
                                  Path target,
                                  long remainingBytes) throws IOException {
        SandboxRuntimeTransportProtocol.ArtifactRequest request = new SandboxRuntimeTransportProtocol.ArtifactRequest(
                descriptor.sessionId(), descriptor.executionId(), descriptor.artifactId());
        String body = writeJson(request);
        URI uri = resolve(endpoint, SeahorseSandboxRuntimeTransportController.ARTIFACT_PATH);
        HttpRequest httpRequest = signedRequest(endpoint.nodeId(), uri, body);
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sandbox remote artifact transfer interrupted", ex);
        }
        requireSuccessful(response.statusCode());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength < 0L || contentLength > remainingBytes) {
            response.body().close();
            throw new IllegalStateException("Sandbox remote artifact exceeds the transfer budget");
        }
        try (InputStream input = response.body()) {
            return copyBounded(input, target, remainingBytes);
        }
    }

    private <T> T invokeJson(SandboxRuntimeNodeEndpoint endpoint, String path, Object payload, Class<T> type) {
        String body = writeJson(payload);
        URI uri = resolve(endpoint, path);
        try {
            HttpResponse<String> response = httpClient.send(
                    signedRequest(endpoint.nodeId(), uri, body),
                    HttpResponse.BodyHandlers.ofString());
            requireSuccessful(response.statusCode());
            return objectMapper.readValue(response.body(), type);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sandbox remote runtime request interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Sandbox remote runtime request failed", ex);
        }
    }

    private HttpRequest signedRequest(String nodeId, URI uri, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        signer.sign(nodeId, "POST", uri.getPath(), body).forEach(builder::header);
        return builder.build();
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Sandbox remote runtime request serialization failed", ex);
        }
    }

    private static URI resolve(SandboxRuntimeNodeEndpoint endpoint, String path) {
        String base = endpoint.transportUri().toString();
        String suffix = path.startsWith(SeahorseSandboxRuntimeTransportController.BASE_PATH)
                ? path.substring(SeahorseSandboxRuntimeTransportController.BASE_PATH.length())
                : path;
        return URI.create(base + suffix);
    }

    private static long copyBounded(InputStream input, Path target, long remainingBytes) throws IOException {
        long copied = 0L;
        byte[] buffer = new byte[BUFFER_BYTES];
        try (var output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                copied += read;
                if (copied > remainingBytes) {
                    throw new IllegalStateException("Sandbox remote artifact exceeds the transfer budget");
                }
                output.write(buffer, 0, read);
            }
        }
        return copied;
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("seahorse-remote-sandbox-");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create sandbox remote artifact directory", ex);
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void requireSuccessful(int statusCode) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Sandbox remote runtime returned HTTP " + statusCode);
        }
    }

    private static Duration positiveDuration(Duration value, Duration fallback, String label) {
        Duration duration = Objects.requireNonNullElse(value, fallback);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return duration;
    }

    private static long requireMaxArtifactBytes(long value) {
        if (value <= 0L || value > 256L * 1024L * 1024L) {
            throw new IllegalArgumentException("maxArtifactBytes must be between 1 byte and 256 MiB");
        }
        return value;
    }
}
