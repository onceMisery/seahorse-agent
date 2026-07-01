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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEAHORSE_SANDBOX_CONTAINER_E2E", matches = "true")
class ContainerSandboxRuntimeAdapterDockerSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecutePythonInRealContainer() {
        ContainerSandboxAdapterProperties properties = new ContainerSandboxAdapterProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.setEngine(System.getenv().getOrDefault("SEAHORSE_SANDBOX_CONTAINER_ENGINE", "docker"));
        properties.setPythonImage(System.getenv().getOrDefault(
                "SEAHORSE_SANDBOX_CONTAINER_PYTHON_IMAGE",
                "python:3.11-alpine"));
        properties.setExecutionTimeout(Duration.ofSeconds(120));
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(
                properties,
                new ProcessBuilderContainerCommandRunner(),
                Clock.systemUTC());
        SandboxSession session = adapter.createSession(new SandboxSessionRequest(
                "default",
                "docker-smoke",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('seahorse sandbox docker smoke')",
                false,
                List.of()));
        SandboxSession closed = adapter.closeSession(session);

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("seahorse sandbox docker smoke");
        assertThat(closed.status()).isEqualTo(SandboxExecutionStatus.CANCELLED);
        assertThat(tempDir.resolve(session.sessionId())).doesNotExist();
    }
}
