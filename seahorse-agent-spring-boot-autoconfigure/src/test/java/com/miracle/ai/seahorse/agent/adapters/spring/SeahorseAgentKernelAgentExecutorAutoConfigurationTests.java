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

import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentLoopDependencies;
import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentStreamEmitter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.MarkdownNormalizer;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ToolCallParser;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentKernelAgentExecutorAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelAgentAutoConfiguration.class))
            .withUserConfiguration(StreamingModelConfiguration.class)
            .withPropertyValues("seahorse-agent.chat.agent-mode-enabled=true");

    @Test
    void createsKernelExecutorAsDefaultReActPortWithCollaborators() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MarkdownNormalizer.class);
            assertThat(context).hasSingleBean(AgentStreamEmitter.class);
            assertThat(context).hasSingleBean(ToolCallParser.class);
            assertThat(context).hasSingleBean(AgentLoopDependencies.class);
            assertThat(context).hasSingleBean(KernelAgentLoop.class);
            assertThat(context).hasSingleBean(ReActExecutorPort.class);
            assertThat(context.getBean(ReActExecutorPort.class)).isSameAs(context.getBean(KernelAgentLoop.class));
            assertThat(context.getBean(ReActExecutorPort.class).engineId()).isEqualTo("kernel");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class StreamingModelConfiguration {

        @Bean
        StreamingChatModelPort streamingChatModelPort() {
            return StreamingChatModelPort.noop();
        }
    }
}
