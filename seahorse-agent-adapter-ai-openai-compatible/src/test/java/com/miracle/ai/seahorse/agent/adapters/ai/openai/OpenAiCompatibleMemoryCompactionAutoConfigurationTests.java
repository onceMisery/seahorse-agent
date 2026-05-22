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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummarizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleMemoryCompactionAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PromptTemplatePort.class, PromptTemplatePort::empty)
            .withConfiguration(AutoConfigurations.of(OpenAiCompatibleMemoryCompactionAutoConfiguration.class));

    @Test
    void shouldStayDisabledByDefault() {
        contextRunner.withUserConfiguration(ChatModelConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(MemoryCompactionSummarizerPort.class));
    }

    @Test
    void shouldRegisterLlmCompactionSummarizerWhenExplicitlyEnabledAndChatModelExists() {
        contextRunner.withUserConfiguration(ChatModelConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.compaction.llm-summarizer-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemoryCompactionSummarizerPort.class);
                    assertThat(context.getBean(MemoryCompactionSummarizerPort.class))
                            .isInstanceOf(LlmMemoryCompactionSummarizerAdapter.class);
                });
    }

    @Test
    void shouldNotRegisterLlmCompactionSummarizerWithoutChatModel() {
        contextRunner.withPropertyValues("seahorse-agent.memory.compaction.llm-summarizer-enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(MemoryCompactionSummarizerPort.class));
    }

    @Test
    void shouldBackOffWhenMemoryCompactionSummarizerAlreadyExists() {
        contextRunner.withUserConfiguration(ChatModelConfiguration.class)
                .withBean(MemoryCompactionSummarizerPort.class, MemoryCompactionSummarizerPort::noop)
                .withPropertyValues("seahorse-agent.memory.compaction.llm-summarizer-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemoryCompactionSummarizerPort.class);
                    assertThat(context.getBean(MemoryCompactionSummarizerPort.class))
                            .isNotInstanceOf(LlmMemoryCompactionSummarizerAdapter.class);
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
