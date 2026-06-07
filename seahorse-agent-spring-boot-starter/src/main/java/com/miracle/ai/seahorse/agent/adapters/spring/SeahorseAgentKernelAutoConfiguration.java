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

import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentAdapterProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentKernelProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentPluginProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.metadata.MetadataIndexCompensationAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentVectorPorts;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Seahorse 原生 L1/L2 内核自动配置。
 *
 * <p>该配置只装配内核编排、Feature 注册表和 Web 本地流式任务能力，
 * 该配置只装配 Seahorse 原生 kernel 与端口，确保 starter 可作为独立微内核入口使用。
 */
@AutoConfiguration
@AutoConfigureAfter(SeahorseAgentNativeAdapterAutoConfiguration.class)
@EnableConfigurationProperties({
        AgentKernelProperties.class,
        AgentPluginProperties.class,
        AgentAdapterProperties.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        SeahorseAgentKernelChatAutoConfiguration.class,
        SeahorseAgentKernelDocumentRefreshAutoConfiguration.class,
        SeahorseAgentKernelKeywordAutoConfiguration.class,
        SeahorseAgentKernelKnowledgeAutoConfiguration.class,
        SeahorseAgentKernelMemoryAutoConfiguration.class,
        SeahorseAgentMemoryAggregationAutoConfiguration.class,
        SeahorseAgentMemoryMaintenanceAutoConfiguration.class,
        SeahorseAgentMemoryOutboxAutoConfiguration.class,
        SeahorseAgentMemoryRecallAutoConfiguration.class,
        SeahorseAgentKernelModelAutoConfiguration.class,
        SeahorseAgentKernelOpsAutoConfiguration.class,
        SeahorseAgentKernelPluginAutoConfiguration.class,
        SeahorseAgentKernelRegistryAutoConfiguration.class,
        SeahorseAgentKernelRetrievalAutoConfiguration.class,
        SeahorseAgentKernelAgentAutoConfiguration.class,
        SeahorseAgentKernelTraceAutoConfiguration.class
})
public class SeahorseAgentKernelAutoConfiguration {
    @Bean
    @ConditionalOnBean({KeywordIndexMaintenanceInboundPort.class, KnowledgeDocumentRepositoryPort.class,
            KnowledgeDocumentVectorPorts.class})
    @ConditionalOnMissingBean(MetadataIndexCompensationPort.class)
    public MetadataIndexCompensationPort seahorseMetadataIndexCompensationPort(
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            KeywordIndexMaintenanceInboundPort keywordIndexMaintenanceInboundPort,
            KnowledgeDocumentVectorPorts documentVectorPorts,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<MetadataBackfillInboundPort> backfillInboundPort) {
        return new MetadataIndexCompensationAdapter(
                documentRepositoryPort,
                keywordIndexMaintenanceInboundPort,
                documentVectorPorts,
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                backfillInboundPort.getIfAvailable());
    }

}
