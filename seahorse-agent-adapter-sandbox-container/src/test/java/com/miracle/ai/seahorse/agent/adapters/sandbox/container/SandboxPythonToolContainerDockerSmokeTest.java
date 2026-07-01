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

package com.miracle.ai.seahorse.agent.adapters.sandbox.container;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.DefaultSandboxPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.KernelSandboxRuntimeService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.AgentToolJsonSupport;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SandboxPythonToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEAHORSE_SANDBOX_CONTAINER_E2E", matches = "true")
class SandboxPythonToolContainerDockerSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldInvokeSandboxPythonToolThroughRealContainerRuntime() {
        ContainerSandboxAdapterProperties properties = new ContainerSandboxAdapterProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.setEngine(System.getenv().getOrDefault("SEAHORSE_SANDBOX_CONTAINER_ENGINE", "docker"));
        properties.setPythonImage(System.getenv().getOrDefault(
                "SEAHORSE_SANDBOX_CONTAINER_PYTHON_IMAGE",
                "python:3.11-alpine"));
        properties.setExecutionTimeout(Duration.ofSeconds(120));
        ContainerSandboxRuntimeAdapter runtimeAdapter = new ContainerSandboxRuntimeAdapter(
                properties,
                new ProcessBuilderContainerCommandRunner(),
                Clock.systemUTC());
        KernelSandboxRuntimeService sandboxRuntime = new KernelSandboxRuntimeService(
                new DefaultSandboxPolicyPort(SandboxNetworkPolicy.DENY_ALL, List.of()),
                runtimeAdapter,
                artifact -> artifact,
                Clock.systemUTC());
        SandboxPythonToolPortAdapter tool = new SandboxPythonToolPortAdapter(
                sandboxRuntime,
                new AgentToolJsonSupport(new ObjectMapper()));

        ToolInvocationResult result = tool.invoke(new ToolInvocationRequest(
                "run-docker-smoke",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                SandboxPythonToolPortAdapter.TOOL_ID,
                Map.of("code", "print('sandbox tool smoke')"),
                Map.of(),
                "run-docker-smoke:call-1",
                List.of(SandboxPythonToolPortAdapter.TOOL_ID)));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("SUCCEEDED", "sandbox tool smoke");
    }
}
