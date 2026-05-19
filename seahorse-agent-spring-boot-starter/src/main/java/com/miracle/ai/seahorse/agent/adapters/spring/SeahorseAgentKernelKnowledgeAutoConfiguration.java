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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionEngine;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionPipelineService;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionTaskService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeBaseService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeChunkService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeDocumentService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentVectorPorts;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataBackfillService;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionConditionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionNodeLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库与摄取链路自动配置。
 *
 * <p>该配置聚合知识库、文档、摄取任务与元数据回填等共享摄取上下文的 Bean，减少主 kernel 配置对
 * 知识域装配细节的直接承载。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(SeahorseAgentKernelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelKnowledgeAutoConfiguration {

    @Bean
    @ConditionalOnBean({KnowledgeBaseRepositoryPort.class, VectorCollectionAdminPort.class, ObjectStoragePort.class})
    @ConditionalOnMissingBean(KnowledgeBaseInboundPort.class)
    public KernelKnowledgeBaseService seahorseKnowledgeBaseInboundPort(
            KnowledgeBaseRepositoryPort knowledgeBaseRepositoryPort,
            VectorCollectionAdminPort vectorCollectionAdminPort,
            ObjectStoragePort objectStoragePort) {
        return new KernelKnowledgeBaseService(
                knowledgeBaseRepositoryPort, vectorCollectionAdminPort, objectStoragePort);
    }

    @Bean
    @ConditionalOnBean(KnowledgeChunkRepositoryPort.class)
    @ConditionalOnMissingBean(KnowledgeChunkInboundPort.class)
    public KernelKnowledgeChunkService seahorseKnowledgeChunkInboundPort(
            KnowledgeChunkRepositoryPort knowledgeChunkRepositoryPort,
            ObjectProvider<EmbeddingModelPort> embeddingModelPort,
            ObjectProvider<VectorIndexPort> vectorIndexPort) {
        return new KernelKnowledgeChunkService(
                knowledgeChunkRepositoryPort,
                embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop),
                vectorIndexPort.getIfAvailable(SeahorseAgentKernelKnowledgeAutoConfiguration::noopVectorIndexPort));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelIngestionEngine seahorseKernelIngestionEngine(ExtensionRegistry extensionRegistry,
                                                               FeatureActivationContext activationContext,
                                                               ObjectProvider<IngestionConditionPort> conditionPort,
                                                               ObjectProvider<IngestionNodeLogPort> nodeLogPort) {
        return new KernelIngestionEngine(extensionRegistry, activationContext,
                conditionPort.getIfAvailable(IngestionConditionPort::alwaysExecute),
                nodeLogPort.getIfAvailable(IngestionNodeLogPort::noop));
    }

    @Bean
    @ConditionalOnBean(IngestionPipelineRepositoryPort.class)
    @ConditionalOnMissingBean(IngestionPipelineInboundPort.class)
    public KernelIngestionPipelineService seahorseIngestionPipelineInboundPort(
            IngestionPipelineRepositoryPort pipelineRepositoryPort) {
        return new KernelIngestionPipelineService(pipelineRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({KernelIngestionEngine.class, PipelineDefinitionRepositoryPort.class,
            IngestionTaskRepositoryPort.class})
    @ConditionalOnMissingBean(IngestionTaskInboundPort.class)
    public KernelIngestionTaskService seahorseIngestionTaskInboundPort(
            KernelIngestionEngine ingestionEngine,
            PipelineDefinitionRepositoryPort pipelineDefinitionRepositoryPort,
            IngestionTaskRepositoryPort taskRepositoryPort) {
        return new KernelIngestionTaskService(
                ingestionEngine, pipelineDefinitionRepositoryPort, taskRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({KnowledgeBaseQueryPort.class, KnowledgeDocumentRepositoryPort.class,
            ObjectStoragePort.class, MessageQueuePort.class, KernelIngestionEngine.class})
    @ConditionalOnMissingBean
    public KnowledgeDocumentServicePorts seahorseKnowledgeDocumentServicePorts(
            KnowledgeBaseQueryPort knowledgeBaseQueryPort,
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            ObjectStoragePort objectStoragePort,
            MessageQueuePort messageQueuePort,
            KernelIngestionEngine ingestionEngine) {
        return new KnowledgeDocumentServicePorts(
                knowledgeBaseQueryPort, documentRepositoryPort, objectStoragePort, messageQueuePort, ingestionEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeDocumentVectorPorts seahorseKnowledgeDocumentVectorPorts(
            ObjectProvider<EmbeddingModelPort> embeddingModelPort,
            ObjectProvider<VectorIndexPort> vectorIndexPort,
            ObjectProvider<KeywordIndexPort> keywordIndexPort) {
        return new KnowledgeDocumentVectorPorts(
                embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop),
                vectorIndexPort.getIfAvailable(SeahorseAgentKernelKnowledgeAutoConfiguration::noopVectorIndexPort),
                keywordIndexPort.getIfAvailable(KeywordIndexPort::noop));
    }

    @Bean
    @ConditionalOnBean(KnowledgeDocumentServicePorts.class)
    @ConditionalOnMissingBean(KnowledgeDocumentInboundPort.class)
    public KernelKnowledgeDocumentService seahorseKernelKnowledgeDocumentService(
            KnowledgeDocumentServicePorts servicePorts,
            KnowledgeDocumentVectorPorts documentVectorPorts,
            ObjectProvider<DocumentRefreshSchedulePort> refreshSchedulePort,
            ObjectProvider<SchedulerPort> schedulerPort,
            @Value("${seahorse-agent.adapters.mq.pulsar.topics.knowledge-document-chunk:"
                    + KernelKnowledgeDocumentService.DEFAULT_CHUNK_TOPIC + "}") String chunkTopic) {
        return new KernelKnowledgeDocumentService(
                servicePorts,
                documentVectorPorts,
                chunkTopic,
                refreshSchedulePort.getIfAvailable(DocumentRefreshSchedulePort::noop),
                schedulerPort.getIfAvailable(SchedulerPort::none));
    }

    @Bean
    @ConditionalOnBean({KnowledgeDocumentRepositoryPort.class, ObjectStoragePort.class,
            PipelineDefinitionRepositoryPort.class, KernelIngestionEngine.class,
            MetadataBackfillJobRepositoryPort.class})
    @ConditionalOnMissingBean(MetadataBackfillInboundPort.class)
    public KernelMetadataBackfillService seahorseMetadataBackfillInboundPort(
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            ObjectStoragePort objectStoragePort,
            PipelineDefinitionRepositoryPort pipelineRepositoryPort,
            KernelIngestionEngine ingestionEngine,
            MetadataBackfillJobRepositoryPort jobRepositoryPort,
            ObjectProvider<MetadataExtractionResultRepositoryPort> extractionResultRepositoryPort,
            ObjectProvider<MetadataQuarantinePort> quarantinePort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelMetadataBackfillService(
                documentRepositoryPort,
                objectStoragePort,
                pipelineRepositoryPort,
                ingestionEngine,
                jobRepositoryPort,
                extractionResultRepositoryPort.getIfAvailable(MetadataExtractionResultRepositoryPort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop),
                observationPort.getIfAvailable());
    }

    private static VectorIndexPort noopVectorIndexPort() {
        return new VectorIndexPort() {
            @Override
            public void indexDocumentChunks(String collectionName, String docId,
                                            java.util.List<com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk> chunks) {
            }

            @Override
            public void updateChunk(String collectionName, String docId,
                                    com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk chunk) {
            }

            @Override
            public void deleteDocumentVectors(String collectionName, String docId) {
            }

            @Override
            public void deleteChunkById(String collectionName, String chunkId) {
            }

            @Override
            public void deleteChunksByIds(String collectionName, java.util.List<String> chunkIds) {
            }
        };
    }
}
