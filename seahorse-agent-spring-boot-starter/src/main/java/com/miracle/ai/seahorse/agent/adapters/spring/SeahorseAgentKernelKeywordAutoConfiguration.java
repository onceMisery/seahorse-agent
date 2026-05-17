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

import com.miracle.ai.seahorse.agent.kernel.application.keyword.KernelKeywordIndexMaintenanceService;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 关键词索引维护内核自动配置。
 *
 * <p>该配置只负责关键词索引补偿和批量维护链路，具体 Lucene/Elasticsearch/JDBC 适配器仍由 native keyword
 * 自动配置承载。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelKeywordAutoConfiguration {

    @Bean
    @ConditionalOnBean(KnowledgeDocumentRepositoryPort.class)
    @ConditionalOnMissingBean(KeywordIndexMaintenanceInboundPort.class)
    public KernelKeywordIndexMaintenanceService seahorseKeywordIndexMaintenanceInboundPort(
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            ObjectProvider<KeywordIndexPort> keywordIndexPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelKeywordIndexMaintenanceService(
                documentRepositoryPort,
                keywordIndexPort.getIfAvailable(KeywordIndexPort::noop),
                observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(KeywordIndexMaintenanceInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.keyword-index.maintenance", name = "scheduler-enabled",
            havingValue = "true")
    @ConditionalOnMissingBean
    public SeahorseKeywordIndexMaintenanceJob seahorseKeywordIndexMaintenanceJob(
            KeywordIndexMaintenanceInboundPort maintenanceInboundPort,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse-agent.keyword-index.maintenance.doc-ids:}") String docIds,
            @Value("${seahorse-agent.keyword-index.maintenance.kb-ids:}") String kbIds,
            @Value("${seahorse-agent.keyword-index.maintenance.batch-size:50}") int batchSize) {
        return new SeahorseKeywordIndexMaintenanceJob(
                maintenanceInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                docIds,
                kbIds,
                batchSize);
    }
}
