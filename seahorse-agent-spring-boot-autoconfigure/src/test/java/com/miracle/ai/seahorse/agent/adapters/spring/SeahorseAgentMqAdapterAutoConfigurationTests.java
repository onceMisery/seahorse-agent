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
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SeahorseAgentMqAdapterAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentMqAdapterAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void directReliablePublishUsesOutboxByDefaultWhenAvailable() {
        OutboxEventRepositoryPort outboxRepository = mock(OutboxEventRepositoryPort.class);

        contextRunner
                .withBean(OutboxEventRepositoryPort.class, () -> outboxRepository)
                .withPropertyValues("seahorse-agent.adapters.mq.type=direct")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MessageQueuePort queuePort = context.getBean(MessageQueuePort.class);
                    MessageSubscriptionPort subscriptionPort = context.getBean(MessageSubscriptionPort.class);
                    List<SamplePayload> received = new ArrayList<>();

                    AutoCloseable subscription = subscriptionPort.subscribe(
                            "topic-a", "sub-a", SamplePayload.class, received::add);
                    try {
                        queuePort.publishReliable("topic-a", "key-a", "biz", new SamplePayload("doc-1"));
                    } finally {
                        subscription.close();
                    }

                    assertThat(received).isEmpty();
                    verify(outboxRepository).append(any());
                });
    }

    @Test
    void directReliablePublishCanBypassOutboxWhenDisabled() {
        OutboxEventRepositoryPort outboxRepository = mock(OutboxEventRepositoryPort.class);

        contextRunner
                .withBean(OutboxEventRepositoryPort.class, () -> outboxRepository)
                .withPropertyValues(
                        "seahorse-agent.adapters.mq.type=direct",
                        "seahorse-agent.adapters.mq.reliable-outbox-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MessageQueuePort queuePort = context.getBean(MessageQueuePort.class);
                    MessageSubscriptionPort subscriptionPort = context.getBean(MessageSubscriptionPort.class);
                    List<SamplePayload> received = new ArrayList<>();

                    AutoCloseable subscription = subscriptionPort.subscribe(
                            "topic-a", "sub-a", SamplePayload.class, received::add);
                    try {
                        queuePort.publishReliable("topic-a", "key-a", "biz", new SamplePayload("doc-1"));
                    } finally {
                        subscription.close();
                    }

                    assertThat(received).containsExactly(new SamplePayload("doc-1"));
                    verifyNoInteractions(outboxRepository);
                });
    }

    private record SamplePayload(String id) {
    }
}
