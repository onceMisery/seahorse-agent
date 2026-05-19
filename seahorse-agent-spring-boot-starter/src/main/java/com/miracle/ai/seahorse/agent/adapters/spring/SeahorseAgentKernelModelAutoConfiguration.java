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

import com.miracle.ai.seahorse.agent.kernel.application.model.KernelModelRoutingService;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelRoutingStatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型路由内核自动配置。
 *
 * <p>模型路由聚合 chat、streaming、embedding、rerank 和健康状态端口，独立配置后新增模型 provider 不需要触碰主 kernel 配置。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(SeahorseAgentKernelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KernelModelRoutingService seahorseKernelModelRoutingService(
            ObjectProvider<ChatModelPort> chatModelPort,
            ObjectProvider<StreamingChatModelPort> streamingChatModelPort,
            ObjectProvider<ModelProviderPort> modelProviderPort,
            ObjectProvider<EmbeddingModelPort> embeddingModelPort,
            ObjectProvider<RerankModelPort> rerankModelPort,
            ObjectProvider<TokenCounterPort> tokenCounterPort,
            ObjectProvider<ModelHealthPort> modelHealthPort,
            ObjectProvider<ModelRoutingStatePort> routingStatePort) {
        return new KernelModelRoutingService(
                chatModelPort.getIfAvailable(ChatModelPort::noop),
                streamingChatModelPort.getIfAvailable(StreamingChatModelPort::noop),
                modelProviderPort.getIfAvailable(ModelProviderPort::noop),
                embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop),
                rerankModelPort.getIfAvailable(RerankModelPort::noop),
                tokenCounterPort.getIfAvailable(TokenCounterPort::approximate),
                modelHealthPort.getIfAvailable(ModelHealthPort::noop),
                routingStatePort.getIfAvailable(ModelRoutingStatePort::firstAvailable));
    }
}
