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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryFile;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositorySnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class GitHubRepositoryReaderToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "github_repository_reader";
    private static final int DEFAULT_MAX_FILES = 20;
    private static final int MAX_FILES = 40;
    private static final int DEFAULT_MAX_CHARS_PER_FILE = 8_000;
    private static final int MAX_CHARS_PER_FILE = 30_000;
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "GitHub Repository Reader",
            "Read important documentation and source files from a public GitHub repository.",
            """
                    {"type":"object","required":["repositoryUrl"],"properties":{"repositoryUrl":{"type":"string","minLength":1},"branch":{"type":"string"},"maxFiles":{"type":"integer","minimum":1,"maximum":40},"maxCharsPerFile":{"type":"integer","minimum":200,"maximum":30000}}}
                    """);

    private final GitHubRepositoryPort repositoryPort;
    private final AgentToolJsonSupport jsonSupport;

    public GitHubRepositoryReaderToolPortAdapter(GitHubRepositoryPort repositoryPort,
                                                 AgentToolJsonSupport jsonSupport) {
        this.repositoryPort = Objects.requireNonNullElseGet(repositoryPort, GitHubRepositoryPort::unsupported);
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String repositoryUrl = jsonSupport.string(arguments, "repositoryUrl");
            if (repositoryUrl.isBlank()) {
                return ToolInvocationResult.failed("repositoryUrl is required");
            }
            GitHubRepositorySnapshot snapshot = repositoryPort.read(new GitHubRepositoryRequest(
                    repositoryUrl,
                    jsonSupport.string(arguments, "branch"),
                    jsonSupport.boundedInt(arguments, "maxFiles", DEFAULT_MAX_FILES, 1, MAX_FILES),
                    jsonSupport.boundedInt(arguments, "maxCharsPerFile",
                            DEFAULT_MAX_CHARS_PER_FILE, 200, MAX_CHARS_PER_FILE)));
            return ToolInvocationResult.ok(jsonSupport.write(observation(snapshot)));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("github_repository_reader failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private Map<String, Object> observation(GitHubRepositorySnapshot snapshot) {
        GitHubRepositorySnapshot safeSnapshot = Objects.requireNonNull(snapshot,
                "GitHub repository snapshot must not be null");
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("owner", safeSnapshot.owner());
        observation.put("repository", safeSnapshot.repository());
        observation.put("defaultBranch", safeSnapshot.defaultBranch());
        observation.put("htmlUrl", safeSnapshot.htmlUrl());
        observation.put("description", safeSnapshot.description());
        observation.put("truncated", safeSnapshot.truncated());
        observation.put("fetchedAt", safeSnapshot.fetchedAt().toString());
        observation.put("files", safeSnapshot.files().stream().map(this::file).toList());
        return observation;
    }

    private Map<String, Object> file(GitHubRepositoryFile file) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", file.path());
        payload.put("contentText", file.contentText());
        payload.put("rawUrl", file.rawUrl());
        payload.put("truncated", file.truncated());
        return payload;
    }
}
