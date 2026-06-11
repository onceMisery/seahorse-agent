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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcCompensationLogAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.consistency.CompensationLogPort;
import com.miracle.ai.seahorse.agent.kernel.application.consistency.CompensationRetryService;
import com.miracle.ai.seahorse.agent.kernel.application.consistency.ConcurrencyControlService;
import com.miracle.ai.seahorse.agent.kernel.application.consistency.IdempotencyService;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * 数据一致性保障自动配置。
 *
 * <p>注册补偿日志适配器、补偿重试服务、幂等性服务、并发控制服务及定时任务。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentConsistencyAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(CompensationLogPort.class)
    public JdbcCompensationLogAdapter compensationLogPort(DataSource dataSource) {
        return new JdbcCompensationLogAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(CompensationLogPort.class)
    @ConditionalOnMissingBean(CompensationRetryService.class)
    public CompensationRetryService compensationRetryService(
            CompensationLogPort compensationLogPort,
            ObjectProvider<DistributedLockPort> lockPort,
            ObjectProvider<Map<String, Function<String, Boolean>>> retryHandlersProvider) {
        DistributedLockPort lock = lockPort.getIfAvailable(DistributedLockPort::noop);
        Map<String, Function<String, Boolean>> handlers =
                retryHandlersProvider.getIfAvailable(Collections::emptyMap);
        return new CompensationRetryService(compensationLogPort, lock, handlers);
    }

    @Bean
    @ConditionalOnBean(KeyValueCachePort.class)
    @ConditionalOnMissingBean(IdempotencyService.class)
    public IdempotencyService idempotencyService(KeyValueCachePort cachePort) {
        return new IdempotencyService(cachePort);
    }

    @Bean
    @ConditionalOnMissingBean(ConcurrencyControlService.class)
    public ConcurrencyControlService concurrencyControlService(
            ObjectProvider<DistributedLockPort> lockPort) {
        DistributedLockPort lock = lockPort.getIfAvailable(DistributedLockPort::noop);
        return new ConcurrencyControlService(lock);
    }

    @Bean
    @ConditionalOnBean(CompensationRetryService.class)
    @ConditionalOnMissingBean(CompensationRetryJob.class)
    public CompensationRetryJob compensationRetryJob(CompensationRetryService compensationRetryService) {
        return new CompensationRetryJob(compensationRetryService);
    }
}
