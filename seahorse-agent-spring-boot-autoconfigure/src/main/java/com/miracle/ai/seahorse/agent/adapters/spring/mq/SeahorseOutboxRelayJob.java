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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String STAGE_INDEX = "INDEX";
    private static final String REASON_OUTBOX_RELAY_FAILED = "OUTBOX_RELAY_FAILED";

    private final OutboxEventRepositoryPort repositoryPort;
    private final MessageQueuePort messageQueuePort;
    private final ObjectMapper objectMapper;
    private final DistributedLockPort lockPort;
    private final MetadataQuarantinePort quarantinePort;
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
        this(repositoryPort, messageQueuePort, objectMapper, lockPort, MetadataQuarantinePort.noop(), batchSize);
    }

    public SeahorseOutboxRelayJob(OutboxEventRepositoryPort repositoryPort,
                                  MessageQueuePort messageQueuePort,
                                  ObjectMapper objectMapper,
                                  DistributedLockPort lockPort,
                                  MetadataQuarantinePort quarantinePort,
                                  int batchSize) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.messageQueuePort = Objects.requireNonNull(messageQueuePort, "messageQueuePort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.lockPort = Objects.requireNonNullElse(lockPort, DistributedLockPort.noop());
        this.quarantinePort = Objects.requireNonNullElse(quarantinePort, MetadataQuarantinePort.noop());
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }

    @Scheduled(
            fixedDelayString = "${seahorse.agent.adapters.mq.outbox.relay.fixed-delay-ms:5000}",
            initialDelayString = "${seahorse.agent.adapters.mq.outbox.relay.initial-delay-ms:10000}")
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
        quarantineOutboxFailure(event, ex, retryCount);
        LOG.error("Seahorse outbox relay failed, id={}, topic={}", event.id(), event.topic(), ex);
    }

    private void quarantineOutboxFailure(OutboxEvent event, Exception ex, int retryCount) {
        try {
            Map<String, Object> snapshot = outboxSnapshot(event, ex, retryCount);
            quarantinePort.quarantine(new MetadataQuarantineItem(
                    text(snapshot.get("tenantId")),
                    text(snapshot.get("kbId")),
                    text(snapshot.get("docId")),
                    event.id(),
                    STAGE_INDEX,
                    REASON_OUTBOX_RELAY_FAILED,
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName()),
                    snapshot));
        } catch (RuntimeException ignored) {
            // 隔离记录失败不能覆盖 Outbox 原始重试状态。
        }
    }

    private Map<String, Object> outboxSnapshot(OutboxEvent event, Exception ex, int retryCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("eventId", event.id());
        snapshot.put("topic", event.topic());
        snapshot.put("messageKey", event.messageKey());
        snapshot.put("eventType", event.eventType());
        snapshot.put("retryCount", retryCount);
        snapshot.put("exception", ex.getClass().getSimpleName());
        snapshot.put("error", Objects.requireNonNullElse(ex.getMessage(), ""));
        addMessageKeyParts(event.messageKey(), snapshot);
        addPayloadIdentity(event.payloadJson(), snapshot);
        return snapshot;
    }

    private void addMessageKeyParts(String messageKey, Map<String, Object> snapshot) {
        if (messageKey == null || messageKey.isBlank()) {
            return;
        }
        String[] parts = messageKey.split(":", 2);
        if (parts.length > 0 && !parts[0].isBlank()) {
            snapshot.putIfAbsent("kbId", parts[0]);
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            snapshot.putIfAbsent("docId", parts[1]);
        }
    }

    private void addPayloadIdentity(String payloadJson, Map<String, Object> snapshot) {
        try {
            JsonNode payload = objectMapper.readTree(objectMapper.readValue(payloadJson, ReliableMessageEnvelope.class)
                    .getPayloadJson());
            putTextIfPresent(snapshot, "tenantId", firstText(
                    payload.path("tenantId").asText(""),
                    payload.path("tenant_id").asText("")));
            putTextIfPresent(snapshot, "kbId", payload.path("kbId").asText(""));
            putTextIfPresent(snapshot, "docId", payload.path("docId").asText(""));
            putTextIfPresent(snapshot, "operation", payload.path("operation").asText(""));
            addFirstChunkIdentity(payload, snapshot);
        } catch (RuntimeException | java.io.IOException ignored) {
            // payload 解析失败时保留 event 级快照，避免二次异常影响主流程。
        }
    }

    private void addFirstChunkIdentity(JsonNode payload, Map<String, Object> snapshot) {
        JsonNode chunks = payload.path("chunks");
        if (!chunks.isArray() || chunks.isEmpty()) {
            return;
        }
        JsonNode metadata = chunks.get(0).path("metadata");
        putTextIfPresent(snapshot, "tenantId", firstText(
                metadata.path("tenant_id").asText(""),
                metadata.path("tenantId").asText("")));
        putTextIfPresent(snapshot, "kbId", firstText(
                metadata.path("kb_id").asText(""),
                metadata.path("kbId").asText("")));
        putTextIfPresent(snapshot, "docId", firstText(
                metadata.path("doc_id").asText(""),
                metadata.path("docId").asText("")));
    }

    private void putTextIfPresent(Map<String, Object> snapshot, String key, String value) {
        if (value != null && !value.isBlank()) {
            snapshot.put(key, value);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : Objects.requireNonNullElse(second, "");
    }
}
