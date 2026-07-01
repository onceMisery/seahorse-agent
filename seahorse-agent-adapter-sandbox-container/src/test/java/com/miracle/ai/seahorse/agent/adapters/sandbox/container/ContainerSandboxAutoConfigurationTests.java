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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerSandboxAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContainerSandboxAutoConfiguration.class));

    @Test
    void shouldStayDisabledUnlessSandboxRuntimeIsContainer() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ContainerSandboxRuntimeAdapter.class);
            assertThat(context).doesNotHaveBean(SandboxRuntimePort.class);
        });
    }

    @Test
    void shouldCreateContainerSandboxRuntimeWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.adapters.sandbox.runtime=container",
                        "seahorse-agent.adapters.sandbox.container.workspace-root="
                                + System.getProperty("java.io.tmpdir"),
                        "seahorse-agent.adapters.sandbox.container.engine=podman")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ContainerCommandRunner.class);
                    assertThat(context).hasSingleBean(ContainerSandboxRuntimeAdapter.class);
                    assertThat(context).hasSingleBean(SandboxRuntimePort.class);
                    assertThat(context.getBean(ContainerSandboxAdapterProperties.class).getEngine())
                            .isEqualTo("podman");
                });
    }

    @Test
    void shouldNotReplaceCustomSandboxRuntime() {
        contextRunner
                .withUserConfiguration(CustomRuntimeConfiguration.class)
                .withPropertyValues("seahorse-agent.adapters.sandbox.runtime=container")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SandboxRuntimePort.class))
                            .isSameAs(context.getBean("customSandboxRuntimePort"));
                    assertThat(context).doesNotHaveBean(ContainerSandboxRuntimeAdapter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRuntimeConfiguration {

        @Bean
        SandboxRuntimePort customSandboxRuntimePort() {
            return SandboxRuntimePort.unsupported();
        }
    }
}
