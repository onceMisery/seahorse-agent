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

package com.miracle.ai.seahorse.agent.adapters.ai.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleMemoryRefinerAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PromptTemplatePort.class, PromptTemplatePort::empty)
            .withConfiguration(AutoConfigurations.of(OpenAiCompatibleMemoryRefinerAutoConfiguration.class));

    @Test
    void shouldStayDisabledByDefault() {
        contextRunner.withUserConfiguration(ChatModelConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(MemoryRefinerPort.class));
    }

    @Test
    void shouldRegisterLlmMemoryRefinerWhenExplicitlyEnabledAndChatModelExists() {
        contextRunner.withUserConfiguration(ChatModelConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.refiner.llm-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemoryRefinerPort.class);
                    assertThat(context.getBean(MemoryRefinerPort.class))
                            .isInstanceOf(LlmMemoryRefinerAdapter.class);
                });
    }

    @Test
    void shouldRegisterLlmMemoryRefinerWhenMemoryRefinerIsEnabledWithCanonicalPrefix() {
        contextRunner.withUserConfiguration(ChatModelConfiguration.class)
                .withPropertyValues("seahorse.agent.memory.refiner.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemoryRefinerPort.class);
                    assertThat(context.getBean(MemoryRefinerPort.class))
                            .isInstanceOf(LlmMemoryRefinerAdapter.class);
                });
    }

    @Test
    void shouldNotRegisterLlmMemoryRefinerWithoutChatModel() {
        contextRunner.withPropertyValues("seahorse-agent.memory.refiner.llm-enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(MemoryRefinerPort.class));
    }

    @Test
    void shouldBackOffWhenMemoryRefinerAlreadyExists() {
        contextRunner.withUserConfiguration(ChatModelConfiguration.class)
                .withBean(MemoryRefinerPort.class, MemoryRefinerPort::noop)
                .withPropertyValues("seahorse-agent.memory.refiner.llm-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemoryRefinerPort.class);
                    assertThat(context.getBean(MemoryRefinerPort.class))
                            .isNotInstanceOf(LlmMemoryRefinerAdapter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ChatModelConfiguration {

        @Bean
        ChatModelPort chatModelPort() {
            return ChatModelPort.noop();
        }
    }
}
