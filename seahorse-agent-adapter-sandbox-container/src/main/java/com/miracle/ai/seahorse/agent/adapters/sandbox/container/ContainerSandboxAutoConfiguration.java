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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@AutoConfiguration
@AutoConfigureBefore(name = "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelRegistryAutoConfiguration")
@EnableConfigurationProperties(ContainerSandboxAdapterProperties.class)
@ConditionalOnProperty(prefix = "seahorse-agent.adapters.sandbox", name = "runtime", havingValue = "container")
public class ContainerSandboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ContainerCommandRunner seahorseContainerCommandRunner() {
        return new ProcessBuilderContainerCommandRunner();
    }

    @Bean
    @ConditionalOnMissingBean(SandboxRuntimePort.class)
    public ContainerSandboxRuntimeAdapter seahorseContainerSandboxRuntimePort(
            ContainerSandboxAdapterProperties properties,
            ContainerCommandRunner commandRunner) {
        return new ContainerSandboxRuntimeAdapter(properties, commandRunner, Clock.systemUTC());
    }
}
