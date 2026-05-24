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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputGovernanceResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.ports.common.NoopFallback;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidationRecordPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 2a：noop guard 启动期检测契约。
 */
class SeahorseAgentNoopPortGuardTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentRuntimeGuardAutoConfiguration.class));

    @Test
    void registersGuardBeanByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SeahorseAgentNoopPortGuard.class);
        });
    }

    @Test
    void skipsGuardBeanWhenExplicitlyDisabled() {
        contextRunner
                .withPropertyValues("seahorse-agent.runtime.noop-guard.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SeahorseAgentNoopPortGuard.class));
    }

    @Test
    void identifiesNoopFallbackOnClassAPort() {
        contextRunner
                .withUserConfiguration(NoopOutputRecordConfiguration.class)
                .run(context -> {
                    SeahorseAgentNoopPortGuard guard = context.getBean(SeahorseAgentNoopPortGuard.class);
                    java.util.List<SeahorseAgentNoopPortGuard.Inspection> inspections = guard.inspect();
                    assertThat(inspections)
                            .filteredOn(i -> i.portClass() == OutputValidationRecordPort.class)
                            .singleElement()
                            .satisfies(only -> {
                                assertThat(only.riskClass())
                                        .isEqualTo(SeahorseAgentNoopPortGuard.RiskClass.CLASS_A_FAIL_FAST);
                                assertThat(only.isNoopFallback()).isTrue();
                                assertThat(only.missing()).isFalse();
                            });
                });
    }

    @Test
    void identifiesRealImplementationOnClassAPort() {
        contextRunner
                .withUserConfiguration(RealOutputRecordConfiguration.class)
                .run(context -> {
                    SeahorseAgentNoopPortGuard guard = context.getBean(SeahorseAgentNoopPortGuard.class);
                    java.util.List<SeahorseAgentNoopPortGuard.Inspection> inspections = guard.inspect();
                    assertThat(inspections)
                            .filteredOn(i -> i.portClass() == OutputValidationRecordPort.class)
                            .singleElement()
                            .satisfies(only -> {
                                assertThat(only.isNoopFallback()).isFalse();
                                assertThat(only.missing()).isFalse();
                            });
                });
    }

    @Test
    void reportsMissingWhenPortNotRegistered() {
        contextRunner.run(context -> {
            SeahorseAgentNoopPortGuard guard = context.getBean(SeahorseAgentNoopPortGuard.class);
            java.util.List<SeahorseAgentNoopPortGuard.Inspection> inspections = guard.inspect();
            assertThat(inspections).isNotEmpty();
            assertThat(inspections).allSatisfy(inspection -> assertThat(inspection.missing()).isTrue());
        });
    }

    @Test
    void coversNewSlice2bClassAPortsInDefaultClassification() {
        contextRunner.run(context -> {
            SeahorseAgentNoopPortGuard guard = context.getBean(SeahorseAgentNoopPortGuard.class);
            java.util.Set<Class<?>> portClasses = guard.inspect().stream()
                    .map(SeahorseAgentNoopPortGuard.Inspection::portClass)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(portClasses).contains(
                    OutputValidationRecordPort.class,
                    com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort.class,
                    com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort.class,
                    com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort.class,
                    com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort.class);
        });
    }

    @Test
    void enforcementThrowsOnClassANoopFallback() {
        contextRunner
                .withUserConfiguration(NoopOutputRecordConfiguration.class)
                .withPropertyValues("seahorse-agent.runtime.noop-guard.enforce-class-a=true")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .isInstanceOf(SeahorseAgentNoopPortGuard.NoopFallbackEnforcementException.class));
    }

    @Test
    void enforcementDoesNotThrowOnClassARealImplementation() {
        contextRunner
                .withUserConfiguration(RealOutputRecordConfiguration.class)
                .withPropertyValues("seahorse-agent.runtime.noop-guard.enforce-class-a=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void allowsExplicitlyClassifiedClassCNoopFallback() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(NoopOutputRecordConfiguration.class)
                .withBean("seahorseAgentNoopPortGuard",
                        SeahorseAgentNoopPortGuard.class,
                        () -> null,
                        bd -> { });
        // 直接调用 inspect 避免对 AutoConfiguration 二次依赖，验证 classification map 可注入。
        Map<Class<?>, SeahorseAgentNoopPortGuard.RiskClass> classifications = Map.of(
                OutputValidationRecordPort.class, SeahorseAgentNoopPortGuard.RiskClass.CLASS_C_OK);
        runner.run(context -> {
            SeahorseAgentNoopPortGuard guard = new SeahorseAgentNoopPortGuard(context.getSourceApplicationContext(),
                    true, classifications);
            assertThat(guard.inspect()).hasSize(1);
            assertThat(guard.inspect().get(0).riskClass())
                    .isEqualTo(SeahorseAgentNoopPortGuard.RiskClass.CLASS_C_OK);
            assertThat(guard.inspect().get(0).isNoopFallback()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class NoopOutputRecordConfiguration {

        @Bean
        OutputValidationRecordPort outputValidationRecordPort() {
            return OutputValidationRecordPort.noop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RealOutputRecordConfiguration {

        @Bean
        OutputValidationRecordPort outputValidationRecordPort() {
            return new RealOutputValidationRecordPort();
        }
    }

    private static final class RealOutputValidationRecordPort implements OutputValidationRecordPort {
        @Override
        public void record(OutputValidationRequest request, OutputGovernanceResult result) {
            // intentionally empty for test purposes; the guard only checks instanceof NoopFallback.
        }
    }

    @SuppressWarnings("unused")
    private static final class MarkerNoopFallback implements OutputValidationRecordPort, NoopFallback {
        @Override
        public void record(OutputValidationRequest request, OutputGovernanceResult result) {
            // marker-only impl used for compile-only assertions in this test class
        }
    }
}
