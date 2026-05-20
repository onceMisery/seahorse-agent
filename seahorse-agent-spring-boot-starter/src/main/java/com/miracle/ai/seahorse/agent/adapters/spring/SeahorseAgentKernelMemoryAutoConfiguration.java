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
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryEnginePort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRetrievalPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRouter;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryEngine;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.InMemoryMemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryDecayOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryEngineOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryGovernanceServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryManagementServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryOutboxRelayService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.RuleBasedMemoryCandidateExtractor;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryMaintenancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内核记忆能力自动配置。
 *
 * <p>四层记忆引擎、管理服务、治理服务和治理调度属于同一内核职责域，独立配置后主 kernel 配置不再承载记忆闭环细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentKernelAutoConfiguration.class, SeahorseAgentMemoryRepositoryAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelMemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryPolicyConfigPort.class)
    public InMemoryMemoryPolicyConfigPort seahorseMemoryPolicyConfigPort(
            @Value("${seahorse-agent.memory.policy.capture-accept-threshold:0.4}") double captureAcceptThreshold,
            @Value("${seahorse-agent.memory.policy.high-value-threshold:0.75}") double highValueThreshold,
            @Value("${seahorse-agent.memory.policy.risk-reject-threshold:0.7}") double riskRejectThreshold,
            @Value("${seahorse-agent.memory.policy.token-budget:2400}") int tokenBudget,
            @Value("${seahorse-agent.memory.policy.review-enabled:false}") boolean reviewEnabled,
            @Value("${seahorse-agent.memory.policy.schema-failure-alert-threshold:0}") int schemaFailureAlertThreshold,
            @Value("${seahorse-agent.memory.policy.outbox-backlog-alert-threshold:0}") int outboxBacklogAlertThreshold,
            @Value("${seahorse-agent.memory.policy.grey-release-key:}") String greyReleaseKey) {
        return new InMemoryMemoryPolicyConfigPort(new MemoryPolicyConfig(
                captureAcceptThreshold,
                highValueThreshold,
                riskRejectThreshold,
                tokenBudget,
                reviewEnabled,
                MemoryPolicyConfig.defaults().enabledTracks(),
                schemaFailureAlertThreshold,
                outboxBacklogAlertThreshold,
                greyReleaseKey));
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnMissingBean(MemoryEnginePort.class)
    public DefaultMemoryEnginePort seahorseDefaultMemoryEnginePort(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<ProfileMemoryPort> profileMemoryPort,
            ObjectProvider<CorrectionLedgerPort> correctionLedgerPort,
            ObjectProvider<MemoryRouterPort> memoryRouterPort,
            ObjectProvider<MemoryOperationLogPort> memoryOperationLogPort,
            ObjectProvider<MemoryVectorPort> memoryVectorPort,
            ObjectProvider<MemoryOutboxPort> memoryOutboxPort,
            ObjectProvider<MemoryBusinessDocumentRetrieverPort> businessDocumentRetrieverPort,
            ObjectProvider<MemoryLifecyclePort> memoryLifecyclePort,
            ObjectProvider<MemoryPolicyConfigPort> memoryPolicyConfigPort,
            ObjectProvider<MemoryRetrievalPipelinePort> memoryRetrievalPipelinePort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            @Value("${seahorse-agent.memory.short-term-limit:5}") int shortTermLimit,
            @Value("${seahorse-agent.memory.long-term-limit:3}") int longTermLimit,
            @Value("${seahorse-agent.memory.semantic-limit:10}") int semanticLimit,
            @Value("${seahorse-agent.memory.capture-enabled:true}") boolean captureEnabled) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        MemoryEngineOptions options = new MemoryEngineOptions(
                shortTermLimit,
                longTermLimit,
                semanticLimit,
                captureEnabled);
        return new DefaultMemoryEnginePort(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                objectMapper,
                options,
                profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop),
                correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop),
                memoryRouterPort.getIfAvailable(DefaultMemoryRouter::new),
                memoryOperationLogPort.getIfAvailable(MemoryOperationLogPort::noop),
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                memoryOutboxPort.getIfAvailable(MemoryOutboxPort::noop),
                businessDocumentRetrieverPort.getIfAvailable(MemoryBusinessDocumentRetrieverPort::noop),
                memoryLifecyclePort.getIfAvailable(MemoryLifecyclePort::noop),
                memoryPolicyConfigPort.getIfAvailable(MemoryPolicyConfigPort::defaults),
                memoryRetrievalPipelinePort.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(MemoryRouterPort.class)
    public DefaultMemoryRouter seahorseDefaultMemoryRouter() {
        return new DefaultMemoryRouter();
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnMissingBean(MemoryRetrievalPipelinePort.class)
    public DefaultMemoryRetrievalPipeline seahorseMemoryRetrievalPipeline(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<ProfileMemoryPort> profileMemoryPort,
            ObjectProvider<CorrectionLedgerPort> correctionLedgerPort,
            ObjectProvider<MemoryRouterPort> memoryRouterPort,
            ObjectProvider<MemoryVectorPort> memoryVectorPort,
            ObjectProvider<MemoryBusinessDocumentRetrieverPort> businessDocumentRetrieverPort,
            ObjectProvider<MemoryLifecyclePort> memoryLifecyclePort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            @Value("${seahorse-agent.memory.short-term-limit:5}") int shortTermLimit,
            @Value("${seahorse-agent.memory.long-term-limit:3}") int longTermLimit,
            @Value("${seahorse-agent.memory.semantic-limit:10}") int semanticLimit,
            @Value("${seahorse-agent.memory.capture-enabled:true}") boolean captureEnabled) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        MemoryEngineOptions options = new MemoryEngineOptions(
                shortTermLimit,
                longTermLimit,
                semanticLimit,
                captureEnabled);
        return new DefaultMemoryRetrievalPipeline(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                objectMapper,
                options,
                profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop),
                correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop),
                memoryRouterPort.getIfAvailable(DefaultMemoryRouter::new),
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                businessDocumentRetrieverPort.getIfAvailable(MemoryBusinessDocumentRetrieverPort::noop),
                memoryLifecyclePort.getIfAvailable(MemoryLifecyclePort::noop));
    }

    @Bean
    @ConditionalOnMissingBean(ContextWeaverPort.class)
    public DefaultContextWeaver seahorseDefaultContextWeaver() {
        return new DefaultContextWeaver();
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean
    public MemoryOutboxRelayService seahorseMemoryOutboxRelayService(
            MemoryOutboxPort memoryOutboxPort,
            ObjectProvider<MemoryVectorPort> memoryVectorPort) {
        return new MemoryOutboxRelayService(
                memoryOutboxPort,
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxRelayService.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.outbox", name = "relay-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SeahorseMemoryOutboxRelayJob seahorseMemoryOutboxRelayJob(
            MemoryOutboxRelayService relayService,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse-agent.memory.outbox.relay-batch-size:50}") int batchSize) {
        return new SeahorseMemoryOutboxRelayJob(
                relayService,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                batchSize);
    }

    @Bean
    @ConditionalOnBean(MemoryEnginePort.class)
    @ConditionalOnMissingBean(MemoryIngestionWorkflowPort.class)
    public MemoryIngestionWorkflowPort seahorseMemoryIngestionWorkflowPort(MemoryEnginePort memoryEnginePort) {
        return command -> {
            if (memoryEnginePort instanceof MemoryIngestionWorkflowPort workflowPort) {
                return workflowPort.ingest(command);
            }
            memoryEnginePort.writeMemory(command == null ? null : command.writeRequest());
            return com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult.ignored(
                    "delegated_to_memory_engine");
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelMemoryEngine seahorseKernelMemoryEngine(ObjectProvider<MemoryEnginePort> memoryEnginePort) {
        return new KernelMemoryEngine(memoryEnginePort.getIfAvailable(MemoryEnginePort::noop));
    }

    @Bean
    @ConditionalOnBean({WorkingMemoryPort.class, ShortTermMemoryPort.class, LongTermMemoryPort.class,
            SemanticMemoryPort.class})
    @ConditionalOnMissingBean
    public MemoryManagementServicePorts seahorseMemoryManagementServicePorts(
            WorkingMemoryPort workingMemoryPort,
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<MemoryQualitySnapshotRepositoryPort> qualitySnapshotRepositoryPort,
            ObjectProvider<MemoryConflictLogRepositoryPort> conflictLogRepositoryPort,
            ObjectProvider<ProfileMemoryPort> profileMemoryPort,
            ObjectProvider<CorrectionLedgerPort> correctionLedgerPort,
            ObjectProvider<MemoryOperationLogPort> operationLogPort,
            ObjectProvider<MemoryOutboxPort> outboxPort,
            ObjectProvider<MemoryPolicyConfigPort> policyConfigPort) {
        return new MemoryManagementServicePorts(
                workingMemoryPort,
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                qualitySnapshotRepositoryPort.getIfAvailable(MemoryQualitySnapshotRepositoryPort::empty),
                conflictLogRepositoryPort.getIfAvailable(MemoryConflictLogRepositoryPort::empty),
                profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop),
                correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop),
                operationLogPort.getIfAvailable(MemoryOperationLogPort::noop),
                outboxPort.getIfAvailable(MemoryOutboxPort::noop),
                policyConfigPort.getIfAvailable(MemoryPolicyConfigPort::defaults));
    }

    @Bean
    @ConditionalOnBean(MemoryManagementServicePorts.class)
    @ConditionalOnMissingBean(MemoryManagementInboundPort.class)
    public KernelMemoryManagementService seahorseMemoryManagementInboundPort(
            MemoryManagementServicePorts servicePorts) {
        return new KernelMemoryManagementService(servicePorts);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryInferencePort.class)
    public MemoryInferencePort seahorseRuleBasedMemoryCandidateExtractor() {
        return new RuleBasedMemoryCandidateExtractor();
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnMissingBean
    public MemoryGovernanceServicePorts seahorseMemoryGovernanceServicePorts(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<MemoryEnginePort> memoryEnginePort,
            ObjectProvider<MemoryInferencePort> memoryInferencePort,
            ObjectProvider<ShortTermMemoryMaintenancePort> shortTermMemoryMaintenancePort,
            ObjectProvider<MemoryQualitySnapshotRepositoryPort> qualitySnapshotRepositoryPort,
            ObjectProvider<MemoryConflictLogRepositoryPort> conflictLogRepositoryPort) {
        return new MemoryGovernanceServicePorts(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                memoryEnginePort.getIfAvailable(MemoryEnginePort::noop),
                memoryInferencePort.getIfAvailable(MemoryInferencePort::noop),
                shortTermMemoryMaintenancePort.getIfAvailable(ShortTermMemoryMaintenancePort::noop),
                qualitySnapshotRepositoryPort.getIfAvailable(MemoryQualitySnapshotRepositoryPort::empty),
                conflictLogRepositoryPort.getIfAvailable(MemoryConflictLogRepositoryPort::empty));
    }

    @Bean
    @ConditionalOnBean(MemoryGovernanceServicePorts.class)
    @ConditionalOnMissingBean(MemoryGovernanceInboundPort.class)
    public KernelMemoryGovernanceService seahorseMemoryGovernanceInboundPort(
            MemoryGovernanceServicePorts servicePorts,
            @Value("${seahorse-agent.memory.long-term-importance-threshold:0.6}") double promotionThreshold,
            @Value("${seahorse-agent.memory.inference-enabled:false}") boolean inferenceEnabled,
            @Value("${seahorse-agent.memory.decay.scan-limit:500}") int decayScanLimit,
            @Value("${seahorse-agent.memory.decay.threshold:0.1}") double decayThreshold,
            @Value("${seahorse-agent.memory.decay.dry-run:false}") boolean decayDryRun) {
        return new KernelMemoryGovernanceService(servicePorts, promotionThreshold, inferenceEnabled,
                new MemoryDecayOptions(decayScanLimit, decayThreshold, decayDryRun));
    }

    @Bean
    @ConditionalOnBean(MemoryGovernanceInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.governance", name = "scheduler-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SeahorseMemoryGovernanceJob seahorseMemoryGovernanceJob(
            MemoryGovernanceInboundPort governanceInboundPort,
            ObjectProvider<DistributedLockPort> lockPort) {
        return new SeahorseMemoryGovernanceJob(governanceInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop));
    }
}
