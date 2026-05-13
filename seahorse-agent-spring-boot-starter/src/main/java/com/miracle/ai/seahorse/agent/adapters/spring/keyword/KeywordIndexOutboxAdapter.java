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

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;

import java.util.List;
import java.util.Objects;

/**
 * 通过 Outbox 发布关键词索引事件的端口适配器。
 *
 * <p>入库链路只写可靠消息，实际索引由消费者调用具体 {@link KeywordIndexPort} 完成，
 * 用于隔离 Elasticsearch/PostgreSQL FTS 等后端波动对主入库链路的影响。
 */
public class KeywordIndexOutboxAdapter implements KeywordIndexPort {

    public static final String DEFAULT_TOPIC = "seahorse.keyword-index";
    static final String OP_INDEX_DOCUMENT_CHUNKS = "INDEX_DOCUMENT_CHUNKS";
    static final String OP_DELETE_DOCUMENT_CHUNKS = "DELETE_DOCUMENT_CHUNKS";
    static final String OP_REBUILD_DOCUMENT = "REBUILD_DOCUMENT";
    static final String OP_REBUILD_KNOWLEDGE_BASE = "REBUILD_KNOWLEDGE_BASE";

    private final MessageQueuePort messageQueuePort;
    private final String topic;

    public KeywordIndexOutboxAdapter(MessageQueuePort messageQueuePort, String topic) {
        this.messageQueuePort = Objects.requireNonNull(messageQueuePort, "messageQueuePort must not be null");
        this.topic = hasText(topic) ? topic : DEFAULT_TOPIC;
    }

    @Override
    public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
        publish(OP_INDEX_DOCUMENT_CHUNKS, kbId, docId, chunks);
    }

    @Override
    public void deleteDocumentChunks(String kbId, String docId) {
        publish(OP_DELETE_DOCUMENT_CHUNKS, kbId, docId, List.of());
    }

    @Override
    public void rebuildDocument(String kbId, String docId) {
        publish(OP_REBUILD_DOCUMENT, kbId, docId, List.of());
    }

    @Override
    public void rebuildKnowledgeBase(String kbId) {
        publish(OP_REBUILD_KNOWLEDGE_BASE, kbId, null, List.of());
    }

    private void publish(String operation, String kbId, String docId, List<VectorChunk> chunks) {
        KeywordIndexEvent event = new KeywordIndexEvent(operation, kbId, docId, chunks);
        messageQueuePort.publishReliable(topic, messageKey(kbId, docId), operation, event);
    }

    private String messageKey(String kbId, String docId) {
        if (hasText(docId)) {
            return kbId + ":" + docId;
        }
        return hasText(kbId) ? kbId : "keyword-index";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
