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

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceService;
import com.miracle.ai.seahorse.agent.kernel.application.trace.RagTraceRecorderOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG Trace 内核自动配置。
 *
 * <p>trace 记录、查询入口和 TTL 清理任务同属可观测治理职责域，独立配置后主 kernel 配置不再承载 trace 治理细节。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelTraceAutoConfiguration {

    @Bean
    @ConditionalOnBean(RagTraceRepositoryPort.class)
    @ConditionalOnMissingBean
    public KernelRagTraceRecorder seahorseRagTraceRecorder(
            RagTraceRepositoryPort traceRepositoryPort,
            @Value("${seahorse-agent.rag-trace.sample-rate:1.0}") double sampleRate) {
        return new KernelRagTraceRecorder(traceRepositoryPort, new RagTraceRecorderOptions(sampleRate));
    }

    @Bean
    @ConditionalOnBean(RagTraceRepositoryPort.class)
    @ConditionalOnMissingBean(RagTraceInboundPort.class)
    public KernelRagTraceService seahorseRagTraceInboundPort(RagTraceRepositoryPort traceRepositoryPort) {
        return new KernelRagTraceService(traceRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(RagTraceRepositoryPort.class)
    @ConditionalOnMissingBean(SeahorseRagTraceCleanupJob.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.rag-trace.cleanup", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SeahorseRagTraceCleanupJob seahorseRagTraceCleanupJob(
            RagTraceRepositoryPort traceRepositoryPort,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse-agent.rag-trace.ttl-days:30}") int ttlDays,
            @Value("${seahorse-agent.rag-trace.cleanup-batch-size:1000}") int batchSize) {
        return new SeahorseRagTraceCleanupJob(traceRepositoryPort,
                lockPort.getIfAvailable(DistributedLockPort::noop), ttlDays, batchSize);
    }
}
