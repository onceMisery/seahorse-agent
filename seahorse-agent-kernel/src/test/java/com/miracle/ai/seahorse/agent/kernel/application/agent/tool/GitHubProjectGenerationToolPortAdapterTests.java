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
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryFile;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositorySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubProjectGenerationToolPortAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(objectMapper);

    @Test
    void githubRepositoryReaderShouldReturnRepositoryEvidenceWithImportantFiles() throws Exception {
        AtomicReference<GitHubRepositoryRequest> captured = new AtomicReference<>();
        GitHubRepositoryReaderToolPortAdapter tool = new GitHubRepositoryReaderToolPortAdapter(request -> {
            captured.set(request);
            return new GitHubRepositorySnapshot(
                    "redis",
                    "redis",
                    "unstable",
                    "https://github.com/redis/redis",
                    "Redis is an in-memory database that persists on disk.",
                    List.of(
                            new GitHubRepositoryFile(
                                    "README.md",
                                    "Redis is an in-memory database that persists on disk.",
                                    "https://raw.githubusercontent.com/redis/redis/unstable/README.md",
                                    false),
                            new GitHubRepositoryFile(
                                    "src/server.c",
                                    "int main(int argc, char **argv) { initServer(); }",
                                    "https://raw.githubusercontent.com/redis/redis/unstable/src/server.c",
                                    false),
                            new GitHubRepositoryFile(
                                    "redis.conf",
                                    "port 6379",
                                    "https://raw.githubusercontent.com/redis/redis/unstable/redis.conf",
                                    false)),
                    false,
                    Instant.parse("2026-06-08T00:00:00Z"));
        }, jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", GitHubRepositoryReaderToolPortAdapter.TOOL_ID,
                Map.of("repositoryUrl", "https://github.com/redis/redis", "maxFiles", 50, "maxCharsPerFile", 2000));

        assertTrue(result.success());
        assertEquals("https://github.com/redis/redis", captured.get().repositoryUrl());
        assertEquals(40, captured.get().maxFiles());
        assertEquals(2000, captured.get().maxCharsPerFile());
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("redis", root.path("owner").asText());
        assertEquals("redis", root.path("repository").asText());
        assertEquals("unstable", root.path("defaultBranch").asText());
        assertEquals(3, root.path("files").size());
        assertEquals("README.md", root.path("files").get(0).path("path").asText());
        assertEquals("src/server.c", root.path("files").get(1).path("path").asText());
        assertTrue(root.path("files").get(1).path("contentText").asText().contains("initServer"));
        assertFalse(root.path("truncated").asBoolean());
    }

    @Test
    void imageGenerationShouldPassPromptAndReturnGeneratedImageReference() throws Exception {
        AtomicReference<ImageGenerationRequest> captured = new AtomicReference<>();
        ImageGenerationToolPortAdapter tool = new ImageGenerationToolPortAdapter(request -> {
            captured.set(request);
            return ImageGenerationResult.generated(
                    request.prompt(),
                    request.model(),
                    "https://cdn.example.com/generated/redis-architecture.png",
                    "",
                    "image/png");
        }, "agnes-image-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", ImageGenerationToolPortAdapter.TOOL_ID,
                Map.of(
                        "prompt", "Draw a Redis architecture explainer image",
                        "size", "1024x1024",
                        "style", "technical diagram"));

        assertTrue(result.success());
        assertEquals("Draw a Redis architecture explainer image", captured.get().prompt());
        assertEquals("agnes-image-2.0-flash", captured.get().model());
        assertEquals("1024x1024", captured.get().size());
        assertEquals("technical diagram", captured.get().style());
        assertEquals("b64_json", captured.get().responseFormat());
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("GENERATED", root.path("status").asText());
        assertEquals("https://cdn.example.com/generated/redis-architecture.png", root.path("imageUrl").asText());
        assertEquals("image/png", root.path("mimeType").asText());
    }

    @Test
    void imageGenerationShouldTreatDefaultModelAsConfiguredModel() {
        AtomicReference<ImageGenerationRequest> captured = new AtomicReference<>();
        ImageGenerationToolPortAdapter tool = new ImageGenerationToolPortAdapter(request -> {
            captured.set(request);
            return ImageGenerationResult.generated(
                    request.prompt(),
                    request.model(),
                    "https://cdn.example.com/generated/redis-architecture.png",
                    "",
                    "image/png");
        }, "agnes-image-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", ImageGenerationToolPortAdapter.TOOL_ID,
                Map.of(
                        "prompt", "Draw a Redis architecture explainer image",
                        "model", "default"));

        assertTrue(result.success());
        assertEquals("agnes-image-2.0-flash", captured.get().model());
    }
}
