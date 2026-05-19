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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelAdapter;
import com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelProperties;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * AI 模型适配器自动配置。
 *
 * <p>OpenAI-compatible 适配器同时暴露 chat、streaming、embedding、rerank 等端口，集中在本配置中便于后续新增模型 provider。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass(name = "com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelAdapter")
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentAiAdapterAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.ai", name = "type", havingValue = "openai-compatible")
    @ConditionalOnMissingBean(name = "openAiStreamingExecutor")
    public ThreadPoolTaskExecutor openAiStreamingExecutor(
            @Value("${seahorse-agent.adapters.ai.streaming-executor.core-size:4}") int coreSize,
            @Value("${seahorse-agent.adapters.ai.streaming-executor.max-size:32}") int maxSize,
            @Value("${seahorse-agent.adapters.ai.streaming-executor.queue-capacity:200}") int queueCapacity,
            @Value("${seahorse-agent.adapters.ai.streaming-executor.thread-name-prefix:seahorse-openai-stream-}")
            String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, coreSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, coreSize), maxSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnBean(OkHttpClient.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.ai", name = "type", havingValue = "openai-compatible")
    @ConditionalOnMissingBean(OpenAiCompatibleModelAdapter.class)
    public OpenAiCompatibleModelAdapter seahorseOpenAiCompatibleModelAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Qualifier("openAiStreamingExecutor") Executor streamingExecutor,
            @Value("${seahorse-agent.adapters.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${seahorse-agent.adapters.ai.api-key:}") String apiKey,
            @Value("${seahorse-agent.adapters.ai.chat-model:}") String chatModel,
            @Value("${seahorse-agent.adapters.ai.embedding-model:}") String embeddingModel,
            @Value("${seahorse-agent.adapters.ai.rerank-model:}") String rerankModel) {
        OpenAiCompatibleModelProperties properties = new OpenAiCompatibleModelProperties(
                baseUrl, apiKey, chatModel, embeddingModel, rerankModel, List.of());
        return new OpenAiCompatibleModelAdapter(httpClient, objectMapper, properties, streamingExecutor);
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(ChatModelPort.class)
    public ChatModelPort seahorseNativeChatModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(StreamingChatModelPort.class)
    public StreamingChatModelPort seahorseNativeStreamingChatModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(EmbeddingModelPort.class)
    public EmbeddingModelPort seahorseNativeEmbeddingModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(RerankModelPort.class)
    public RerankModelPort seahorseNativeRerankModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(ModelProviderPort.class)
    public ModelProviderPort seahorseNativeModelProviderPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(TokenCounterPort.class)
    public TokenCounterPort seahorseNativeTokenCounterPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(ModelHealthPort.class)
    public ModelHealthPort seahorseNativeModelHealthPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }
}
