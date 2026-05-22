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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "seahorse-agent.memory.refiner", name = "llm-enabled", havingValue = "true")
public class OpenAiCompatibleMemoryRefinerAutoConfiguration {

    @Bean
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean(MemoryRefinerPort.class)
    public MemoryRefinerPort seahorseOpenAiCompatibleMemoryRefiner(
            ChatModelPort chatModelPort,
            ObjectProvider<PromptTemplatePort> promptTemplatePort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<MemoryReviewFeedbackRepositoryPort> feedbackRepositoryPort,
            @Value("${seahorse-agent.memory.refiner.feedback-sample-limit:3}") int feedbackSampleLimit) {
        return new LlmMemoryRefinerAdapter(
                chatModelPort,
                promptTemplatePort.getIfAvailable(PromptTemplatePort::empty),
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                feedbackRepositoryPort.getIfAvailable(MemoryReviewFeedbackRepositoryPort::empty),
                feedbackSampleLimit);
    }
}
