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
import com.miracle.ai.seahorse.agent.kernel.application.consistency.CompensationRetryService;
import com.miracle.ai.seahorse.agent.kernel.application.consistency.ConcurrencyControlService;
import com.miracle.ai.seahorse.agent.kernel.application.consistency.IdempotencyService;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.ExternalServiceException;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeahorseAgentPhase1AutoConfigurationTests {

    @Test
    void consistencyAutoConfigurationStartsWithoutOptionalPersistenceOrCacheAdapters() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentConsistencyAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ConcurrencyControlService.class);
                    assertThat(context).doesNotHaveBean(CompensationRetryService.class);
                    assertThat(context).doesNotHaveBean(IdempotencyService.class);
                });
    }

    @Test
    void consistencyAutoConfigurationCreatesIdempotencyServiceWhenCachePortExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentConsistencyAutoConfiguration.class))
                .withBean(KeyValueCachePort.class, LocalCacheAdapter::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempotencyService.class);
                });
    }

    @Test
    void resilienceAutoConfigurationWrapsChatModelPortWhenRegistriesExist() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AsyncExecutorConfiguration.class,
                        SeahorseAgentResilienceAutoConfiguration.class))
                .withUserConfiguration(ResilienceRegistryConfiguration.class)
                .withBean(ChatModelPort.class, ChatModelPort::noop)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ResilientChatModelAdapter.class);
                    assertThat(context.getBean(ChatModelPort.class))
                            .isInstanceOf(ResilientChatModelAdapter.class);
                });
    }

    @Test
    void resilientChatModelAdapterShouldRedactCredentialShapedWrappedFailures() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            ResilientChatModelAdapter adapter = new ResilientChatModelAdapter(
                    (request, modelId) -> {
                        throw new IllegalStateException(
                                "provider rejected Authorization: Bearer abcdefghijklmnop api_key=plain-model-secret");
                    },
                    CircuitBreakerRegistry.ofDefaults(),
                    RetryRegistry.ofDefaults(),
                    TimeLimiterRegistry.ofDefaults(),
                    executorService);

            assertThatThrownBy(() -> adapter.chat(ChatRequest.builder().build(), "model-a"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("[REDACTED]")
                    .hasMessageNotContaining("abcdefghijklmnop")
                    .hasMessageNotContaining("plain-model-secret");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ResilienceRegistryConfiguration {

        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() {
            return CircuitBreakerRegistry.ofDefaults();
        }

        @Bean
        RetryRegistry retryRegistry() {
            return RetryRegistry.ofDefaults();
        }

        @Bean
        TimeLimiterRegistry timeLimiterRegistry() {
            return TimeLimiterRegistry.ofDefaults();
        }
    }
}
