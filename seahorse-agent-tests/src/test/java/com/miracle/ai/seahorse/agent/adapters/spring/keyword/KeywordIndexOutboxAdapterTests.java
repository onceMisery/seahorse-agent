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

package com.miracle.ai.seahorse.agent.adapters.spring.keyword;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.adapters.mq.direct.DirectMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.ReliableMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.SeahorseOutboxRelayJob;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIndexOutboxAdapterTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldPublishKeywordIndexEventAndDelegateAfterRelay() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        DirectMessageQueueAdapter delegateQueue = new DirectMessageQueueAdapter();
        ReliableMessageQueueAdapter reliableQueue = new ReliableMessageQueueAdapter(
                delegateQueue, delegateQueue, () -> repository, () -> OBJECT_MAPPER);
        RecordingKeywordIndexPort keywordIndex = new RecordingKeywordIndexPort();
        KeywordIndexMessageSubscriber subscriber = new KeywordIndexMessageSubscriber(
                reliableQueue, KeywordIndexOutboxAdapter.DEFAULT_TOPIC, "keyword-index-test", keywordIndex);
        KeywordIndexOutboxAdapter adapter = new KeywordIndexOutboxAdapter(
                reliableQueue, KeywordIndexOutboxAdapter.DEFAULT_TOPIC);
        SeahorseOutboxRelayJob relayJob = new SeahorseOutboxRelayJob(repository, reliableQueue, OBJECT_MAPPER, 10);
        subscriber.start();

        adapter.indexDocumentChunks("kb-1", "doc-1", List.of(VectorChunk.builder().chunkId("chunk-1").build()));
        relayJob.relay();

        assertThat(repository.events).extracting(event -> event.delivery().status()).containsExactly(OutboxEventStatus.SENT);
        assertThat(keywordIndex.indexed).containsExactly("kb-1/doc-1/chunk-1");
    }

    private static final class RecordingKeywordIndexPort implements KeywordIndexPort {

        private final List<String> indexed = new ArrayList<>();

        @Override
        public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
            indexed.add(kbId + "/" + docId + "/" + chunks.get(0).getChunkId());
        }

        @Override
        public void deleteDocumentChunks(String kbId, String docId) {
        }
    }

    private static final class InMemoryOutboxRepository implements OutboxEventRepositoryPort {

        private final List<OutboxEvent> events = new ArrayList<>();

        @Override
        public void append(OutboxEvent event) {
            events.add(copy(event, "event-" + (events.size() + 1), OutboxEventStatus.NEW, 0, null));
        }

        @Override
        public List<OutboxEvent> claimPending(int batchSize, Instant now) {
            return events.stream()
                    .filter(event -> OutboxEventStatus.NEW.equals(event.delivery().status()))
                    .limit(batchSize)
                    .toList();
        }

        @Override
        public boolean markSending(String eventId) {
            replace(eventId, OutboxEventStatus.SENDING, 0, null);
            return true;
        }

        @Override
        public void markSent(String eventId) {
            replace(eventId, OutboxEventStatus.SENT, 0, null);
        }

        @Override
        public void markFailed(String eventId, int retryCount, Instant nextRetryTime, String lastError) {
            replace(eventId, OutboxEventStatus.FAILED, retryCount, lastError);
        }

        private void replace(String eventId, String status, int retryCount, String lastError) {
            for (int index = 0; index < events.size(); index++) {
                OutboxEvent event = events.get(index);
                if (event.id().equals(eventId)) {
                    events.set(index, copy(event, eventId, status, retryCount, lastError));
                    return;
                }
            }
        }

        private OutboxEvent copy(OutboxEvent event, String eventId, String status, int retryCount, String lastError) {
            return OutboxEvent.builder()
                    .id(eventId)
                    .topic(event.topic())
                    .messageKey(event.messageKey())
                    .eventType(event.eventType())
                    .payloadJson(event.payloadJson())
                    .delivery(OutboxEvent.OutboxEventDelivery.of(status, retryCount, Instant.now(), lastError))
                    .build();
        }
    }
}
