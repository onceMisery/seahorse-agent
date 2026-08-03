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

import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeDocumentChunkHandler;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeahorseAgentKernelDocumentRefreshAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelDocumentRefreshAutoConfiguration.class));

    @Test
    void shouldSkipChunkHandlerWhenDocumentProcessingDependenciesAreMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(KernelKnowledgeDocumentChunkHandler.class);
        });
    }

    @Test
    void shouldAssembleS3KnowledgeProcessingBeforeMessageSubscription() {
        MessageSubscriptionPort subscriptionPort = mock(MessageSubscriptionPort.class);
        when(subscriptionPort.subscribe(anyString(), anyString(), any(), any()))
                .thenReturn(() -> { });

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SeahorseAgentKernelAutoConfiguration.class,
                        SeahorseAgentS3StorageAutoConfiguration.class,
                        SeahorseAgentKernelKnowledgeAutoConfiguration.class,
                        SeahorseAgentKernelDocumentRefreshAutoConfiguration.class))
                .withPropertyValues("seahorse-agent.adapters.storage.type=s3")
                .withBean(S3Client.class, () -> mock(S3Client.class))
                .withBean(ExtensionRegistry.class, () -> mock(ExtensionRegistry.class))
                .withBean(FeatureActivationContext.class, () -> mock(FeatureActivationContext.class))
                .withBean(KnowledgeBaseQueryPort.class, () -> mock(KnowledgeBaseQueryPort.class))
                .withBean(KnowledgeDocumentRepositoryPort.class,
                        () -> mock(KnowledgeDocumentRepositoryPort.class))
                .withBean(PipelineDefinitionRepositoryPort.class,
                        () -> mock(PipelineDefinitionRepositoryPort.class))
                .withBean(MessageSubscriptionPort.class, () -> subscriptionPort)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeDocumentInboundPort.class);
                    assertThat(context).hasSingleBean(KernelKnowledgeDocumentChunkHandler.class);
                    assertThat(context).hasBean("seahorseKnowledgeDocumentChunkSubscription");
                });
    }
}
