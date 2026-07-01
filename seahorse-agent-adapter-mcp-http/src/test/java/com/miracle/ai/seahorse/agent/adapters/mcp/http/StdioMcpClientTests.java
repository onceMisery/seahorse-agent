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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class StdioMcpClientTests {

    private static final String RAW_SECRET = "sk-live-secret-123456";

    @Test
    void shouldInitializeListToolsAndCallToolOverStdio() {
        try (StdioMcpClient client = new StdioMcpClient(
                new ObjectMapper(),
                "fake",
                javaCommand(),
                List.of("-cp", testClasspath(), FakeStdioMcpServer.class.getName()),
                Map.of("FAKE_MCP_PREFIX", "seen"),
                "",
                Duration.ofSeconds(20))) {

            Assertions.assertTrue(client.initialize());

            List<McpToolDescriptor> tools = client.listTools();
            Assertions.assertEquals(1, tools.size());
            Assertions.assertEquals("echo", tools.getFirst().toolId());
            Assertions.assertTrue(tools.getFirst().parameters().containsKey("text"));

            McpToolExecutionResult result = client.call(new McpToolExecutionRequest(
                    "echo",
                    Map.of("text", "world")));

            Assertions.assertTrue(result.success());
            Assertions.assertEquals("seen world", result.content());
        }
    }

    @Test
    void shouldIncludeStderrWhenStdioServerFailsToInitialize() {
        try (StdioMcpClient client = new StdioMcpClient(
                new ObjectMapper(),
                "fake-failing",
                javaCommand(),
                List.of("-cp", testClasspath(), FakeFailingStdioMcpServer.class.getName()),
                Map.of(),
                "",
                Duration.ofSeconds(5))) {

            McpToolExecutionResult result = client.call(new McpToolExecutionRequest("echo", Map.of()));

            Assertions.assertFalse(result.success());
            Assertions.assertTrue(result.message().contains("boom from stderr"), result.message());
        }
    }

    @Test
    void shouldRedactSensitiveStderrDiagnostics() {
        try (StdioMcpClient client = new StdioMcpClient(
                new ObjectMapper(),
                "fake-failing",
                javaCommand(),
                List.of("-cp", testClasspath(), FakeFailingStdioMcpServer.class.getName()),
                Map.of("FAKE_MCP_SECRET", RAW_SECRET),
                "",
                Duration.ofSeconds(5))) {

            McpToolExecutionResult result = client.call(new McpToolExecutionRequest("echo", Map.of()));

            Assertions.assertFalse(result.success());
            Assertions.assertFalse(result.message().contains(RAW_SECRET), result.message());
            Assertions.assertTrue(result.message().contains(McpDiagnosticRedactor.REDACTED), result.message());
            Assertions.assertFalse(client.stderrTail().contains(RAW_SECRET), client.stderrTail());
            Assertions.assertTrue(client.stderrTail().contains(McpDiagnosticRedactor.REDACTED), client.stderrTail());
        }
    }

    @Test
    void shouldRedactSensitiveToolOutput() {
        try (StdioMcpClient client = new StdioMcpClient(
                new ObjectMapper(),
                "fake",
                javaCommand(),
                List.of("-cp", testClasspath(), FakeStdioMcpServer.class.getName()),
                Map.of("FAKE_MCP_PREFIX", "token=" + RAW_SECRET),
                "",
                Duration.ofSeconds(20))) {

            McpToolExecutionResult result = client.call(new McpToolExecutionRequest(
                    "echo",
                    Map.of("text", "world")));

            Assertions.assertTrue(result.success());
            Assertions.assertFalse(result.content().contains(RAW_SECRET), result.content());
            Assertions.assertTrue(result.content().contains("token=" + McpDiagnosticRedactor.REDACTED),
                    result.content());
        }
    }

    @Test
    void shouldRedactCommonSensitiveDiagnosticShapes() {
        Assertions.assertEquals(
                "Authorization: " + McpDiagnosticRedactor.REDACTED,
                McpDiagnosticRedactor.redact("Authorization: Bearer plain-secret-123456"));
        Assertions.assertEquals(
                "\"access_token\":\"" + McpDiagnosticRedactor.REDACTED + "\"",
                McpDiagnosticRedactor.redact("\"access_token\":\"plain-secret-123456\""));
        Assertions.assertEquals(
                "FAKE_MCP_SECRET=" + McpDiagnosticRedactor.REDACTED,
                McpDiagnosticRedactor.redact("FAKE_MCP_SECRET=plain-secret-123456"));
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String testClasspath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }
}
