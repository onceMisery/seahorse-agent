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

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 健康检查自动配置。
 *
 * <p>当 Redisson 客户端在 classpath 上且存在 {@link RedissonClient} Bean 时，
 * 注册一个 {@link HealthIndicator} 通过 {@code getBucket("health-check").isExists()} 检测连通性。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
public class SeahorseAgentRedisHealthAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeahorseAgentRedisHealthAutoConfiguration.class);

    /**
     * Redis health indicator using Redisson client.
     */
    @Bean("seahorseRedisHealth")
    @ConditionalOnBean(RedissonClient.class)
    public HealthIndicator seahorseRedisHealthIndicator(RedissonClient redisson) {
        return () -> {
            try {
                boolean reachable = redisson.getBucket("health-check").isExists();
                // isExists() returning true or false both mean Redis is responsive
                return Health.up()
                        .withDetail("redis", "Redisson client")
                        .withDetail("reachable", true)
                        .build();
            } catch (Exception e) {
                LOGGER.warn("[Health] Redis connectivity check failed", e);
                return Health.down()
                        .withDetail("redis", "Redisson client")
                        .withException(e)
                        .build();
            }
        };
    }
}
