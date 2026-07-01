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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SandboxPythonToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxPythonToolAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelAgentAutoConfiguration.class))
            .withUserConfiguration(SandboxRuntimeConfiguration.class)
            .withPropertyValues("seahorse-agent.chat.agent-mode-enabled=true");

    @Test
    void shouldRegisterSandboxPythonToolWhenSandboxRuntimeExists() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SandboxPythonToolPortAdapter.class);
        });
    }

    @Test
    void shouldNotRegisterSandboxPythonToolWhenSandboxToolsAreDisabled() {
        contextRunner.withPropertyValues("seahorse-agent.chat.agent.tools.sandbox.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SandboxPythonToolPortAdapter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class SandboxRuntimeConfiguration {

        @Bean
        SandboxRuntimeInboundPort sandboxRuntimeInboundPort() {
            return new NoopSandboxRuntime();
        }
    }

    private static final class NoopSandboxRuntime implements SandboxRuntimeInboundPort {

        @Override
        public SandboxSession createSession(SandboxSessionCreateCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SandboxExecutionResult execute(SandboxExecutionCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SandboxSession close(String sessionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SandboxExecution> listExecutions(String sessionId) {
            return List.of();
        }

        @Override
        public List<SandboxArtifact> listArtifacts(String sessionId) {
            return List.of();
        }
    }
}
