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

import com.miracle.ai.seahorse.agent.kernel.application.knowledge.DocumentRefreshServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelDocumentRefreshService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeDocumentChunkHandler;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeDocumentService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentChunkEvent;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档刷新与切片订阅自动配置。
 *
 * <p>该配置收拢知识文档刷新链路、批量刷新任务和切片消息订阅，避免这些异步编排能力分散在主 kernel
 * 自动配置中。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentKernelAutoConfiguration.class, SeahorseAgentKernelKnowledgeAutoConfiguration.class, SeahorseAgentKnowledgeRepositoryAutoConfiguration.class, SeahorseAgentIngestionRepositoryAutoConfiguration.class, SeahorseAgentStorageAdapterAutoConfiguration.class, SeahorseAgentLocalAdapterAutoConfiguration.class, SeahorseAgentMqAdapterAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelDocumentRefreshAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SeahorseAgentKernelDocumentRefreshAutoConfiguration.class);

    @Bean
    @ConditionalOnBean({DocumentRefreshSchedulePort.class, DocumentRefreshStateRepositoryPort.class,
            KnowledgeDocumentRepositoryPort.class, DocumentFetcherPort.class, ObjectStoragePort.class,
            KnowledgeDocumentInboundPort.class, PipelineDefinitionRepositoryPort.class, SchedulerPort.class})
    @ConditionalOnMissingBean
    public DocumentRefreshServicePorts seahorseDocumentRefreshServicePorts(
            DocumentRefreshSchedulePort schedulePort,
            DocumentRefreshStateRepositoryPort stateRepositoryPort,
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            DocumentFetcherPort documentFetcherPort,
            ObjectStoragePort objectStoragePort,
            KnowledgeDocumentInboundPort documentInboundPort,
            PipelineDefinitionRepositoryPort pipelineRepositoryPort,
            SchedulerPort schedulerPort,
            ObjectProvider<DistributedLockPort> lockPort) {
        return new DocumentRefreshServicePorts(
                schedulePort,
                stateRepositoryPort,
                documentRepositoryPort,
                documentFetcherPort,
                objectStoragePort,
                documentInboundPort,
                pipelineRepositoryPort,
                schedulerPort,
                lockPort.getIfAvailable(DistributedLockPort::noop));
    }

    @Bean
    @ConditionalOnBean(DocumentRefreshServicePorts.class)
    @ConditionalOnMissingBean(DocumentRefreshInboundPort.class)
    public KernelDocumentRefreshService seahorseDocumentRefreshInboundPort(
            DocumentRefreshServicePorts servicePorts) {
        return new KernelDocumentRefreshService(servicePorts);
    }

    @Bean
    @ConditionalOnBean(DocumentRefreshInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.document-refresh", name = "scheduler-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SeahorseDocumentRefreshJob seahorseDocumentRefreshJob(
            DocumentRefreshInboundPort refreshInboundPort,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse.agent.document-refresh.batch-size:20}") int batchSize) {
        return new SeahorseDocumentRefreshJob(refreshInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop), batchSize);
    }

    @Bean
    @ConditionalOnBean({KnowledgeDocumentInboundPort.class, PipelineDefinitionRepositoryPort.class})
    @ConditionalOnMissingBean
    public KernelKnowledgeDocumentChunkHandler seahorseKernelKnowledgeDocumentChunkHandler(
            KnowledgeDocumentInboundPort documentInboundPort,
            PipelineDefinitionRepositoryPort pipelineDefinitionRepositoryPort) {
        return new KernelKnowledgeDocumentChunkHandler(documentInboundPort, pipelineDefinitionRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({KernelKnowledgeDocumentChunkHandler.class, MessageSubscriptionPort.class})
    @ConditionalOnMissingBean(name = "seahorseKnowledgeDocumentChunkSubscription")
    public AutoCloseable seahorseKnowledgeDocumentChunkSubscription(
            KernelKnowledgeDocumentChunkHandler chunkHandler,
            MessageSubscriptionPort subscriptionPort,
            @Value("${seahorse.agent.adapters.mq.pulsar.topics.knowledge-document-chunk:"
                    + KernelKnowledgeDocumentService.DEFAULT_CHUNK_TOPIC + "}") String chunkTopic) {
        log.info("Creating Pulsar subscription for topic: {}, subscription: seahorse-knowledge-document-chunk", chunkTopic);
        AutoCloseable subscription = subscriptionPort.subscribe(chunkTopic, "seahorse-knowledge-document-chunk",
                KnowledgeDocumentChunkEvent.class, chunkHandler::handle);
        log.info("Pulsar subscription created successfully for topic: {}", chunkTopic);
        return subscription;
    }
}
