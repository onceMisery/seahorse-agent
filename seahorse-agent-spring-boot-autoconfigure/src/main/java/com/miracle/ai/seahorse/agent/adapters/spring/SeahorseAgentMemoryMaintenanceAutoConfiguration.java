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

import com.miracle.ai.seahorse.agent.adapters.spring.properties.MemoryProperties;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.DefaultMemoryMaintenanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryAliasResolutionOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryAliasResolutionService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryCompactionOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryCompactionService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryGarbageCollectionOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryGarbageCollectionService;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummarizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spec §9.2：memory maintenance 子能力域 auto configuration。
 *
 * <p>从原 {@link SeahorseAgentKernelMemoryAutoConfiguration} 拆出 6 个 maintenance 相关 bean，
 * 聚焦"长期记忆健康"能力域：compaction / garbage collection / alias resolution / 总控
 * inbound 服务 / 定时 job。
 *
 * <p>alias dictionary 反序列化辅助方法（{@code memoryAliasDictionary} / {@code toCandidate} /
 * {@code hasText}）随 alias bean 一起迁入此 sub-config，避免 KernelMemory 主配置承担
 * maintenance 子域的实现细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(SeahorseAgentKernelMemoryAutoConfiguration.class)
@EnableConfigurationProperties(MemoryProperties.class)
public class SeahorseAgentMemoryMaintenanceAutoConfiguration {

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean
    public MemoryCompactionService seahorseMemoryCompactionService(
            ObjectProvider<MemoryCompactionPort> compactionPort,
            ObjectProvider<LongTermMemoryPort> longTermMemoryPort,
            MemoryOutboxPort outboxPort,
            ObjectProvider<MemoryCompactionSummarizerPort> summarizerPort,
            ObjectProvider<MemoryKeywordIndexPort> keywordIndexPort,
            ObjectProvider<MemoryGraphIndexPort> graphIndexPort,
            ObjectProvider<ObservationPort> observationPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Maintenance.Compaction compaction = memoryProperties.getMaintenance().getCompaction();
        return new MemoryCompactionService(
                compactionPort.getIfAvailable(MemoryCompactionPort::noop),
                longTermMemoryPort.getIfAvailable(),
                outboxPort,
                summarizerPort.getIfAvailable(MemoryCompactionSummarizerPort::noop),
                new MemoryCompactionOptions(
                        compaction.getScanLimit(),
                        compaction.getMinGroupSize(),
                        compaction.isVectorIndexEnabled(),
                        compaction.isKeywordIndexEnabled() && keywordIndexPort.getIfAvailable() != null,
                        compaction.isGraphIndexEnabled() && graphIndexPort.getIfAvailable() != null,
                        compaction.getEmbeddingModel()),
                observationPort.getIfAvailable(ObservationPort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean
    public MemoryGarbageCollectionService seahorseMemoryGarbageCollectionService(
            ObjectProvider<MemoryGarbageCollectionPort> garbageCollectionPort,
            MemoryOutboxPort outboxPort,
            ObjectProvider<MemoryKeywordIndexPort> keywordIndexPort,
            ObjectProvider<MemoryGraphIndexPort> graphIndexPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Maintenance.Gc gc = memoryProperties.getMaintenance().getGc();
        return new MemoryGarbageCollectionService(
                garbageCollectionPort.getIfAvailable(MemoryGarbageCollectionPort::noop),
                outboxPort,
                new MemoryGarbageCollectionOptions(
                        gc.getScanLimit(),
                        Duration.ofDays(Math.max(0L, gc.getRetentionDays())),
                        gc.isDryRun(),
                        gc.isVectorIndexEnabled(),
                        gc.isKeywordIndexEnabled() && keywordIndexPort.getIfAvailable() != null,
                        gc.isGraphIndexEnabled() && graphIndexPort.getIfAvailable() != null,
                        gc.isArchiveEnabled(),
                        Duration.ofDays(Math.max(0L, gc.getArchiveIdleDays())),
                        gc.getArchiveScoreThreshold(),
                        gc.isPhysicalDeleteEnabled(),
                        Duration.ofDays(Math.max(0L, gc.getPhysicalDeleteRetentionDays()))));
    }

    @Bean
    @ConditionalOnBean(MemoryAliasPort.class)
    @ConditionalOnMissingBean(MemoryAliasResolutionService.class)
    public MemoryAliasResolutionService seahorseMemoryAliasResolutionService(
            MemoryAliasPort aliasPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.AliasResolution aliasResolution = memoryProperties.getAliasResolution();
        return new MemoryAliasResolutionService(
                aliasPort,
                new MemoryAliasResolutionOptions(
                        aliasResolution.getScanLimit(),
                        "",
                        "default",
                        aliasResolution.getAutoResolveConfidenceThreshold(),
                        memoryAliasDictionary(aliasResolution)));
    }

    private static Map<String, MemoryAliasCandidate> memoryAliasDictionary(
            MemoryProperties.AliasResolution aliasResolution) {
        Map<String, MemoryAliasCandidate> dictionary = new LinkedHashMap<>();
        aliasResolution.getDictionary().forEach((aliasText, entry) ->
                dictionary.put(aliasText, toCandidate(aliasText, entry)));
        return dictionary;
    }

    private static MemoryAliasCandidate toCandidate(String dictionaryAliasText,
            MemoryProperties.AliasResolution.DictionaryEntry entry) {
        String candidateAliasText = hasText(entry.getAliasText()) ? entry.getAliasText() : dictionaryAliasText;
        return new MemoryAliasCandidate(
                entry.getUserId(),
                entry.getTenantId(),
                candidateAliasText,
                entry.getCanonicalEntityId(),
                entry.getCanonicalName(),
                entry.getEntityType(),
                entry.getConfidenceLevel());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Bean
    @ConditionalOnBean(MemoryGarbageCollectionService.class)
    @ConditionalOnMissingBean(MemoryMaintenanceInboundPort.class)
    public DefaultMemoryMaintenanceService seahorseMemoryMaintenanceInboundPort(
            MemoryGarbageCollectionService garbageCollectionService,
            ObjectProvider<MemoryCompactionService> compactionService,
            ObjectProvider<MemoryAliasResolutionService> aliasResolutionService,
            ObjectProvider<MemoryMaintenanceRunRepositoryPort> maintenanceRunRepositoryPort,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<ObservationPort> observationPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Maintenance maintenance = memoryProperties.getMaintenance();
        return new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                compactionService.getIfAvailable(),
                aliasResolutionService.getIfAvailable(),
                maintenanceRunRepositoryPort.getIfAvailable(MemoryMaintenanceRunRepositoryPort::noop),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                observationPort.getIfAvailable(ObservationPort::noop),
                maintenance.isCompactionEnabled(),
                maintenance.isAliasEnabled(),
                maintenance.isGcEnabled());
    }

    @Bean
    @ConditionalOnBean(MemoryMaintenanceInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.maintenance", name = "scheduler-enabled",
            havingValue = "true")
    @ConditionalOnMissingBean
    public SeahorseMemoryMaintenanceJob seahorseMemoryMaintenanceJob(
            MemoryMaintenanceInboundPort maintenanceInboundPort,
            ObjectProvider<DistributedLockPort> lockPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Maintenance maintenance = memoryProperties.getMaintenance();
        return new SeahorseMemoryMaintenanceJob(
                maintenanceInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                maintenance.isCompactionEnabled(),
                maintenance.isAliasEnabled(),
                maintenance.isGcEnabled());
    }

    @Bean
    @ConditionalOnBean(MemoryGarbageCollectionService.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.gc", name = "scheduler-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("!${seahorse-agent.memory.maintenance.scheduler-enabled:false}"
            + " || !${seahorse-agent.memory.maintenance.gc-enabled:true}")
    @ConditionalOnMissingBean
    public SeahorseMemoryGarbageCollectionJob seahorseMemoryGarbageCollectionJob(
            MemoryGarbageCollectionService garbageCollectionService,
            ObjectProvider<DistributedLockPort> lockPort) {
        return new SeahorseMemoryGarbageCollectionJob(
                garbageCollectionService,
                lockPort.getIfAvailable(DistributedLockPort::noop));
    }
}
