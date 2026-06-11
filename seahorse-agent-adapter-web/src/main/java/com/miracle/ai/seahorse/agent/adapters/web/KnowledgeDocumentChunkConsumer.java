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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeDocumentService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentChunkEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 知识库文档处理消费者
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeDocumentChunkConsumer {

    @Autowired
    private ApplicationContext applicationContext;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        KernelKnowledgeDocumentService documentService;
        try {
            documentService = applicationContext.getBean(KernelKnowledgeDocumentService.class);
        } catch (Exception e) {
            log.warn("KernelKnowledgeDocumentService not available, document chunk consumer not started");
            return;
        }

        // Find any bean that implements MessageSubscriptionPort
        MessageSubscriptionPort subscriptionPort = null;
        try {
            var beans = applicationContext.getBeansOfType(MessageSubscriptionPort.class);
            if (!beans.isEmpty()) {
                subscriptionPort = beans.values().iterator().next();
            }
        } catch (Exception e) {
            log.warn("Failed to find MessageSubscriptionPort beans", e);
        }

        if (subscriptionPort == null) {
            log.warn("MessageSubscriptionPort not available, document chunk consumer not started");
            return;
        }
        String topic = KernelKnowledgeDocumentService.DEFAULT_CHUNK_TOPIC;
        String subscription = "seahorse-document-chunk-consumer";

        log.info("Starting knowledge document chunk consumer: topic={}, subscription={}", topic, subscription);

        try {
            subscriptionPort.subscribe(
                topic,
                subscription,
                KnowledgeDocumentChunkEvent.class,
                chunkEvent -> {
                    log.info("Processing document chunk: docId={}, kbId={}, operator={}, pipelineId={}",
                            chunkEvent.docId(), chunkEvent.kbId(), chunkEvent.operator(), chunkEvent.pipelineId());

                    try {
                        var pipeline = com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition.builder()
                                .id(chunkEvent.pipelineId())
                                .build();
                        documentService.executeChunk(chunkEvent.docId(), pipeline, chunkEvent.operator());
                        log.info("Document chunk processing completed: docId={}", chunkEvent.docId());
                    } catch (Exception e) {
                        log.error("Failed to process document chunk: docId={}", chunkEvent.docId(), e);
                    }
                }
            );
            log.info("Knowledge document chunk consumer started successfully");
        } catch (Exception e) {
            log.error("Failed to start knowledge document chunk consumer", e);
        }
    }
}
