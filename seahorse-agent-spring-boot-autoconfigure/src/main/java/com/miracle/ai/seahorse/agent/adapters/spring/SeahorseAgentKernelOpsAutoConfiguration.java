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

import com.miracle.ai.seahorse.agent.kernel.application.conversation.KernelConversationBranchService;
import com.miracle.ai.seahorse.agent.kernel.application.conversation.KernelConversationAttachmentService;
import com.miracle.ai.seahorse.agent.kernel.application.conversation.KernelConversationManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.conversation.MessageTreeAssembler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorRouter;
import com.miracle.ai.seahorse.agent.kernel.application.dashboard.KernelDashboardService;
import com.miracle.ai.seahorse.agent.kernel.application.feedback.KernelFeedbackEvaluationCandidateQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.feedback.KernelMessageFeedbackService;
import com.miracle.ai.seahorse.agent.kernel.application.intent.KernelIntentTreeService;
import com.miracle.ai.seahorse.agent.kernel.application.mapping.KernelQueryTermMappingService;
import com.miracle.ai.seahorse.agent.kernel.application.rolecard.KernelRoleCardService;
import com.miracle.ai.seahorse.agent.kernel.application.runcontext.KernelRunContextSnapshotService;
import com.miracle.ai.seahorse.agent.kernel.application.runexperiment.KernelRunExperimentService;
import com.miracle.ai.seahorse.agent.kernel.application.runexperiment.KernelRunExperimentTrialExecutor;
import com.miracle.ai.seahorse.agent.kernel.application.runprofile.KernelRunProfileService;
import com.miracle.ai.seahorse.agent.kernel.application.sample.KernelSampleQuestionService;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationBranchInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationAttachmentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.dashboard.DashboardInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.mapping.QueryTermMappingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runcontext.RunContextSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidateQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardGuardrailPort;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 运营管理类内核入口自动配置。
 *
 * <p>该配置只承载后台运营和治理页面使用的轻量入站服务，避免这些低耦合管理能力继续堆在主 kernel 装配类中。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentKernelAutoConfiguration.class, SeahorseAgentOpsRepositoryAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelOpsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MessageFeedbackRepositoryPort.class)
    @ConditionalOnMissingBean(MessageFeedbackInboundPort.class)
    public KernelMessageFeedbackService seahorseMessageFeedbackInboundPort(
            MessageFeedbackRepositoryPort feedbackRepositoryPort) {
        return new KernelMessageFeedbackService(feedbackRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({FeedbackEvaluationCandidateQueryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(FeedbackEvaluationCandidateQueryInboundPort.class)
    public KernelFeedbackEvaluationCandidateQueryService seahorseFeedbackEvaluationCandidateQueryInboundPort(
            FeedbackEvaluationCandidateQueryPort candidateQueryPort,
            CurrentUserPort currentUserPort) {
        return new KernelFeedbackEvaluationCandidateQueryService(candidateQueryPort, currentUserPort);
    }

    @Bean
    @ConditionalOnBean(ConversationRepositoryPort.class)
    @ConditionalOnMissingBean(ConversationManagementInboundPort.class)
    public KernelConversationManagementService seahorseConversationManagementInboundPort(
            ConversationRepositoryPort conversationRepositoryPort) {
        return new KernelConversationManagementService(conversationRepositoryPort);
    }

    @Bean
    @ConditionalOnMissingBean(MessageTreeAssembler.class)
    public MessageTreeAssembler seahorseMessageTreeAssembler() {
        return new MessageTreeAssembler();
    }

    @Bean
    @ConditionalOnBean({ConversationBranchRepositoryPort.class, MessageTreeAssembler.class})
    @ConditionalOnMissingBean(ConversationBranchInboundPort.class)
    public KernelConversationBranchService seahorseConversationBranchInboundPort(
            ConversationBranchRepositoryPort conversationBranchRepositoryPort,
            MessageTreeAssembler messageTreeAssembler) {
        return new KernelConversationBranchService(conversationBranchRepositoryPort, messageTreeAssembler);
    }

    @Bean
    @ConditionalOnBean(RoleCardRepositoryPort.class)
    @ConditionalOnMissingBean(RoleCardInboundPort.class)
    public KernelRoleCardService seahorseRoleCardInboundPort(
            RoleCardRepositoryPort roleCardRepositoryPort,
            ObjectProvider<RoleCardGuardrailPort> guardrailPort) {
        return new KernelRoleCardService(
                roleCardRepositoryPort,
                guardrailPort.getIfAvailable(RoleCardGuardrailPort::noop));
    }

    @Bean
    @ConditionalOnBean(RunContextSnapshotRepositoryPort.class)
    @ConditionalOnMissingBean(RunContextSnapshotInboundPort.class)
    public KernelRunContextSnapshotService seahorseRunContextSnapshotInboundPort(
            RunContextSnapshotRepositoryPort repositoryPort) {
        return new KernelRunContextSnapshotService(repositoryPort);
    }

    @Bean
    @ConditionalOnBean(RunProfileRepositoryPort.class)
    @ConditionalOnMissingBean(RunProfileInboundPort.class)
    public KernelRunProfileService seahorseRunProfileInboundPort(
            RunProfileRepositoryPort repositoryPort,
            ObjectProvider<ReActExecutorPort> executorPorts) {
        return new KernelRunProfileService(repositoryPort, supportedExecutorEngines(executorPorts));
    }

    @Bean
    @ConditionalOnBean(RunExperimentRepositoryPort.class)
    @ConditionalOnMissingBean(RunExperimentInboundPort.class)
    public KernelRunExperimentService seahorseRunExperimentInboundPort(
            RunExperimentRepositoryPort repositoryPort,
            ObjectProvider<RunExperimentTrialExecutorPort> trialExecutorPortProvider) {
        return new KernelRunExperimentService(
                repositoryPort,
                trialExecutorPortProvider.getIfAvailable(RunExperimentTrialExecutorPort::noop));
    }

    @Bean
    @ConditionalOnBean({
            ReActExecutorPort.class,
            ConversationBranchRepositoryPort.class,
            RunProfileRepositoryPort.class
    })
    @ConditionalOnMissingBean(RunExperimentTrialExecutorPort.class)
    public KernelRunExperimentTrialExecutor seahorseRunExperimentTrialExecutor(
            ObjectProvider<ReActExecutorPort> executorPorts,
            ConversationBranchRepositoryPort conversationBranchRepositoryPort,
            RunProfileRepositoryPort runProfileRepositoryPort,
            ObjectProvider<RunContextSnapshotRepositoryPort> runContextSnapshotRepositoryPort,
            Environment environment) {
        return new KernelRunExperimentTrialExecutor(
                resolveReActExecutor(executorPorts, environment),
                conversationBranchRepositoryPort,
                runProfileRepositoryPort,
                runContextSnapshotRepositoryPort.getIfAvailable(RunContextSnapshotRepositoryPort::noop));
    }

    @Bean
    @ConditionalOnBean({ConversationAttachmentRepositoryPort.class, ObjectStoragePort.class})
    @ConditionalOnMissingBean(ConversationAttachmentInboundPort.class)
    public KernelConversationAttachmentService seahorseConversationAttachmentInboundPort(
            ConversationAttachmentRepositoryPort attachmentRepositoryPort,
            ObjectStoragePort objectStoragePort) {
        return new KernelConversationAttachmentService(attachmentRepositoryPort, objectStoragePort);
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

    private static ReActExecutorPort resolveReActExecutor(
            ObjectProvider<ReActExecutorPort> executors,
            Environment environment) {
        List<ReActExecutorPort> candidates = executors.orderedStream()
                .filter(executor -> !(executor instanceof ReActExecutorRouter))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Run experiment requires at least one ReActExecutorPort");
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return new ReActExecutorRouter(candidates, environment.getProperty("seahorse.agent.executor.engine", "kernel"));
    }

    private static Set<String> supportedExecutorEngines(ObjectProvider<ReActExecutorPort> executors) {
        Set<String> engines = executors.orderedStream()
                .filter(executor -> !(executor instanceof ReActExecutorRouter))
                .map(ReActExecutorPort::engineId)
                .filter(engine -> engine != null && !engine.isBlank())
                .map(engine -> engine.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return engines.isEmpty() ? Set.of("kernel") : engines;
    }
}
