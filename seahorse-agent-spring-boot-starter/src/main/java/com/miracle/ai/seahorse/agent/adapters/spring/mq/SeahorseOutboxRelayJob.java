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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.ReliableMessageEnvelope;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Seahorse 原生 Outbox 转发任务。
 *
 * <p>任务只依赖 MQ 与 Outbox 端口，避免把 Pulsar SDK 或 JDBC 细节泄漏到内核层。
 */
public class SeahorseOutboxRelayJob {

    private static final Logger LOG = LoggerFactory.getLogger(SeahorseOutboxRelayJob.class);
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long MAX_RETRY_DELAY_SECONDS = 300L;
    private static final long RETRY_DELAY_STEP_SECONDS = 30L;
    private static final String LOCK_NAME = "job:outbox-relay";
    private static final Duration LOCK_LEASE = Duration.ofMinutes(5);

    private final OutboxEventRepositoryPort repositoryPort;
    private final MessageQueuePort messageQueuePort;
    private final ObjectMapper objectMapper;
    private final DistributedLockPort lockPort;
    private final int batchSize;

    public SeahorseOutboxRelayJob(OutboxEventRepositoryPort repositoryPort,
                                  MessageQueuePort messageQueuePort,
                                  ObjectMapper objectMapper,
                                  int batchSize) {
        this(repositoryPort, messageQueuePort, objectMapper, DistributedLockPort.noop(), batchSize);
    }

    public SeahorseOutboxRelayJob(OutboxEventRepositoryPort repositoryPort,
                                  MessageQueuePort messageQueuePort,
                                  ObjectMapper objectMapper,
                                  DistributedLockPort lockPort,
                                  int batchSize) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.messageQueuePort = Objects.requireNonNull(messageQueuePort, "messageQueuePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }

    @Scheduled(
            fixedDelayString = "${seahorse-agent.adapters.mq.outbox.relay.fixed-delay-ms:5000}",
            initialDelayString = "${seahorse-agent.adapters.mq.outbox.relay.initial-delay-ms:10000}")
    public void relay() {
        if (!lockPort.tryLock(LOCK_NAME, Duration.ZERO, LOCK_LEASE)) {
            return;
        }
        try {
            List<OutboxEvent> events = repositoryPort.claimPending(batchSize, Instant.now());
            for (OutboxEvent event : events) {
                relayOne(event);
            }
        } finally {
            lockPort.unlock(LOCK_NAME);
        }
    }

    private void relayOne(OutboxEvent event) {
        if (event == null || !repositoryPort.markSending(event.id())) {
            return;
        }
        try {
            ReliableMessageEnvelope envelope = objectMapper.readValue(event.payloadJson(), ReliableMessageEnvelope.class);
            JsonNode payload = objectMapper.readTree(envelope.getPayloadJson());
            messageQueuePort.send(event.topic(), event.messageKey(), event.eventType(), payload);
            repositoryPort.markSent(event.id());
        } catch (Exception ex) {
            markFailed(event, ex);
        }
    }

    private void markFailed(OutboxEvent event, Exception ex) {
        int retryCount = event.delivery().retryCount() + 1;
        long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS, retryCount * RETRY_DELAY_STEP_SECONDS);
        repositoryPort.markFailed(event.id(), retryCount, Instant.now().plusSeconds(delaySeconds), ex.getMessage());
        LOG.error("Seahorse outbox relay failed, id={}, topic={}", event.id(), event.topic(), ex);
    }
}
