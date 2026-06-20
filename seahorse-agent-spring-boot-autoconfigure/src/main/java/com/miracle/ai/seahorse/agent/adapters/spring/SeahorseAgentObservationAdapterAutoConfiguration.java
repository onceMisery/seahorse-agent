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

import com.miracle.ai.seahorse.agent.adapters.observation.micrometer.MicrometerObservationAdapter;
import com.miracle.ai.seahorse.agent.adapters.observation.noop.NoopObservationAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 观测适配器自动配置。
 *
 * <p>从原生适配器总配置拆出，保持 Bean 名称和条件不变，降低总配置类继续膨胀的风险。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentObservationAdapterAutoConfiguration {

    @Bean
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.observation", name = "type",
            havingValue = "noop")
    @ConditionalOnMissingBean(ObservationPort.class)
    public NoopObservationAdapter seahorseNoopObservationAdapter() {
        return new NoopObservationAdapter();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.micrometer.core.instrument.MeterRegistry",
            "com.miracle.ai.seahorse.agent.adapters.observation.micrometer.MicrometerObservationAdapter"
    })
    static class MicrometerObservationAutoConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.observation", name = "type",
                havingValue = "micrometer")
        @ConditionalOnMissingBean(ObservationPort.class)
        public MicrometerObservationAdapter seahorseMicrometerObservationAdapter(MeterRegistry meterRegistry) {
            return new MicrometerObservationAdapter(meterRegistry);
        }
    }
}
