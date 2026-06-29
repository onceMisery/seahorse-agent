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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnExpression("${seahorse.agent.memory.compaction.llm-summarizer-enabled:false}"
        + " || ${seahorse-agent.memory.compaction.llm-summarizer-enabled:false}")
public class OpenAiCompatibleMemoryCompactionAutoConfiguration {

    @Bean
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean(MemoryCompactionSummarizerPort.class)
    public MemoryCompactionSummarizerPort seahorseOpenAiCompatibleMemoryCompactionSummarizer(
            ChatModelPort chatModelPort,
            ObjectProvider<PromptTemplatePort> promptTemplatePort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            @Value("${seahorse-agent.memory.compaction.llm-summarizer-model:}") String modelId) {
        return new LlmMemoryCompactionSummarizerAdapter(
                chatModelPort,
                promptTemplatePort.getIfAvailable(PromptTemplatePort::empty),
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                modelId);
    }
}
