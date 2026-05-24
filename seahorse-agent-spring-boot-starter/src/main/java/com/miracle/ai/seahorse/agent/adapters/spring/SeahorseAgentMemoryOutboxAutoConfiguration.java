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
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryOutboxRelayService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.GraphMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.KeywordMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.VectorMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spec §9.2：memory outbox 子能力域 auto configuration。
 *
 * <p>从原 {@link SeahorseAgentKernelMemoryAutoConfiguration} 拆出 8 个 outbox 相关 bean，
 * 聚焦"派生索引异步写入"能力域：
 * <ul>
 *     <li>3 类 outbox task handler（vector / keyword / graph），各拆 upsert / delete。</li>
 *     <li>{@link MemoryOutboxRelayService} 负责按 handler 顺序 dispatch。</li>
 *     <li>{@link SeahorseMemoryOutboxRelayJob} 定时驱动 relay（由
 *         {@code seahorse-agent.memory.outbox.relay-enabled} 控制）。</li>
 * </ul>
 *
 * <p>调度顺序：依赖 main kernel memory 配置已加载，因此 {@code @AutoConfigureAfter}
 * {@link SeahorseAgentKernelMemoryAutoConfiguration}（保留同一 Layer 6 时序）。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(SeahorseAgentKernelMemoryAutoConfiguration.class)
@EnableConfigurationProperties(MemoryProperties.class)
public class SeahorseAgentMemoryOutboxAutoConfiguration {

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean(name = "seahorseVectorUpsertMemoryOutboxTaskHandler")
    public VectorMemoryOutboxTaskHandler seahorseVectorUpsertMemoryOutboxTaskHandler(
            ObjectProvider<MemoryVectorPort> memoryVectorPort) {
        return new VectorMemoryOutboxTaskHandler(
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                MemoryOutboxTaskTypes.VECTOR_UPSERT);
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean(name = "seahorseVectorDeleteMemoryOutboxTaskHandler")
    public VectorMemoryOutboxTaskHandler seahorseVectorDeleteMemoryOutboxTaskHandler(
            ObjectProvider<MemoryVectorPort> memoryVectorPort) {
        return new VectorMemoryOutboxTaskHandler(
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                MemoryOutboxTaskTypes.VECTOR_DELETE);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryKeywordIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseKeywordUpsertMemoryOutboxTaskHandler")
    public KeywordMemoryOutboxTaskHandler seahorseKeywordUpsertMemoryOutboxTaskHandler(
            MemoryKeywordIndexPort keywordIndexPort) {
        return new KeywordMemoryOutboxTaskHandler(keywordIndexPort, MemoryOutboxTaskTypes.KEYWORD_UPSERT);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryKeywordIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseKeywordDeleteMemoryOutboxTaskHandler")
    public KeywordMemoryOutboxTaskHandler seahorseKeywordDeleteMemoryOutboxTaskHandler(
            MemoryKeywordIndexPort keywordIndexPort) {
        return new KeywordMemoryOutboxTaskHandler(keywordIndexPort, MemoryOutboxTaskTypes.KEYWORD_DELETE);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryGraphIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseGraphUpsertMemoryOutboxTaskHandler")
    public GraphMemoryOutboxTaskHandler seahorseGraphUpsertMemoryOutboxTaskHandler(
            MemoryGraphIndexPort graphIndexPort) {
        return new GraphMemoryOutboxTaskHandler(graphIndexPort, MemoryOutboxTaskTypes.GRAPH_UPSERT);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryGraphIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseGraphDeleteMemoryOutboxTaskHandler")
    public GraphMemoryOutboxTaskHandler seahorseGraphDeleteMemoryOutboxTaskHandler(
            MemoryGraphIndexPort graphIndexPort) {
        return new GraphMemoryOutboxTaskHandler(graphIndexPort, MemoryOutboxTaskTypes.GRAPH_DELETE);
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean
    public MemoryOutboxRelayService seahorseMemoryOutboxRelayService(
            MemoryOutboxPort memoryOutboxPort,
            ObjectProvider<MemoryOutboxTaskHandler> taskHandlers,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<ObservationPort> observationPort) {
        return new MemoryOutboxRelayService(
                memoryOutboxPort,
                taskHandlers.orderedStream().toList(),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                observationPort.getIfAvailable(ObservationPort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxRelayService.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.outbox", name = "relay-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SeahorseMemoryOutboxRelayJob seahorseMemoryOutboxRelayJob(
            MemoryOutboxRelayService relayService,
            ObjectProvider<DistributedLockPort> lockPort,
            MemoryProperties memoryProperties) {
        return new SeahorseMemoryOutboxRelayJob(
                relayService,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                memoryProperties.getOutbox().getRelayBatchSize());
    }
}
