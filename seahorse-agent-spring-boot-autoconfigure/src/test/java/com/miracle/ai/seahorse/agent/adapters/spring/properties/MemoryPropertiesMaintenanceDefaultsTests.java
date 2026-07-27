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

import com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelMemoryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPropertiesMaintenanceDefaultsTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void shouldEnableCompactionByDefaultForMaintenanceRuns() {
        contextRunner.run(context -> {
            MemoryProperties.Maintenance maintenance = context.getBean(MemoryProperties.class).getMaintenance();

            assertThat(maintenance.isCompactionEnabled()).isTrue();
            assertThat(maintenance.isAliasEnabled()).isFalse();
            assertThat(maintenance.isGcEnabled()).isTrue();
        });
    }

    @Test
    void shouldAllowDisablingCompactionWithCanonicalProperty() {
        contextRunner.withPropertyValues("seahorse-agent.memory.maintenance.compaction-enabled=false")
                .run(context -> {
                    MemoryProperties.Maintenance maintenance = context.getBean(MemoryProperties.class).getMaintenance();

                    assertThat(maintenance.isCompactionEnabled()).isFalse();
                });
    }

    @Test
    void shouldBindLegacyGarbageCollectionDryRunProperty() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelMemoryAutoConfiguration.class))
                .withPropertyValues("seahorse.agent.memory.maintenance.gc.dry-run=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MemoryProperties.class).getMaintenance().getGc().isDryRun())
                            .isTrue();
                });
    }

    @Test
    void shouldPreferCanonicalGarbageCollectionDryRunProperty() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelMemoryAutoConfiguration.class))
                .withPropertyValues(
                        "seahorse.agent.memory.maintenance.gc.dry-run=true",
                        "seahorse-agent.memory.maintenance.gc.dry-run=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MemoryProperties.class).getMaintenance().getGc().isDryRun())
                            .isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MemoryProperties.class)
    static class PropertiesConfiguration {
    }
}
