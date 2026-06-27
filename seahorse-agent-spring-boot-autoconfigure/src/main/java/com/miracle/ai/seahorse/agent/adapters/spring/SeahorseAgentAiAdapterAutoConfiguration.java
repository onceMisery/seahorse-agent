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
import com.miracle.ai.seahorse.agent.kernel.config.EmbeddingModelDimensions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 模型适配器自动配置。
 *
 * <p>OpenAI-compatible 适配器同时暴露 chat、streaming、embedding、rerank 等端口，集中在本配置中便于后续新增模型 provider。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass(name = "com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelAdapter")
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentAiAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OkHttpClient.class)
    public OkHttpClient seahorseOkHttpClient(
            @Value("${seahorse.agent.adapters.http.connect-timeout:10s}") String connectTimeout,
            @Value("${seahorse.agent.adapters.http.read-timeout:60s}") String readTimeout,
            @Value("${seahorse.agent.adapters.http.write-timeout:60s}") String writeTimeout,
            @Value("${seahorse.agent.adapters.http.call-timeout:120s}") String callTimeout,
            @Value("${seahorse.agent.adapters.http.protocols:}") String protocols) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(parseDuration(connectTimeout, Duration.ofSeconds(10)))
                .readTimeout(parseDuration(readTimeout, Duration.ofSeconds(60)))
                .writeTimeout(parseDuration(writeTimeout, Duration.ofSeconds(60)))
                .callTimeout(parseDuration(callTimeout, Duration.ofSeconds(120)));
        List<Protocol> configuredProtocols = parseProtocols(protocols);
        if (!configuredProtocols.isEmpty()) {
            builder.protocols(configuredProtocols);
        }
        HttpProxySupport.proxySelectorFromEnvironment().ifPresent(builder::proxySelector);
        return builder.build();
    }

    private static Duration parseDuration(String value, Duration fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        return DurationStyle.detectAndParse(normalized);
    }

    private static List<Protocol> parseProtocols(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<Protocol> protocols = new ArrayList<>();
        for (String token : normalized.split(",")) {
            String protocol = token.trim().toLowerCase(Locale.ROOT);
            if (protocol.isBlank()) {
                continue;
            }
            protocols.add(switch (protocol) {
                case "http/1.1", "http1", "http_1_1" -> Protocol.HTTP_1_1;
                case "h2", "http/2", "http2" -> Protocol.HTTP_2;
                default -> throw new IllegalArgumentException("Unsupported OkHttp protocol: " + token.trim());
            });
        }
        return List.copyOf(protocols);
    }

    @Bean
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.ai", name = "type",
            havingValue = "openai-compatible")
    @ConditionalOnMissingBean(name = "openAiStreamingExecutor")
    public ThreadPoolTaskExecutor openAiStreamingExecutor(
            @Value("${seahorse.agent.adapters.ai.streaming-executor.core-size:4}") int coreSize,
            @Value("${seahorse.agent.adapters.ai.streaming-executor.max-size:32}") int maxSize,
            @Value("${seahorse.agent.adapters.ai.streaming-executor.queue-capacity:200}") int queueCapacity,
            @Value("${seahorse.agent.adapters.ai.streaming-executor.thread-name-prefix:seahorse-openai-stream-}")
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
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.ai", name = "type",
            havingValue = "openai-compatible")
    @ConditionalOnMissingBean(OpenAiCompatibleModelAdapter.class)
    public OpenAiCompatibleModelAdapter seahorseOpenAiCompatibleModelAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Qualifier("openAiStreamingExecutor") Executor streamingExecutor,
            @Value("${seahorse.agent.adapters.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${seahorse.agent.adapters.ai.api-key:}") String apiKey,
            @Value("${seahorse.agent.adapters.ai.chat-model:}") String chatModel,
            @Value("${seahorse.agent.adapters.ai.embedding-model:}") String embeddingModel,
            @Value("${seahorse.agent.adapters.ai.rerank-model:}") String rerankModel,
            @Value("${seahorse.agent.adapters.ai.image-model:}") String imageModel) {
        OpenAiCompatibleModelProperties properties = new OpenAiCompatibleModelProperties(
                baseUrl, apiKey, chatModel, embeddingModel, rerankModel, imageModel, List.of());
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

    /**
     * 独立 Embedding provider。
     *
     * <p>当 chat 模型供应商（如纯对话网关）不提供 {@code /embeddings} 端点时，可为向量化单独配置一个
     * OpenAI-compatible 端点（如本地 Ollama: {@code http://ollama:11434/v1}）。配置
     * {@code seahorse.agent.adapters.ai.embedding.base-url} 即启用，并以 {@link Primary} 覆盖
     * 复用 chat 端点的 {@link #seahorseNativeEmbeddingModelPort} 与 mock 实现。
     */
    @Bean
    @Primary
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.ai", name = "type",
            havingValue = "openai-compatible")
    @ConditionalOnSeahorseAgentProperty(
            prefix = "seahorse-agent.adapters.ai",
            name = "embedding-type",
            havingValue = "openai-compatible",
            matchIfMissing = true)
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.ai.embedding", name = "base-url")
    @ConditionalOnMissingBean(name = "seahorseDedicatedEmbeddingModelPort")
    public EmbeddingModelPort seahorseDedicatedEmbeddingModelPort(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${seahorse.agent.adapters.ai.embedding.base-url}") String embeddingBaseUrl,
            @Value("${seahorse.agent.adapters.ai.embedding.api-key:}") String embeddingApiKey,
            @Value("${seahorse.agent.adapters.ai.embedding-model:}") String embeddingModel) {
        OpenAiCompatibleModelProperties properties = new OpenAiCompatibleModelProperties(
                embeddingBaseUrl, embeddingApiKey, "", embeddingModel, "", "", List.of());
        // embedding 调用为同步请求，executor 仅用于流式对话，这里用调用线程执行即可。
        OpenAiCompatibleModelAdapter delegate =
                new OpenAiCompatibleModelAdapter(httpClient, objectMapper, properties, Runnable::run);
        // 仅暴露 EmbeddingModelPort：OpenAiCompatibleModelAdapter 同时实现 ChatModelPort 等多个端口，
        // 若直接以 @Primary 暴露该实例，会让它在 ChatModelPort 候选中也成为 primary，与 chat adapter 冲突。
        // 用纯委托包装隔离，确保 @Primary 只作用于 embedding 端口。
        return (modelId, text) -> delegate.embed(modelId, text);
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(EmbeddingModelPort.class)
    public EmbeddingModelPort seahorseNativeEmbeddingModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.ai", name = "embedding-type",
            havingValue = "mock")
    public EmbeddingModelPort seahorseMockEmbeddingModelPortForOpenAiCompatible(
            Environment environment) {
        return new com.miracle.ai.seahorse.agent.adapters.ai.openai.MockEmbeddingAdapter(
                resolveMockEmbeddingDimension(environment));
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

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(ImageGenerationPort.class)
    public ImageGenerationPort seahorseNativeImageGenerationPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    // Mock Embedding Adapter for E2E Testing
    @Bean
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.ai", name = "type", havingValue = "mock")
    @ConditionalOnMissingBean(EmbeddingModelPort.class)
    public EmbeddingModelPort seahorseMockEmbeddingModelPort(
            Environment environment) {
        return new com.miracle.ai.seahorse.agent.adapters.ai.openai.MockEmbeddingAdapter(
                resolveMockEmbeddingDimension(environment));
    }

    @Bean
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.ai", name = "type", havingValue = "mock")
    @ConditionalOnMissingBean(StreamingChatModelPort.class)
    public StreamingChatModelPort seahorseMockStreamingChatModelPort() {
        return new StreamingChatModelPort() {
            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
                completeMockStream(request, callback);
                return () -> {
                };
            }

            @Override
            public StreamCancellationHandle streamChatWithTools(
                    ChatRequest request,
                    StreamCallback callback,
                    ToolCallCollector toolCallCollector) {
                if (toolCallCollector != null) {
                    toolCallCollector.onToolCalls(List.of());
                }
                completeMockStream(request, callback);
                return () -> {
                };
            }
        };
    }

    private static void completeMockStream(ChatRequest request, StreamCallback callback) {
        if (callback == null) {
            return;
        }
        callback.onContent(mockStreamingResponse(request));
        callback.onComplete();
    }

    private static String mockStreamingResponse(ChatRequest request) {
        int toolCount = request == null || request.getTools() == null ? 0 : request.getTools().size();
        return "mock-streaming-chat skill=" + firstSkillName(request) + " tools=" + toolCount;
    }

    private static String firstSkillName(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return "none";
        }
        Pattern skillNamePattern = Pattern.compile("<skill\\s+name=\"([^\"]+)\"");
        for (ChatMessage message : request.getMessages()) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            Matcher matcher = skillNamePattern.matcher(message.getContent());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "none";
    }

    static int resolveMockEmbeddingDimension(Environment environment) {
        String mockDimension = bindString(environment, "seahorse.agent.adapters.ai.mock.embedding-dimension");
        String explicitDimension = mockDimension.isBlank()
                ? bindString(environment, "seahorse.agent.adapters.vector.dimension")
                : mockDimension;
        return EmbeddingModelDimensions.resolveOrThrow(
                explicitDimension,
                bindString(environment, "seahorse.agent.adapters.ai.embedding-model"),
                bindString(environment, "seahorse.agent.adapters.ai.embedding-model-dimensions"));
    }

    private static String bindString(Environment environment, String name) {
        String value = Binder.get(environment).bind(name, String.class).orElse("");
        if (!value.isBlank()) {
            return value;
        }
        return environment.getProperty(name.replace("seahorse.agent", "seahorse-agent"), "");
    }
}
