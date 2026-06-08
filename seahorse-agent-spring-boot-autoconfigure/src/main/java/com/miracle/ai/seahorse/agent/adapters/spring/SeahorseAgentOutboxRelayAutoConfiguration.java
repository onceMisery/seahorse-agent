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
import com.miracle.ai.seahorse.agent.adapters.spring.mq.SeahorseOutboxRelayJob;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox relay 自动配置。
 *
 * <p>relay job 依赖 outbox 仓储和消息队列端口，单独配置后可以清晰表达它是跨仓储与 MQ 的集成任务。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentMqAdapterAutoConfiguration.class, SeahorseAgentOpsRepositoryAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentOutboxRelayAutoConfiguration {

    @Bean
    @ConditionalOnBean({OutboxEventRepositoryPort.class, MessageQueuePort.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.mq.outbox.relay", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(SeahorseOutboxRelayJob.class)
    public SeahorseOutboxRelayJob seahorseOutboxRelayJob(
            OutboxEventRepositoryPort outboxEventRepositoryPort,
            MessageQueuePort messageQueuePort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<DistributedLockPort> lockPort,
            ObjectProvider<MetadataQuarantinePort> quarantinePort,
            @Value("${seahorse-agent.adapters.mq.outbox.relay.batch-size:50}") int batchSize) {
        return new SeahorseOutboxRelayJob(outboxEventRepositoryPort, messageQueuePort,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                lockPort.getIfAvailable(DistributedLockPort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop), batchSize);
    }
}
