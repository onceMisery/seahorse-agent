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

package com.miracle.ai.seahorse.agent.adapters.spring.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSendReceipt;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.ReliableMessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 支持 Outbox 的消息队列装饰器。
 *
 * <p>普通发送仍委托给底层 MQ adapter；可靠发布优先写入 outbox，缺少 outbox 仓储时退化为直接发送并记录告警。
 */
public class ReliableMessageQueueAdapter implements MessageQueuePort, MessageSubscriptionPort, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ReliableMessageQueueAdapter.class);
    private static final String LOG_MSG_OUTBOX_UNAVAILABLE =
            "Outbox repository unavailable, reliable publish falls back to direct send, topic={}";
    private static final String DEFAULT_TRACE_ID = "";
    private static final ObjectMapper FALLBACK_OBJECT_MAPPER = new ObjectMapper();

    private final MessageQueuePort delegate;
    private final MessageSubscriptionPort subscriptionDelegate;
    private final Supplier<OutboxEventRepositoryPort> outboxRepositorySupplier;
    private final Supplier<ObjectMapper> objectMapperSupplier;

    public ReliableMessageQueueAdapter(MessageQueuePort delegate,
                                       MessageSubscriptionPort subscriptionDelegate,
                                       Supplier<OutboxEventRepositoryPort> outboxRepositorySupplier,
                                       Supplier<ObjectMapper> objectMapperSupplier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.subscriptionDelegate = Objects.requireNonNull(subscriptionDelegate, "subscriptionDelegate must not be null");
        this.outboxRepositorySupplier = Objects.requireNonNull(outboxRepositorySupplier,
                "outboxRepositorySupplier must not be null");
        this.objectMapperSupplier = Objects.requireNonNull(objectMapperSupplier, "objectMapperSupplier must not be null");
    }

    @Override
    public MessageSendReceipt send(String topic, String key, String bizDesc, Object body) {
        return delegate.send(topic, key, bizDesc, body);
    }

    @Override
    public void publishReliable(String topic, String key, String bizDesc, Object body) {
        OutboxEventRepositoryPort repositoryPort = outboxRepositorySupplier.get();
        if (repositoryPort == null) {
            LOG.warn(LOG_MSG_OUTBOX_UNAVAILABLE, topic);
            delegate.send(topic, key, bizDesc, body);
            return;
        }
        String resolvedKey = resolveKey(key);
        ReliableMessageEnvelope envelope = buildEnvelope(resolvedKey, body);
        repositoryPort.append(OutboxEvent.builder()
                .topic(topic)
                .messageKey(resolvedKey)
                .eventType(envelope.getEventType())
                .payloadJson(toJson(envelope))
                .build());
    }

    @Override
    public <T> AutoCloseable subscribe(String topic,
                                       String subscriptionName,
                                       Class<T> payloadType,
                                       MessageHandler<T> handler) {
        return subscriptionDelegate.subscribe(topic, subscriptionName, payloadType, handler);
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private ReliableMessageEnvelope buildEnvelope(String key, Object body) {
        ReliableMessageEnvelope envelope = new ReliableMessageEnvelope();
        envelope.setKey(key);
        envelope.setEventType(resolveEventType(body));
        envelope.setPayloadJson(toJson(body));
        envelope.setTraceId(DEFAULT_TRACE_ID);
        envelope.setTimestamp(System.currentTimeMillis());
        return envelope;
    }

    private String resolveEventType(Object body) {
        if (body == null) {
            return "unknown";
        }
        return body.getClass().getSimpleName();
    }

    private String resolveKey(String key) {
        if (key == null || key.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return key;
    }

    private String toJson(Object body) {
        try {
            return objectMapper().writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Serialize reliable MQ payload failed", ex);
        }
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = objectMapperSupplier.get();
        if (objectMapper == null) {
            return FALLBACK_OBJECT_MAPPER;
        }
        return objectMapper;
    }
}
