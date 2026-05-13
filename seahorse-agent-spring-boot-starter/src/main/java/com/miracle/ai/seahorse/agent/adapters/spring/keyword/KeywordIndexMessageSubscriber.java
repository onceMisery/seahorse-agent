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

import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import org.springframework.context.SmartLifecycle;

import java.util.Objects;

/**
 * 关键词索引消息订阅器。
 *
 * <p>订阅器只负责把异步事件还原为 {@link KeywordIndexPort} 调用，具体写入 Elasticsearch
 * 还是 PostgreSQL FTS 由被注入的 delegate adapter 决定。
 */
public class KeywordIndexMessageSubscriber implements SmartLifecycle {

    private final MessageSubscriptionPort subscriptionPort;
    private final String topic;
    private final String subscriptionName;
    private final KeywordIndexPort delegate;
    private volatile boolean running;
    private AutoCloseable subscription;

    public KeywordIndexMessageSubscriber(MessageSubscriptionPort subscriptionPort,
                                         String topic,
                                         String subscriptionName,
                                         KeywordIndexPort delegate) {
        this.subscriptionPort = Objects.requireNonNull(subscriptionPort, "subscriptionPort must not be null");
        this.topic = hasText(topic) ? topic : KeywordIndexOutboxAdapter.DEFAULT_TOPIC;
        this.subscriptionName = hasText(subscriptionName) ? subscriptionName : "seahorse-keyword-index";
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        subscription = subscriptionPort.subscribe(topic, subscriptionName, KeywordIndexEvent.class, this::handle);
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        try {
            if (subscription != null) {
                subscription.close();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Close keyword index subscription failed", ex);
        } finally {
            subscription = null;
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void handle(KeywordIndexEvent event) {
        if (event == null || event.operation() == null) {
            return;
        }
        switch (event.operation()) {
            case KeywordIndexOutboxAdapter.OP_INDEX_DOCUMENT_CHUNKS ->
                    delegate.indexDocumentChunks(event.kbId(), event.docId(), event.chunks());
            case KeywordIndexOutboxAdapter.OP_DELETE_DOCUMENT_CHUNKS ->
                    delegate.deleteDocumentChunks(event.kbId(), event.docId());
            case KeywordIndexOutboxAdapter.OP_REBUILD_DOCUMENT ->
                    delegate.rebuildDocument(event.kbId(), event.docId());
            case KeywordIndexOutboxAdapter.OP_REBUILD_KNOWLEDGE_BASE ->
                    delegate.rebuildKnowledgeBase(event.kbId());
            default -> {
                // 未识别事件保持幂等跳过，避免错误事件阻断同批次后续消息。
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
