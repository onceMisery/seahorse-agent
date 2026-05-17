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

import com.miracle.ai.seahorse.agent.kernel.application.conversation.KernelConversationManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.dashboard.KernelDashboardService;
import com.miracle.ai.seahorse.agent.kernel.application.feedback.KernelMessageFeedbackService;
import com.miracle.ai.seahorse.agent.kernel.application.intent.KernelIntentTreeService;
import com.miracle.ai.seahorse.agent.kernel.application.mapping.KernelQueryTermMappingService;
import com.miracle.ai.seahorse.agent.kernel.application.sample.KernelSampleQuestionService;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.dashboard.DashboardInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.mapping.QueryTermMappingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运营管理类内核入口自动配置。
 *
 * <p>该配置只承载后台运营和治理页面使用的轻量入站服务，避免这些低耦合管理能力继续堆在主 kernel 装配类中。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelOpsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MessageFeedbackRepositoryPort.class)
    @ConditionalOnMissingBean(MessageFeedbackInboundPort.class)
    public KernelMessageFeedbackService seahorseMessageFeedbackInboundPort(
            MessageFeedbackRepositoryPort feedbackRepositoryPort) {
        return new KernelMessageFeedbackService(feedbackRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(ConversationRepositoryPort.class)
    @ConditionalOnMissingBean(ConversationManagementInboundPort.class)
    public KernelConversationManagementService seahorseConversationManagementInboundPort(
            ConversationRepositoryPort conversationRepositoryPort) {
        return new KernelConversationManagementService(conversationRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(SampleQuestionRepositoryPort.class)
    @ConditionalOnMissingBean(SampleQuestionInboundPort.class)
    public KernelSampleQuestionService seahorseSampleQuestionInboundPort(
            SampleQuestionRepositoryPort sampleQuestionRepositoryPort) {
        return new KernelSampleQuestionService(sampleQuestionRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(DashboardRepositoryPort.class)
    @ConditionalOnMissingBean(DashboardInboundPort.class)
    public KernelDashboardService seahorseDashboardInboundPort(DashboardRepositoryPort dashboardRepositoryPort) {
        return new KernelDashboardService(dashboardRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(IntentTreeRepositoryPort.class)
    @ConditionalOnMissingBean(IntentTreeInboundPort.class)
    public KernelIntentTreeService seahorseIntentTreeInboundPort(
            IntentTreeRepositoryPort intentTreeRepositoryPort,
            ObjectProvider<KeyValueCachePort> cachePort) {
        return new KernelIntentTreeService(intentTreeRepositoryPort,
                cachePort.getIfAvailable(SeahorseAgentKernelOpsAutoConfiguration::noopCachePort));
    }

    @Bean
    @ConditionalOnBean(QueryTermMappingRepositoryPort.class)
    @ConditionalOnMissingBean(QueryTermMappingInboundPort.class)
    public KernelQueryTermMappingService seahorseQueryTermMappingInboundPort(
            QueryTermMappingRepositoryPort mappingRepositoryPort,
            ObjectProvider<KeyValueCachePort> cachePort) {
        return new KernelQueryTermMappingService(mappingRepositoryPort,
                cachePort.getIfAvailable(SeahorseAgentKernelOpsAutoConfiguration::noopCachePort));
    }

    private static KeyValueCachePort noopCachePort() {
        return new KeyValueCachePort() {
            @Override
            public Optional<String> get(String key) {
                return Optional.empty();
            }

            @Override
            public void set(String key, String value, Duration ttl) {
            }

            @Override
            public boolean delete(String key) {
                return false;
            }
        };
    }
}
