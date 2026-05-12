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

package com.miracle.ai.seahorse.agent.adapters.mq.direct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSendReceipt;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内直连消息队列 adapter。
 *
 * <p>该实现不依赖外部 Broker，适用于本地开发、单体部署或测试环境。生产环境需要可靠投递时应切换到 Pulsar 等专用 adapter。
 */
public class DirectMessageQueueAdapter implements MessageQueuePort, MessageSubscriptionPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<DirectMessage> messages = new CopyOnWriteArrayList<>();
    private final Map<String, List<DirectSubscription<?>>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public MessageSendReceipt send(String topic, String key, String bizDesc, Object body) {
        String safeTopic = requireText(topic, "topic");
        String messageId = UUID.randomUUID().toString();
        long publishTime = Instant.now().toEpochMilli();
        messages.add(new DirectMessage(messageId, safeTopic, Objects.requireNonNullElse(key, ""), bizDesc, body));
        dispatch(safeTopic, body);
        return new MessageSendReceipt(messageId, safeTopic, key, publishTime);
    }

    @Override
    public void publishReliable(String topic, String key, String bizDesc, Object body) {
        send(topic, key, bizDesc, body);
    }

    public List<DirectMessage> messages() {
        return List.copyOf(messages);
    }

    @Override
    public <T> AutoCloseable subscribe(String topic,
                                       String subscriptionName,
                                       Class<T> payloadType,
                                       MessageHandler<T> handler) {
        String safeTopic = requireText(topic, "topic");
        DirectSubscription<T> subscription = new DirectSubscription<>(
                safeTopic,
                requireText(subscriptionName, "subscriptionName"),
                Objects.requireNonNull(payloadType, "payloadType must not be null"),
                Objects.requireNonNull(handler, "handler must not be null"));
        subscriptions.computeIfAbsent(safeTopic, ignored -> new CopyOnWriteArrayList<>()).add(subscription);
        return () -> unsubscribe(subscription);
    }

    private void dispatch(String topic, Object body) {
        List<DirectSubscription<?>> topicSubscriptions = subscriptions.get(topic);
        if (topicSubscriptions == null || topicSubscriptions.isEmpty()) {
            return;
        }
        for (DirectSubscription<?> subscription : topicSubscriptions) {
            subscription.dispatch(body);
        }
    }

    private void unsubscribe(DirectSubscription<?> subscription) {
        List<DirectSubscription<?>> topicSubscriptions = subscriptions.get(subscription.topic());
        if (topicSubscriptions != null) {
            topicSubscriptions.remove(subscription);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * 进程内消息快照。
     *
     * @param messageId 消息 ID
     * @param topic     主题
     * @param key       消息 key
     * @param bizDesc   业务描述
     * @param body      消息体
     */
    public record DirectMessage(String messageId, String topic, String key, String bizDesc, Object body) {
    }

    private record DirectSubscription<T>(
            String topic,
            String subscriptionName,
            Class<T> payloadType,
            MessageHandler<T> handler) {

        private void dispatch(Object body) {
            if (payloadType.isInstance(body)) {
                handler.handle(payloadType.cast(body));
                return;
            }
            handler.handle(OBJECT_MAPPER.convertValue(body, payloadType));
        }
    }
}
