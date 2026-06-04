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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a {@link SimpleMeterRegistry} as a fallback when no Prometheus or other
 * registry is configured. This activates the existing {@code MicrometerObservationAdapter}
 * which has {@code @ConditionalOnBean(MeterRegistry.class)}.
 * <p>
 * When {@code micrometer-registry-prometheus} is on the classpath and Prometheus endpoint
 * is enabled, Spring Boot will auto-configure a {@code PrometheusMeterRegistry} instead,
 * and this bean will not be created (due to {@code @ConditionalOnMissingBean}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.adapters.observation", name = "type", havingValue = "micrometer", matchIfMissing = true)
public class SeahorseAgentSimpleMeterRegistryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public SimpleMeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
