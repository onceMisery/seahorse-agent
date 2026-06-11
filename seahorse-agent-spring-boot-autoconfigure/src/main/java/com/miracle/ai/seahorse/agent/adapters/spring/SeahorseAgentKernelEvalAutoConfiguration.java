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

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalCandidateRepositoryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalDatasetQueryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalCandidateDecisionService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService;
import com.miracle.ai.seahorse.agent.adapters.web.RateLimitFilter;
import com.miracle.ai.seahorse.agent.adapters.spring.properties.RoutingProperties;
import com.miracle.ai.seahorse.agent.kernel.application.agent.routing.ModelRoutingPolicy;
import com.miracle.ai.seahorse.agent.kernel.application.conversation.ConversationAttachmentParserService;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 评测、附件解析与模型路由自动配置。
 *
 * <p>注册 ModelRoutingPolicy、ConversationAttachmentParserService、
 * KernelEvalCandidateDecisionService 和 KernelEvalRegressionService。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentKernelAutoConfiguration.class,
        SeahorseAgentKernelChatAutoConfiguration.class,
        SeahorseAgentAiAdapterAutoConfiguration.class
})
@EnableConfigurationProperties(RoutingProperties.class)
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelEvalAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ModelRoutingPolicy seahorseModelRoutingPolicy(
            RoutingProperties routingProperties,
            ObjectProvider<ModelProviderPort> modelProviderPort,
            ObjectProvider<RateLimiterPort> rateLimiterPort) {
        return new ModelRoutingPolicy(
                routingProperties.toKernelProperties(),
                modelProviderPort.getIfAvailable(),
                rateLimiterPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean({ConversationAttachmentRepositoryPort.class, ObjectStoragePort.class})
    @ConditionalOnMissingBean
    public ConversationAttachmentParserService seahorseConversationAttachmentParserService(
            ConversationAttachmentRepositoryPort repositoryPort,
            ObjectStoragePort objectStoragePort,
            ObjectProvider<DocumentParserPort> documentParserPort) {
        return new ConversationAttachmentParserService(
                repositoryPort,
                objectStoragePort,
                documentParserPort.getIfAvailable(DocumentParserPort::plainText));
    }

    @Bean
    @ConditionalOnBean({EvalCandidateRepositoryPort.class, EvalDatasetRepositoryPort.class})
    @ConditionalOnMissingBean
    public KernelEvalCandidateDecisionService seahorseEvalCandidateDecisionService(
            EvalCandidateRepositoryPort candidateRepository,
            EvalDatasetRepositoryPort datasetRepository) {
        return new KernelEvalCandidateDecisionService(candidateRepository, datasetRepository);
    }

    @Bean
    @ConditionalOnBean({EvalDatasetQueryPort.class, ChatModelPort.class})
    @ConditionalOnMissingBean
    public KernelEvalRegressionService seahorseEvalRegressionService(
            EvalDatasetQueryPort datasetQueryPort,
            ChatModelPort chatModel) {
        return new KernelEvalRegressionService(datasetQueryPort, chatModel);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(RateLimitFilter.class)
    @ConditionalOnMissingBean(name = "seahorseRateLimitFilterRegistration")
    public FilterRegistrationBean<RateLimitFilter> seahorseRateLimitFilterRegistration(
            ObjectProvider<RateLimiterPort> rateLimiterPort) {
        RateLimitFilter filter = new RateLimitFilter(
                rateLimiterPort.getIfAvailable(RateLimiterPort::noop));
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        registration.setName("seahorseRateLimitFilter");
        return registration;
    }
}
