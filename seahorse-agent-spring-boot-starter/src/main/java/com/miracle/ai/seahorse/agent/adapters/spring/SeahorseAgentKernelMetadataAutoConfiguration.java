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

import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataDictionaryService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataExtractionResultService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataQualityService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataQuarantineService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataReviewService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataSchemaService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataSchemaUsageService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelVersionQualityComparisonService;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataDictionaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataExtractionResultInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 元数据治理能力自动配置。
 *
 * <p>元数据回填、质检、复核、补偿和字典治理存在稳定的内部协作关系，拆分到独立配置后可避免主
 * kernel 配置继续承担治理链路的装配细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentKernelAutoConfiguration.class, SeahorseAgentMetadataAdapterAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelMetadataAutoConfiguration {

    @Bean
    @ConditionalOnBean(MetadataQualityReportRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataQualityInboundPort.class)
    public KernelMetadataQualityService seahorseMetadataQualityInboundPort(
            MetadataQualityReportRepositoryPort reportRepositoryPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelMetadataQualityService(reportRepositoryPort, observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(MetadataSchemaUsageReportRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataSchemaUsageInboundPort.class)
    public KernelMetadataSchemaUsageService seahorseMetadataSchemaUsageInboundPort(
            MetadataSchemaUsageReportRepositoryPort reportRepositoryPort) {
        return new KernelMetadataSchemaUsageService(reportRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(MetadataReviewManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataReviewInboundPort.class)
    public KernelMetadataReviewService seahorseMetadataReviewInboundPort(
            MetadataReviewManagementRepositoryPort reviewRepositoryPort,
            ObjectProvider<MetadataCanonicalWritePort> canonicalWritePort,
            ObjectProvider<MetadataQuarantinePort> quarantinePort,
            ObjectProvider<MetadataReviewReExtractPort> reExtractPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelMetadataReviewService(
                reviewRepositoryPort,
                canonicalWritePort.getIfAvailable(MetadataCanonicalWritePort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop),
                MetadataIndexCompensationPort.noop(),
                reExtractPort.getIfAvailable(MetadataReviewReExtractPort::noop),
                observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(MetadataQuarantineManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataQuarantineInboundPort.class)
    public KernelMetadataQuarantineService seahorseMetadataQuarantineInboundPort(
            MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort,
            @Value("${seahorse-agent.metadata.governance.quarantine.max-retry-count:3}") int maxRetryCount,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelMetadataQuarantineService(quarantineRepositoryPort, maxRetryCount,
                observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(MetadataSchemaManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataSchemaInboundPort.class)
    public KernelMetadataSchemaService seahorseMetadataSchemaInboundPort(
            MetadataSchemaManagementRepositoryPort repositoryPort,
            ObjectProvider<MetadataSchemaIndexSyncPort> indexSyncPort,
            ObjectProvider<MetadataIndexCompensationPort> indexCompensationPort) {
        return new KernelMetadataSchemaService(repositoryPort,
                indexSyncPort.getIfAvailable(MetadataSchemaIndexSyncPort::noop),
                indexCompensationPort.getIfAvailable(MetadataIndexCompensationPort::noop));
    }

    @Bean
    @ConditionalOnBean(MetadataDictionaryManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataDictionaryInboundPort.class)
    public KernelMetadataDictionaryService seahorseMetadataDictionaryInboundPort(
            MetadataDictionaryManagementRepositoryPort repositoryPort) {
        return new KernelMetadataDictionaryService(repositoryPort);
    }

    @Bean
    @ConditionalOnBean(MetadataExtractionResultManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataExtractionResultInboundPort.class)
    public KernelMetadataExtractionResultService seahorseMetadataExtractionResultInboundPort(
            MetadataExtractionResultManagementRepositoryPort repositoryPort) {
        return new KernelMetadataExtractionResultService(repositoryPort);
    }

    @Bean
    @ConditionalOnBean({MetadataQualityInboundPort.class, RetrievalEvaluationInboundPort.class})
    @ConditionalOnMissingBean(VersionQualityComparisonInboundPort.class)
    public KernelVersionQualityComparisonService seahorseVersionQualityComparisonInboundPort(
            MetadataQualityInboundPort metadataQualityInboundPort,
            RetrievalEvaluationInboundPort retrievalEvaluationInboundPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelVersionQualityComparisonService(
                metadataQualityInboundPort,
                retrievalEvaluationInboundPort,
                observationPort.getIfAvailable());
    }
}
