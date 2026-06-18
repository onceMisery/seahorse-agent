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

import com.miracle.ai.seahorse.agent.adapters.web.AdvancedFeatureGate;
import com.miracle.ai.seahorse.agent.adapters.web.ReadinessController;
import com.miracle.ai.seahorse.agent.kernel.application.readiness.KernelReadinessService;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort.ComponentStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统就绪诊断自动配置。
 * <p>
 * 注册 ReadinessProbePort（检查各适配器 Bean 可用性）、
 * KernelReadinessService（业务逻辑）和 ReadinessController（REST 端点）。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentVectorAdapterAutoConfiguration.class,
        SeahorseAgentKeywordAdapterAutoConfiguration.class,
        SeahorseAgentAiAdapterAutoConfiguration.class,
        SeahorseAgentCacheAdapterAutoConfiguration.class,
        SeahorseAgentMqAdapterAutoConfiguration.class,
        SeahorseAgentStorageAdapterAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentReadinessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReadinessProbePort.class)
    public ReadinessProbePort seahorseReadinessProbe(
            ObjectProvider<DataSource> dataSource,
            ObjectProvider<StreamingChatModelPort> chatModel,
            ObjectProvider<VectorSearchPort> vectorSearch,
            ObjectProvider<KeywordSearchPort> keywordSearch,
            ObjectProvider<KeyValueCachePort> cache,
            ObjectProvider<MessageQueuePort> mq,
            ObjectProvider<ObjectStoragePort> storage,
            @Value("${seahorse-agent.adapters.vector.type:noop}") String vectorType,
            @Value("${seahorse-agent.adapters.cache.type:local}") String cacheType,
            @Value("${seahorse-agent.adapters.mq.type:direct}") String mqType,
            @Value("${seahorse-agent.adapters.storage.type:local}") String storageType,
            @Value("${seahorse-agent.adapters.ai.type:unknown}") String aiType,
            @Value("${seahorse-agent.adapters.ai.embedding-type:unknown}") String embeddingType,
            Environment environment
    ) {
        return new ReadinessProbePort() {
            @Override
            public Map<String, ComponentStatus> probeComponents() {
                Map<String, ComponentStatus> map = new LinkedHashMap<>();

                map.put("database", dataSource.getIfAvailable() != null
                        ? ComponentStatus.available("postgresql")
                        : ComponentStatus.unavailable("DataSource not available"));

                map.put("chat-model", chatModel.getIfAvailable() != null
                        ? ComponentStatus.available(aiType)
                        : ComponentStatus.unavailable("StreamingChatModelPort not available"));

                // Embedding model availability — check if a separate embedding bean exists
                // In practice, the embedding model is often part of the AI adapter
                map.put("embedding-model", chatModel.getIfAvailable() != null
                        ? ComponentStatus.available(embeddingType)
                        : ComponentStatus.unavailable("Embedding model not available"));

                map.put("vector-store", vectorSearch.getIfAvailable() != null
                        ? ComponentStatus.available(vectorType)
                        : ComponentStatus.unavailable("VectorSearchPort not available"));

                map.put("keyword-search", keywordSearch.getIfAvailable() != null
                        ? ComponentStatus.available("lucene")
                        : ComponentStatus.unavailable("KeywordSearchPort not available"));

                map.put("cache", cache.getIfAvailable() != null
                        ? ComponentStatus.available(cacheType)
                        : ComponentStatus.unavailable("KeyValueCachePort not available"));

                map.put("mq", mq.getIfAvailable() != null
                        ? ComponentStatus.available(mqType)
                        : ComponentStatus.unavailable("MessageQueuePort not available"));

                map.put("storage", storage.getIfAvailable() != null
                        ? ComponentStatus.available(storageType)
                        : ComponentStatus.unavailable("ObjectStoragePort not available"));

                return map;
            }

            @Override
            public Map<String, String> adapterTypes() {
                Map<String, String> types = new LinkedHashMap<>();
                types.put("vector-store", vectorType);
                types.put("cache", cacheType);
                types.put("mq", mqType);
                types.put("storage", storageType);
                types.put("ai", aiType);
                types.put("embedding", embeddingType);
                return types;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessInboundPort.class)
    public ReadinessInboundPort seahorseReadinessService(
            ReadinessProbePort probePort,
            @Value("${seahorse-agent.product-mode:demo}") String productMode
    ) {
        return new KernelReadinessService(productMode, probePort);
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessController.class)
    public ReadinessController seahorseReadinessController(
            ReadinessInboundPort readinessPort,
            AdvancedFeatureGate featureGate
    ) {
        return new ReadinessController(readinessPort, featureGate);
    }
}
