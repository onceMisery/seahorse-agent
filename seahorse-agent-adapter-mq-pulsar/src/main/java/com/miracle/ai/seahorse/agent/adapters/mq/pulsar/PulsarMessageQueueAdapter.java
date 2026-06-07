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

package com.miracle.ai.seahorse.agent.adapters.mq.pulsar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSendReceipt;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Apache Pulsar 消息队列 adapter。
 */
public class PulsarMessageQueueAdapter implements MessageQueuePort, MessageSubscriptionPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PulsarMessageQueueAdapter.class);

    private final PulsarClient pulsarClient;

    private final ObjectMapper objectMapper;

    private final PulsarMessageQueueProperties properties;

    private final Map<String, Producer<PulsarMessageEnvelope>> producers = new ConcurrentHashMap<>();
    private final Map<String, Consumer<PulsarMessageEnvelope>> consumers = new ConcurrentHashMap<>();

    public PulsarMessageQueueAdapter(
            PulsarClient pulsarClient,
            ObjectMapper objectMapper,
            PulsarMessageQueueProperties properties) {
        this.pulsarClient = Objects.requireNonNull(pulsarClient, "pulsarClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNullElseGet(properties, PulsarMessageQueueProperties::new);
    }

    @Override
    public MessageSendReceipt send(String topic, String key, String bizDesc, Object body) {
        String safeTopic = requireText(topic, "topic");
        String resolvedKey = resolveKey(key);
        PulsarMessageEnvelope envelope = buildEnvelope(resolvedKey, body);
        try {
            Producer<PulsarMessageEnvelope> producer = producers.computeIfAbsent(safeTopic, this::createProducer);
            MessageId messageId = producer.newMessage()
                    .key(resolvedKey)
                    .value(envelope)
                    .send();
            return new MessageSendReceipt(messageId.toString(), safeTopic, resolvedKey, envelope.getTimestamp());
        } catch (Exception ex) {
            throw new IllegalStateException("Pulsar send failed: " + safeTopic, ex);
        }
    }

    @Override
    public void publishReliable(String topic, String key, String bizDesc, Object body) {
        send(topic, key, bizDesc, body);
    }

    @Override
    public void close() {
        producers.values().forEach(this::closeProducer);
        consumers.values().forEach(this::closeConsumer);
        producers.clear();
        consumers.clear();
    }

    @Override
    public <T> AutoCloseable subscribe(String topic,
                                       String subscriptionName,
                                       Class<T> payloadType,
                                       MessageHandler<T> handler) {
        String safeTopic = requireText(topic, "topic");
        String safeSubscription = requireText(subscriptionName, "subscriptionName");
        Class<T> safePayloadType = Objects.requireNonNull(payloadType, "payloadType must not be null");
        MessageHandler<T> safeHandler = Objects.requireNonNull(handler, "handler must not be null");

        log.info("Creating Pulsar consumer: topic={}, subscription={}, payloadType={}",
                safeTopic, safeSubscription, safePayloadType.getSimpleName());

        String key = safeTopic + "#" + safeSubscription;
        Consumer<PulsarMessageEnvelope> consumer = consumers.computeIfAbsent(key,
                ignored -> createConsumer(safeTopic, safeSubscription, safePayloadType, safeHandler));

        log.info("Pulsar consumer created successfully: topic={}, subscription={}", safeTopic, safeSubscription);

        return () -> closeAndRemoveConsumer(key, consumer);
    }

    private Producer<PulsarMessageEnvelope> createProducer(String topic) {
        try {
            return pulsarClient.newProducer(Schema.JSON(PulsarMessageEnvelope.class))
                    .topic(topic)
                    .compressionType(resolveCompressionType())
                    .enableBatching(properties.isBatchingEnabled())
                    .batchingMaxMessages(properties.getBatchingMaxMessages())
                    .batchingMaxPublishDelay(properties.getBatchingMaxPublishDelayMs(), TimeUnit.MILLISECONDS)
                    .sendTimeout(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS)
                    .blockIfQueueFull(properties.isBlockIfQueueFull())
                    .create();
        } catch (PulsarClientException ex) {
            throw new IllegalStateException("Create Pulsar producer failed: " + topic, ex);
        }
    }

    private <T> Consumer<PulsarMessageEnvelope> createConsumer(String topic,
                                                              String subscriptionName,
                                                              Class<T> payloadType,
                                                              MessageHandler<T> handler) {
        try {
            return pulsarClient.newConsumer(Schema.JSON(PulsarMessageEnvelope.class))
                    .topic(topic)
                    .subscriptionName(subscriptionName)
                    .subscriptionType(SubscriptionType.Shared)
                    .messageListener((consumer, message) -> handleMessage(consumer, message, payloadType, handler))
                    .subscribe();
        } catch (PulsarClientException ex) {
            throw new IllegalStateException("Create Pulsar consumer failed: " + topic, ex);
        }
    }

    private <T> void handleMessage(Consumer<PulsarMessageEnvelope> consumer,
                                   Message<PulsarMessageEnvelope> message,
                                   Class<T> payloadType,
                                   MessageHandler<T> handler) {
        try {
            PulsarMessageEnvelope envelope = message.getValue();
            T payload = objectMapper.readValue(envelope.getPayloadJson(), payloadType);
            handler.handle(payload);
            consumer.acknowledge(message);
        } catch (Exception ex) {
            negativeAcknowledge(consumer, message);
        }
    }

    private void negativeAcknowledge(Consumer<PulsarMessageEnvelope> consumer,
                                     Message<PulsarMessageEnvelope> message) {
        consumer.negativeAcknowledge(message);
    }

    private PulsarMessageEnvelope buildEnvelope(String key, Object body) {
        PulsarMessageEnvelope envelope = new PulsarMessageEnvelope();
        envelope.setKey(key);
        envelope.setEventType(resolveEventType(body));
        envelope.setPayloadJson(toJson(body));
        envelope.setTraceId("");
        envelope.setTimestamp(System.currentTimeMillis());
        return envelope;
    }

    private String resolveEventType(Object body) {
        if (body == null) {
            return "unknown";
        }
        return body.getClass().getSimpleName();
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Serialize MQ payload failed", ex);
        }
    }

    private CompressionType resolveCompressionType() {
        try {
            return CompressionType.valueOf(properties.getCompressionType());
        } catch (IllegalArgumentException ex) {
            return CompressionType.LZ4;
        }
    }

    private String resolveKey(String key) {
        if (key == null || key.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return key;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private void closeProducer(Producer<PulsarMessageEnvelope> producer) {
        try {
            producer.close();
        } catch (PulsarClientException ex) {
            throw new IllegalStateException("Close Pulsar producer failed", ex);
        }
    }

    private void closeConsumer(Consumer<PulsarMessageEnvelope> consumer) {
        try {
            consumer.close();
        } catch (PulsarClientException ex) {
            throw new IllegalStateException("Close Pulsar consumer failed", ex);
        }
    }

    private void closeAndRemoveConsumer(String key, Consumer<PulsarMessageEnvelope> consumer) {
        closeConsumer(consumer);
        consumers.remove(key);
    }
}
