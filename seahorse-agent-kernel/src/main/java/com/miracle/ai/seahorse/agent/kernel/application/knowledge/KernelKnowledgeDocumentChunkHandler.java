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

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Handles knowledge document chunk events and resolves the pipeline used for ingestion.
 */
public class KernelKnowledgeDocumentChunkHandler {

    private static final Logger log = LoggerFactory.getLogger(KernelKnowledgeDocumentChunkHandler.class);
    static final String DEFAULT_PIPELINE_ID = "default-knowledge-document";

    private final KnowledgeDocumentInboundPort documentInboundPort;
    private final PipelineDefinitionRepositoryPort pipelineRepositoryPort;

    public KernelKnowledgeDocumentChunkHandler(KnowledgeDocumentInboundPort documentInboundPort,
                                               PipelineDefinitionRepositoryPort pipelineRepositoryPort) {
        this.documentInboundPort = Objects.requireNonNull(documentInboundPort,
                "documentInboundPort must not be null");
        this.pipelineRepositoryPort = Objects.requireNonNull(pipelineRepositoryPort,
                "pipelineRepositoryPort must not be null");
    }

    public void handle(KnowledgeDocumentChunkEvent event) {
        KnowledgeDocumentChunkEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        log.info("Received KnowledgeDocumentChunkEvent: docId={}, pipelineId={}, operator={}",
                safeEvent.docId(), safeEvent.pipelineId(), safeEvent.operator());

        try {
            String effectivePipelineId = safeEvent.pipelineId();
            PipelineDefinition pipeline;
            if (effectivePipelineId == null || effectivePipelineId.trim().isEmpty()) {
                log.info("pipelineId is empty, using built-in default knowledge document pipeline");
                pipeline = defaultPipeline();
                effectivePipelineId = pipeline.getId();
            } else {
                effectivePipelineId = effectivePipelineId.trim();
                log.debug("Fetching pipeline definition: pipelineId={}", effectivePipelineId);
                String finalEffectivePipelineId = effectivePipelineId;
                pipeline = pipelineRepositoryPort.findById(effectivePipelineId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "ingestion pipeline does not exist: " + finalEffectivePipelineId));
            }

            log.info("Executing chunk processing: docId={}, pipelineId={}, operator={}",
                    safeEvent.docId(), effectivePipelineId, safeEvent.operator());
            documentInboundPort.executeChunk(safeEvent.docId(), pipeline, safeEvent.operator());

            log.info("Successfully processed KnowledgeDocumentChunkEvent: docId={}", safeEvent.docId());
        } catch (Exception e) {
            log.error("Failed to handle KnowledgeDocumentChunkEvent: docId={}, pipelineId={}, error={}",
                    safeEvent.docId(), safeEvent.pipelineId(), e.getMessage(), e);
            throw e;
        }
    }

    private PipelineDefinition defaultPipeline() {
        return PipelineDefinition.builder()
                .id(DEFAULT_PIPELINE_ID)
                .name("Default Knowledge Document Pipeline")
                .description("Built-in parser, chunker, and indexer pipeline used when no pipelineId is provided.")
                .nodes(List.of(
                        NodeConfig.builder()
                                .nodeId("1")
                                .nodeType("parser")
                                .nextNodeId("2")
                                .build(),
                        NodeConfig.builder()
                                .nodeId("2")
                                .nodeType("chunker")
                                .nextNodeId("3")
                                .build(),
                        NodeConfig.builder()
                                .nodeId("3")
                                .nodeType("indexer")
                                .build()))
                .build();
    }
}
