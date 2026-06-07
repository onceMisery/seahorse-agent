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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 原生文档分块消息处理器。
 *
 * <p>该处理器只编排消息到入库服务的转换，消息来源可以是 Pulsar adapter、Direct adapter 或测试驱动。
 */
public class KernelKnowledgeDocumentChunkHandler {

    private static final Logger log = LoggerFactory.getLogger(KernelKnowledgeDocumentChunkHandler.class);

    private final KnowledgeDocumentInboundPort documentInboundPort;
    private final PipelineDefinitionRepositoryPort pipelineRepositoryPort;

    public KernelKnowledgeDocumentChunkHandler(KnowledgeDocumentInboundPort documentInboundPort,
                                               PipelineDefinitionRepositoryPort pipelineRepositoryPort) {
        this.documentInboundPort = Objects.requireNonNull(documentInboundPort,
                "documentInboundPort must not be null");
        this.pipelineRepositoryPort = Objects.requireNonNull(pipelineRepositoryPort,
                "pipelineRepositoryPort must not be null");
    }

    /**
     * 处理文档分块事件。
     *
     * @param event 分块事件
     */
    public void handle(KnowledgeDocumentChunkEvent event) {
        log.info("Received KnowledgeDocumentChunkEvent: docId={}, pipelineId={}, operator={}",
                event.docId(), event.pipelineId(), event.operator());

        try {
            KnowledgeDocumentChunkEvent safeEvent = Objects.requireNonNull(event, "event must not be null");

            log.debug("Fetching pipeline definition: pipelineId={}", safeEvent.pipelineId());
            PipelineDefinition pipeline = pipelineRepositoryPort.findById(safeEvent.pipelineId())
                    .orElseThrow(() -> new IllegalArgumentException("入库流水线不存在：" + safeEvent.pipelineId()));

            log.info("Executing chunk processing: docId={}, pipelineId={}, operator={}",
                    safeEvent.docId(), safeEvent.pipelineId(), safeEvent.operator());
            documentInboundPort.executeChunk(safeEvent.docId(), pipeline, safeEvent.operator());

            log.info("Successfully processed KnowledgeDocumentChunkEvent: docId={}", safeEvent.docId());
        } catch (Exception e) {
            log.error("Failed to handle KnowledgeDocumentChunkEvent: docId={}, pipelineId={}, error={}",
                    event.docId(), event.pipelineId(), e.getMessage(), e);
            throw e;  // 重新抛出以触发 negative ack
        }
    }
}
