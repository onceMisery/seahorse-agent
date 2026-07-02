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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@AutoConfigureAfter(SeahorseAgentKernelRegistryAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentSandboxMaintenanceAutoConfiguration {

    @Bean
    @ConditionalOnBean(SandboxRuntimeInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.sandbox.session-sweep", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SandboxSessionTtlSweepJob seahorseSandboxSessionTtlSweepJob(
            SandboxRuntimeInboundPort sandboxRuntime,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse.agent.sandbox.session-sweep.tenant-id:default}") String tenantId,
            @Value("${seahorse.agent.sandbox.session-sweep.limit:20}") int limit) {
        return new SandboxSessionTtlSweepJob(
                sandboxRuntime,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                tenantId,
                limit);
    }

    @Bean
    @ConditionalOnBean(SandboxRuntimeInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.sandbox.runtime-sweep", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SandboxRuntimeOrphanSweepJob seahorseSandboxRuntimeOrphanSweepJob(
            SandboxRuntimeInboundPort sandboxRuntime,
            ObjectProvider<DistributedLockPort> lockPort) {
        return new SandboxRuntimeOrphanSweepJob(
                sandboxRuntime,
                lockPort.getIfAvailable(DistributedLockPort::noop));
    }
}
