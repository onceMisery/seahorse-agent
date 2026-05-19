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

import com.miracle.ai.seahorse.agent.adapters.cache.local.LocalCacheAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.local.LocalSemaphoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisCacheAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisSemaphoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisStreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedSemaphorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;

/**
 * 缓存与单 JVM/Redis 协调适配器自动配置。
 *
 * <p>该配置承接 cache-local 与 cache-redis 模块，保持原 Bean 名称不变，后续可继续拆出 coordination 专用配置。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentCacheAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimiterPort.class)
    public RateLimiterPort seahorseRateLimiterPort() {
        return RateLimiterPort.noop();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type", havingValue = "local")
    @ConditionalOnMissingBean(KeyValueCachePort.class)
    public LocalCacheAdapter seahorseLocalCacheAdapter() {
        return new LocalCacheAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type", havingValue = "local")
    @ConditionalOnMissingBean(DistributedSemaphorePort.class)
    public LocalSemaphoreAdapter seahorseLocalSemaphoreAdapter() {
        return new LocalSemaphoreAdapter();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.redisson.api.RedissonClient",
            "com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisCacheAdapter"
    })
    static class RedisCacheAutoConfiguration {

        @Bean(destroyMethod = "shutdown")
        @Conditional(RedisRequiredCondition.class)
        @ConditionalOnBean(RedisProperties.class)
        @ConditionalOnMissingBean(RedissonClient.class)
        public RedissonClient redissonClient(RedisProperties redisProperties) {
            Config config = new Config();
            config.useSingleServer()
                    .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                    .setPassword(redisProperties.getPassword())
                    .setDatabase(redisProperties.getDatabase());
            return Redisson.create(config);
        }

        @Bean
        @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type",
                havingValue = "redis", matchIfMissing = true)
        @ConditionalOnBean(RedissonClient.class)
        @ConditionalOnMissingBean(RedisCacheAdapter.class)
        public RedisCacheAdapter seahorseRedisCacheAdapter(RedissonClient redissonClient) {
            return new RedisCacheAdapter(redissonClient);
        }

        @Bean
        @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type",
                havingValue = "redis", matchIfMissing = true)
        @ConditionalOnBean(RedissonClient.class)
        @ConditionalOnMissingBean(DistributedSemaphorePort.class)
        public RedisSemaphoreAdapter seahorseRedisSemaphoreAdapter(RedissonClient redissonClient) {
            return new RedisSemaphoreAdapter(redissonClient);
        }

        @Bean
        @ConditionalOnProperty(prefix = "seahorse-agent.adapters.stream-task", name = "type", havingValue = "redis")
        @ConditionalOnBean(RedissonClient.class)
        @ConditionalOnMissingBean(StreamTaskPort.class)
        public RedisStreamTaskPort seahorseRedisStreamTaskPort(RedissonClient redissonClient) {
            return new RedisStreamTaskPort(redissonClient);
        }
    }

    static class RedisRequiredCondition extends AnyNestedCondition {

        RedisRequiredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type",
                havingValue = "redis", matchIfMissing = true)
        static class RedisCacheSelected {
        }

        @ConditionalOnProperty(prefix = "seahorse-agent.adapters.stream-task", name = "type", havingValue = "redis")
        static class RedisStreamTaskSelected {
        }
    }
}
