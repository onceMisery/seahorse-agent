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

import com.miracle.ai.seahorse.agent.kernel.application.agent.routing.ModelRoutingPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentModelRoutingAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelEvalAutoConfiguration.class));

    @Test
    void bindsRoutingPropertiesIntoModelRoutingPolicy() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.routing.tiers.low.model-id=test-low",
                        "seahorse-agent.routing.tiers.low.max-context-tokens=16000",
                        "seahorse-agent.routing.tiers.low.cost-per-1k-input=0.0001",
                        "seahorse-agent.routing.tiers.high.model-id=test-high",
                        "seahorse-agent.routing.tiers.high.max-context-tokens=64000",
                        "seahorse-agent.routing.tiers.high.cost-per-1k-input=0.02",
                        "seahorse-agent.routing.fallback-chain[0].tier=high",
                        "seahorse-agent.routing.fallback-chain[0].retry-count=2",
                        "seahorse-agent.routing.fallback-chain[1].tier=low",
                        "seahorse-agent.routing.fallback-chain[1].retry-count=0",
                        "seahorse-agent.routing.high-cost-concurrency-limit=3",
                        "seahorse-agent.routing.high-cost-concurrency-window=45s")
                .run(context -> {
                    ModelRoutingPolicy policy = context.getBean(ModelRoutingPolicy.class);

                    assertThat(policy.selectModel("HIGH", 10.0d, 2000).modelId()).isEqualTo("test-high");
                    assertThat(policy.selectModel("LOW", 10.0d, 2000).modelId()).isEqualTo("test-low");
                });
    }
}
