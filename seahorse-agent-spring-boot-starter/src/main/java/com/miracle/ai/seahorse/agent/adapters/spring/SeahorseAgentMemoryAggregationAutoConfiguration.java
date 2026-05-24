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
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.DefaultMemoryAggregationService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.ExplicitCueMemoryAggregationTopicShiftDetector;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.InMemoryMemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.KernelMemoryAggregationControlService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationTopicShiftDetector;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryAggregationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationSchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Spec §9.2：memory aggregation 子能力域 auto configuration。
 *
 * <p>从原 {@link SeahorseAgentKernelMemoryAutoConfiguration} 拆出 7 个 aggregation 相关 bean，
 * 聚焦"跨轮对话聚合 / topic shift 检测 / debounce flush"能力域。
 *
 * <p>aggregation 由 {@code seahorse-agent.memory.aggregation.enabled=true} 开启；关闭时
 * 仍会注册 policy 与 topic-shift detector（供其他子配置或外部代码引用），但 service / inbound /
 * job 不会装配，保持原历史行为完全不变。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(SeahorseAgentKernelMemoryAutoConfiguration.class)
@EnableConfigurationProperties(MemoryProperties.class)
public class SeahorseAgentMemoryAggregationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryAggregationPolicy.class)
    public MemoryAggregationPolicy seahorseMemoryAggregationPolicy(MemoryProperties properties) {
        MemoryProperties.Aggregation aggregation = properties.getAggregation();
        return new MemoryAggregationPolicy(
                aggregation.isEnabled(),
                aggregation.getIdleFlushMillis(),
                aggregation.getMaxTurns(),
                aggregation.getMaxTokens(),
                aggregation.getMaxContextBlocks(),
                aggregation.getBufferTtlMillis(),
                aggregation.isCaptureOnError(),
                aggregation.isTopicShiftFlushEnabled());
    }

    @Bean
    @ConditionalOnMissingBean(MemoryAggregationTopicShiftDetector.class)
    public ExplicitCueMemoryAggregationTopicShiftDetector seahorseMemoryAggregationTopicShiftDetector() {
        return new ExplicitCueMemoryAggregationTopicShiftDetector();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(MemoryAggregationBufferPort.class)
    public InMemoryMemoryAggregationBufferPort seahorseInMemoryAggregationBufferPort(
            MemoryAggregationPolicy policy) {
        return new InMemoryMemoryAggregationBufferPort(policy);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryAggregationSchedulerPort.class)
    public MemoryAggregationSchedulerPort seahorseMemoryAggregationSchedulerPort() {
        return MemoryAggregationSchedulerPort.noop();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(MemoryAggregationServicePort.class)
    public DefaultMemoryAggregationService seahorseMemoryAggregationService(
            MemoryAggregationPolicy policy,
            ObjectProvider<MemoryAggregationBufferPort> aggregationBufferPort,
            ObjectProvider<MemoryAggregationSchedulerPort> schedulerPort,
            ObjectProvider<MemoryIngestionWorkflowPort> ingestionWorkflowPort,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<MemoryAggregationTopicShiftDetector> topicShiftDetector,
            ObjectProvider<ObservationPort> observationPort) {
        return new DefaultMemoryAggregationService(
                policy,
                aggregationBufferPort.getIfAvailable(MemoryAggregationBufferPort::noop),
                schedulerPort.getIfAvailable(MemoryAggregationSchedulerPort::noop),
                ingestionWorkflowPort.getIfAvailable(() -> command -> MemoryIngestionResult.ignored("noop")),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                topicShiftDetector.getIfAvailable(ExplicitCueMemoryAggregationTopicShiftDetector::new),
                observationPort.getIfAvailable(ObservationPort::noop),
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnBean(MemoryAggregationServicePort.class)
    @ConditionalOnMissingBean(MemoryAggregationInboundPort.class)
    public KernelMemoryAggregationControlService seahorseMemoryAggregationInboundPort(
            MemoryAggregationServicePort aggregationServicePort) {
        return new KernelMemoryAggregationControlService(aggregationServicePort);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnBean(MemoryAggregationServicePort.class)
    @ConditionalOnMissingBean
    public SeahorseMemoryAggregationJob seahorseMemoryAggregationJob(
            MemoryAggregationServicePort aggregationServicePort,
            ObjectProvider<DistributedLockPort> lockPort,
            MemoryProperties memoryProperties) {
        return new SeahorseMemoryAggregationJob(
                aggregationServicePort,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                memoryProperties.getAggregation().getScanLimit());
    }
}
