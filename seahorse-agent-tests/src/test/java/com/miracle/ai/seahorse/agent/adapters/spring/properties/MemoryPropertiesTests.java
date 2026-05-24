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

package com.miracle.ai.seahorse.agent.adapters.spring.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 4：验证 {@link MemoryProperties} 默认值与 binding 行为，确保现存
 * {@code seahorse-agent.memory.aggregation.*} key 不发生破坏性变更。
 */
class MemoryPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesHolderConfiguration.class);

    @Test
    void aggregationDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties properties = context.getBean(MemoryProperties.class);
            MemoryProperties.Aggregation aggregation = properties.getAggregation();
            assertThat(aggregation.isEnabled()).isFalse();
            assertThat(aggregation.getIdleFlushMillis()).isEqualTo(40000L);
            assertThat(aggregation.getMaxTurns()).isEqualTo(10);
            assertThat(aggregation.getMaxTokens()).isEqualTo(2000);
            assertThat(aggregation.getMaxContextBlocks()).isEqualTo(32);
            assertThat(aggregation.getBufferTtlMillis()).isEqualTo(86400000L);
            assertThat(aggregation.isCaptureOnError()).isFalse();
            assertThat(aggregation.isTopicShiftFlushEnabled()).isFalse();
        });
    }

    @Test
    void aggregationCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.aggregation.enabled=true",
                        "seahorse-agent.memory.aggregation.idle-flush-millis=12345",
                        "seahorse-agent.memory.aggregation.max-turns=99",
                        "seahorse-agent.memory.aggregation.max-tokens=4096",
                        "seahorse-agent.memory.aggregation.max-context-blocks=64",
                        "seahorse-agent.memory.aggregation.buffer-ttl-millis=200000",
                        "seahorse-agent.memory.aggregation.capture-on-error=true",
                        "seahorse-agent.memory.aggregation.topic-shift-flush-enabled=true")
                .run(context -> {
                    MemoryProperties.Aggregation aggregation = context.getBean(MemoryProperties.class)
                            .getAggregation();
                    assertThat(aggregation.isEnabled()).isTrue();
                    assertThat(aggregation.getIdleFlushMillis()).isEqualTo(12345L);
                    assertThat(aggregation.getMaxTurns()).isEqualTo(99);
                    assertThat(aggregation.getMaxTokens()).isEqualTo(4096);
                    assertThat(aggregation.getMaxContextBlocks()).isEqualTo(64);
                    assertThat(aggregation.getBufferTtlMillis()).isEqualTo(200000L);
                    assertThat(aggregation.isCaptureOnError()).isTrue();
                    assertThat(aggregation.isTopicShiftFlushEnabled()).isTrue();
                });
    }

    @Test
    void allSixNestedSectionsExist() {
        contextRunner.run(context -> {
            MemoryProperties properties = context.getBean(MemoryProperties.class);
            assertThat(properties.getPolicy()).isNotNull();
            assertThat(properties.getRecall()).isNotNull();
            assertThat(properties.getAggregation()).isNotNull();
            assertThat(properties.getOutbox()).isNotNull();
            assertThat(properties.getMaintenance()).isNotNull();
            assertThat(properties.getRefiner()).isNotNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MemoryProperties.class)
    static class PropertiesHolderConfiguration {
    }
}
