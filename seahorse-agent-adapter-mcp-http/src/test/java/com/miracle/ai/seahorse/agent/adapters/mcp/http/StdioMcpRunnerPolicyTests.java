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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StdioMcpRunnerPolicyTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldDropParentEnvironmentExceptAllowlistedKeysAndKeepConfiguredEnv() {
        McpHttpAdapterProperties.StdioRunnerIsolation properties =
                new McpHttpAdapterProperties.StdioRunnerIsolation();
        properties.setEnvironmentAllowlist(List.of("PATH", "LANG"));
        StdioMcpRunnerPolicy policy = StdioMcpRunnerPolicy.from(properties);

        Map<String, String> environment = policy.isolatedEnvironment(
                Map.of(
                        "PATH", "/usr/bin",
                        "LANG", "C.UTF-8",
                        "AWS_SECRET_ACCESS_KEY", "parent-secret",
                        "MCP_STDIO_PARENT_ONLY_MARKER", "parent-only"),
                Map.of("MCP_STDIO_E2E_SECRET", "configured-secret"));

        assertThat(environment)
                .containsEntry("PATH", "/usr/bin")
                .containsEntry("LANG", "C.UTF-8")
                .containsEntry("MCP_STDIO_E2E_SECRET", "configured-secret")
                .doesNotContainKeys("AWS_SECRET_ACCESS_KEY", "MCP_STDIO_PARENT_ONLY_MARKER");
    }

    @Test
    void shouldDenyWorkingDirWhenNoAllowlistIsConfigured() throws Exception {
        Path workingDir = Files.createDirectories(tempDir.resolve("server"));
        StdioMcpRunnerPolicy policy = StdioMcpRunnerPolicy.defaultPolicy();

        assertThat(policy.validateWorkingDir(workingDir.toString()))
                .hasValueSatisfying(reason -> assertThat(reason).contains("stdio workingDir not allowlisted"));
    }

    @Test
    void shouldAllowWorkingDirUnderConfiguredRoot() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("allowed"));
        Path workingDir = Files.createDirectories(root.resolve("server"));
        McpHttpAdapterProperties.StdioRunnerIsolation properties =
                new McpHttpAdapterProperties.StdioRunnerIsolation();
        properties.setWorkingDirAllowlist(List.of(root.toString()));
        StdioMcpRunnerPolicy policy = StdioMcpRunnerPolicy.from(properties);

        assertThat(policy.validateWorkingDir(workingDir.toString())).isEmpty();
        assertThat(policy.workingDirectory(workingDir.toString()).toPath())
                .isEqualTo(workingDir.toAbsolutePath().normalize());
    }

    @Test
    void shouldBypassIsolationWhenDisabled() throws Exception {
        Path workingDir = Files.createDirectories(tempDir.resolve("server"));
        McpHttpAdapterProperties.StdioRunnerIsolation properties =
                new McpHttpAdapterProperties.StdioRunnerIsolation();
        properties.setEnabled(false);
        StdioMcpRunnerPolicy policy = StdioMcpRunnerPolicy.from(properties);

        assertThat(policy.validateWorkingDir(workingDir.toString())).isEmpty();
        assertThat(policy.isolatedEnvironment(
                Map.of("AWS_SECRET_ACCESS_KEY", "parent-secret"),
                Map.of()))
                .containsEntry("AWS_SECRET_ACCESS_KEY", "parent-secret");
    }
}
