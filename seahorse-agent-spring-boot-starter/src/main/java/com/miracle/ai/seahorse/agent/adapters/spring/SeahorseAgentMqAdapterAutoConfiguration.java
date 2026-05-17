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
import com.miracle.ai.seahorse.agent.adapters.mq.direct.DirectMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.mq.pulsar.PulsarMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.mq.pulsar.PulsarMessageQueueProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.ReliableMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import org.apache.pulsar.client.api.PulsarClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息队列适配器自动配置。
 *
 * <p>仅迁移 direct/pulsar 基础 MQ Bean；keyword outbox 仍保留在 keyword 配置区域，避免跨领域耦合扩大。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentMqAdapterAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.mq", name = "type", havingValue = "direct")
    @ConditionalOnMissingBean(MessageQueuePort.class)
    public ReliableMessageQueueAdapter seahorseDirectMessageQueueAdapter(
            ObjectProvider<OutboxEventRepositoryPort> outboxRepositoryPort,
            ObjectProvider<ObjectMapper> objectMapper) {
        DirectMessageQueueAdapter delegate = new DirectMessageQueueAdapter();
        return new ReliableMessageQueueAdapter(delegate, delegate,
                outboxRepositoryPort::getIfAvailable, objectMapper::getIfAvailable);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.apache.pulsar.client.api.PulsarClient",
            "com.miracle.ai.seahorse.agent.adapters.mq.pulsar.PulsarMessageQueueAdapter"
    })
    static class PulsarMqAutoConfiguration {

        @Bean
        @ConditionalOnBean(PulsarClient.class)
        @ConditionalOnProperty(prefix = "seahorse-agent.adapters.mq", name = "type",
                havingValue = "pulsar", matchIfMissing = true)
        @ConditionalOnMissingBean(MessageQueuePort.class)
        public ReliableMessageQueueAdapter seahorsePulsarMessageQueueAdapter(
                PulsarClient pulsarClient,
                ObjectMapper objectMapper,
                ObjectProvider<OutboxEventRepositoryPort> outboxRepositoryPort) {
            PulsarMessageQueueAdapter delegate = new PulsarMessageQueueAdapter(
                    pulsarClient, objectMapper, new PulsarMessageQueueProperties());
            return new ReliableMessageQueueAdapter(
                    delegate, delegate, outboxRepositoryPort::getIfAvailable, () -> objectMapper);
        }
    }
}
