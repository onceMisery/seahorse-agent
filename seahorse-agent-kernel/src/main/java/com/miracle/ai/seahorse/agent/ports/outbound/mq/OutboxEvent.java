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

package com.miracle.ai.seahorse.agent.ports.outbound.mq;

import java.time.Instant;
import java.util.Objects;

/**
 * Outbox 事件快照。
 *
 * <p>该模型只表达可靠发布所需的稳定字段，不绑定 MyBatis、Pulsar 或具体数据库类型。
 */
public class OutboxEvent {

    private final String id;
    private final String topic;
    private final String messageKey;
    private final String eventType;
    private final String payloadJson;
    private final OutboxEventDelivery delivery;

    private OutboxEvent(Builder builder) {
        this.id = builder.id;
        this.topic = requireText(builder.topic, "topic");
        this.messageKey = requireText(builder.messageKey, "messageKey");
        this.eventType = requireText(builder.eventType, "eventType");
        this.payloadJson = requireText(builder.payloadJson, "payloadJson");
        this.delivery = Objects.requireNonNullElseGet(builder.delivery, OutboxEventDelivery::newPending);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String id() {
        return id;
    }

    public String topic() {
        return topic;
    }

    public String messageKey() {
        return messageKey;
    }

    public String eventType() {
        return eventType;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public OutboxEventDelivery delivery() {
        return delivery;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * Outbox 事件构造器，避免端口模型出现过长构造函数。
     */
    public static final class Builder {

        private String id;
        private String topic;
        private String messageKey;
        private String eventType;
        private String payloadJson;
        private OutboxEventDelivery delivery;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder messageKey(String messageKey) {
            this.messageKey = messageKey;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder payloadJson(String payloadJson) {
            this.payloadJson = payloadJson;
            return this;
        }

        public Builder delivery(OutboxEventDelivery delivery) {
            this.delivery = delivery;
            return this;
        }

        public OutboxEvent build() {
            return new OutboxEvent(this);
        }
    }

    /**
     * Outbox 投递状态。
     */
    public static final class OutboxEventDelivery {

        private final String status;
        private final int retryCount;
        private final Instant nextRetryTime;
        private final String lastError;

        private OutboxEventDelivery(String status, int retryCount, Instant nextRetryTime, String lastError) {
            this.status = requireStatus(status);
            this.retryCount = Math.max(0, retryCount);
            this.nextRetryTime = nextRetryTime == null ? Instant.now() : nextRetryTime;
            this.lastError = lastError;
        }

        public OutboxEventDelivery() {
            this(OutboxEventStatus.NEW, 0, Instant.now(), null);
        }

        public static OutboxEventDelivery newPending() {
            return new OutboxEventDelivery();
        }

        public static OutboxEventDelivery of(String status, int retryCount, Instant nextRetryTime, String lastError) {
            return new OutboxEventDelivery(status, retryCount, nextRetryTime, lastError);
        }

        public String status() {
            return status;
        }

        public int retryCount() {
            return retryCount;
        }

        public Instant nextRetryTime() {
            return nextRetryTime;
        }

        public String lastError() {
            return lastError;
        }

        private static String requireStatus(String status) {
            if (status == null || status.isBlank()) {
                return OutboxEventStatus.NEW;
            }
            return status;
        }
    }
}
