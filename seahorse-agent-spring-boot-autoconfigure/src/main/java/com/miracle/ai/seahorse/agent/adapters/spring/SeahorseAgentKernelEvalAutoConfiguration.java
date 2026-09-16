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
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalDecisionFacade;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService;
import com.miracle.ai.seahorse.agent.adapters.web.RateLimitFilter;
import com.miracle.ai.seahorse.agent.adapters.spring.properties.LearningLoopProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.properties.RoutingProperties;
import com.miracle.ai.seahorse.agent.kernel.application.agent.routing.ModelRoutingPolicy;
import com.miracle.ai.seahorse.agent.kernel.application.conversation.ConversationAttachmentParserService;
import com.miracle.ai.seahorse.agent.kernel.application.learning.KernelLearningLoopService;
import com.miracle.ai.seahorse.agent.kernel.application.learning.KernelLearningLoopRunner;
import com.miracle.ai.seahorse.agent.kernel.application.learning.KernelPolicyAdjustmentTicketService;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidateQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketRepositoryPort;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
@EnableConfigurationProperties({RoutingProperties.class, LearningLoopProperties.class})
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentKernelEvalAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ModelRoutingPolicy seahorseModelRoutingPolicy(
            RoutingProperties routingProperties,
            Environment environment,
            ObjectProvider<ModelProviderPort> modelProviderPort,
            ObjectProvider<RateLimiterPort> rateLimiterPort) {
        RoutingProperties resolvedRoutingProperties = Binder.get(environment)
                .bind("seahorse-agent.routing", Bindable.of(RoutingProperties.class))
                .orElse(routingProperties);
        return new ModelRoutingPolicy(
                resolvedRoutingProperties.toKernelProperties(),
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
    @ConditionalOnBean({KernelEvalCandidateDecisionService.class, KernelEvalRegressionService.class})
    @ConditionalOnMissingBean
    public KernelEvalDecisionFacade seahorseEvalDecisionFacade(
            KernelEvalCandidateDecisionService decisionService,
            KernelEvalRegressionService regressionService) {
        return new KernelEvalDecisionFacade(decisionService, regressionService);
    }

    /**
     * 自学习闭环（P3-2）。刻意没有配套的"自动执行器"：本服务只产出带证据的调整建议，
     * 落地与否由人决定，避免无人值守地改动生产阈值。
     */
    @Bean
    @ConditionalOnMissingBean
    public KernelLearningLoopService seahorseKernelLearningLoopService(LearningLoopProperties properties) {
        return new KernelLearningLoopService(properties == null ? null : properties.toPolicy());
    }

    /**
     * 落地工单（P3-2 配套）：给闭环的建议补上身份、审批与回访能力。
     *
     * <p>两个出站端口都用 {@code ObjectProvider} + {@code empty()} 兜底——
     * 没有配置持久化时工单仍然能在内存里流转，只是不落库，不会让整个装配失败。
     */
    @Bean
    @ConditionalOnMissingBean
    public PolicyAdjustmentTicketManagementInboundPort seahorsePolicyAdjustmentTicketService(
            ObjectProvider<PolicyAdjustmentTicketRepositoryPort> repository,
            ObjectProvider<PolicyAdjustmentTicketQueryPort> queryPort,
            ObjectProvider<FeedbackEvaluationCandidateQueryPort> feedbackQuery,
            ObjectProvider<MemoryReviewFeedbackRepositoryPort> reviewRepository,
            LearningLoopProperties properties) {
        // 闭环取样端口同样是可选的：没接数据源时"生成工单"返回空报告而不是启动失败。
        FeedbackEvaluationCandidateQueryPort safeFeedback =
                feedbackQuery.getIfAvailable(FeedbackEvaluationCandidateQueryPort::empty);
        MemoryReviewFeedbackRepositoryPort safeReviews =
                reviewRepository.getIfAvailable(MemoryReviewFeedbackRepositoryPort::empty);
        KernelLearningLoopRunner runner = new KernelLearningLoopRunner(
                properties == null ? null : properties.toPolicy(), safeFeedback, safeReviews);
        return new KernelPolicyAdjustmentTicketService(
                repository.getIfAvailable(PolicyAdjustmentTicketRepositoryPort::empty),
                queryPort.getIfAvailable(PolicyAdjustmentTicketQueryPort::empty),
                java.time.Clock.systemUTC(),
                runner);
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
