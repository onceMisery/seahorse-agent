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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataGovernanceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataExtractionResultService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataSchemaUsageService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelVersionQualityComparisonService;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataExtractionResultInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeahorseAgentKernelMetadataAutoConfigurationTests {

    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelMetadataAutoConfiguration.class));
    }

    @Test
    void createsMetadataExtractionResultPortWithoutRepositoryAdapter() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KernelMetadataExtractionResultService.class);
            assertThat(context).hasSingleBean(MetadataExtractionResultInboundPort.class);

            MetadataExtractionResultInboundPort port = context.getBean(MetadataExtractionResultInboundPort.class);
            assertThat(port.page("default", "", "", "", "", 1, 10).total()).isZero();
        });
    }

    @Test
    void createsJdbcMetadataGovernanceAdapterWhenSchemaRegistryOverrideExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentMetadataAdapterAutoConfiguration.class))
                .withUserConfiguration(JdbcMetadataAdapterConfiguration.class, SchemaRegistryOverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcMetadataGovernanceRepositoryAdapter.class);
                    assertThat(context).hasBean("customMetadataSchemaRegistryPort");
                    assertThat(context).hasSingleBean(MetadataSchemaUsageReportRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataQualityReportRepositoryPort.class);
                });
    }

    @Test
    void createsSchemaUsageAndVersionQualityPortsWhenTheirDependenciesExist() {
        contextRunner.withUserConfiguration(MetadataQualityAndRetrievalConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelMetadataSchemaUsageService.class);
                    assertThat(context).hasSingleBean(MetadataSchemaUsageInboundPort.class);
                    assertThat(context).hasSingleBean(KernelVersionQualityComparisonService.class);
                    assertThat(context).hasSingleBean(VersionQualityComparisonInboundPort.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcMetadataAdapterConfiguration {

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SchemaRegistryOverrideConfiguration {

        @Bean
        MetadataSchemaRegistryPort customMetadataSchemaRegistryPort() {
            return MetadataSchemaRegistryPort.empty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MetadataQualityAndRetrievalConfiguration {

        @Bean
        MetadataQualityReportRepositoryPort metadataQualityReportRepositoryPort() {
            return MetadataQualityReportRepositoryPort.empty();
        }

        @Bean
        MetadataSchemaUsageReportRepositoryPort metadataSchemaUsageReportRepositoryPort() {
            return MetadataSchemaUsageReportRepositoryPort.empty();
        }

        @Bean
        RetrievalEvaluationInboundPort retrievalEvaluationInboundPort() {
            return new RetrievalEvaluationInboundPort() {
                @Override
                public RetrievalEvaluationReport evaluate(RetrievalEvaluationCommand command) {
                    return new RetrievalEvaluationReport("", 0, 0, 0, 0D, 0D, 0D,
                            0D, 0D, 0D, List.of());
                }

                @Override
                public RetrievalEvaluationComparisonReport compare(RetrievalEvaluationComparisonCommand command) {
                    return new RetrievalEvaluationComparisonReport("", "", List.of(), List.of());
                }
            };
        }
    }
}
