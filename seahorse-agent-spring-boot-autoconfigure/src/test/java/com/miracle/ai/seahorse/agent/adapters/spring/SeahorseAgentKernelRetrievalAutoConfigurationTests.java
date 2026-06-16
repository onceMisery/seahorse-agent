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

import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeatureProperties;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentKernelRetrievalAutoConfigurationTests {

    private final RecordingEmbeddingModelChannel channel = new RecordingEmbeddingModelChannel();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelRetrievalAutoConfiguration.class))
            .withBean(ExtensionRegistry.class, this::extensionRegistry)
            .withBean(FeatureActivationContext.class,
                    () -> new FeatureActivationContext("tenant-a", "user-a", Map.of(), AgentFeatureProperties.empty()));

    @Test
    void shouldInjectConfiguredEmbeddingModelIntoRetrievalOptions() {
        contextRunner.withPropertyValues("seahorse.agent.adapters.ai.embedding-model=nomic-embed-text")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    context.getBean(KernelRetrievalEngine.class)
                            .retrieveKnowledgeChannels(List.of(new SubQuestionIntent("question", List.of())), 3);

                    assertThat(channel.embeddingModel).isEqualTo("nomic-embed-text");
                });
    }

    @Test
    void shouldPassKnowledgeBaseScopeIntoMemoryBusinessDocumentRetrievalFilter() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            context.getBean(MemoryBusinessDocumentRetrieverPort.class)
                    .retrieve("tenant-a", "policy", 3, List.of("42"));

            assertThat(channel.lastFilter).isNotNull();
            assertThat(channel.lastFilter.system().tenantId()).isEqualTo("tenant-a");
            assertThat(channel.lastFilter.system().knowledgeBaseIds()).containsExactly("42");
        });
    }

    private ExtensionRegistry extensionRegistry() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(new ExtensionDescriptor(channel.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, 1, true), channel);
        return registry;
    }

    private static final class RecordingEmbeddingModelChannel implements SearchChannelFeature {

        private String embeddingModel = "";
        private RetrievalFilter lastFilter;

        @Override
        public String name() {
            return "recording-embedding-model";
        }

        @Override
        public SearchChannelType channelType() {
            return SearchChannelType.VECTOR_GLOBAL;
        }

        @Override
        public boolean enabled(SearchContext context) {
            return true;
        }

        @Override
        public SearchChannelResult search(SearchContext context) {
            embeddingModel = context.effectiveOptions().embeddingModel();
            lastFilter = context.getFilter();
            return SearchChannelResult.builder()
                    .channelType(channelType())
                    .channelName(name())
                    .chunks(List.of(RetrievedChunk.builder()
                            .id("spring-context-chunk")
                            .text("spring context")
                            .score(0.8F)
                            .build()))
                    .latencyMs(1L)
                    .build();
        }
    }
}
