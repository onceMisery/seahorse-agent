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

import com.github.benmanes.caffeine.cache.Cache;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 多级缓存自动配置。
 *
 * <p>当 Caffeine 类和 KeyValueCachePort 同时存在时自动注册 {@link MultiLevelCacheService}。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Cache.class)
@ConditionalOnProperty(prefix = "seahorse.agent.cache.multi-level", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentMultiLevelCacheAutoConfiguration {

    @Bean
    @ConditionalOnBean(KeyValueCachePort.class)
    @ConditionalOnMissingBean(MultiLevelCacheService.class)
    public MultiLevelCacheService multiLevelCacheService(
            KeyValueCachePort keyValueCachePort,
            @Value("${seahorse.agent.cache.multi-level.local-max-size:10000}") long maxSize,
            @Value("${seahorse.agent.cache.multi-level.local-ttl-minutes:5}") long ttlMinutes) {
        return new MultiLevelCacheService(keyValueCachePort, maxSize, Duration.ofMinutes(ttlMinutes));
    }
}
