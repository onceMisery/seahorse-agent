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

import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resilience4j 弹性能力自动装配。
 *
 * <p>当 Resilience4j 和 ChatModelPort 同时存在时，自动包装为 ResilientChatModelAdapter。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(CircuitBreakerRegistry.class)
@ConditionalOnProperty(prefix = "seahorse.agent.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentResilienceAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnBean(ChatModelPort.class)
    public ResilientChatModelAdapter resilientChatModelAdapter(
            ChatModelPort chatModelPort,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            @Qualifier("aiExecutor") Executor aiExecutor) {
        // Wrap the Executor as ExecutorService if needed
        ExecutorService executorService;
        if (aiExecutor instanceof ExecutorService es) {
            executorService = es;
        } else {
            executorService = Executors.newFixedThreadPool(8);
        }
        return new ResilientChatModelAdapter(
                chatModelPort, circuitBreakerRegistry, retryRegistry, timeLimiterRegistry, executorService);
    }
}
