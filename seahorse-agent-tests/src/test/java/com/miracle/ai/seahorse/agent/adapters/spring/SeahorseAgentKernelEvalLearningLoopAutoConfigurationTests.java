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

import com.miracle.ai.seahorse.agent.kernel.application.learning.KernelLearningLoopService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopRunResult;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自学习闭环与落地工单的装配验证。
 *
 * <p>这里要证明的是"应用能起来"：闭环运行器与工单服务都依赖若干可选端口，
 * 一旦某个 {@code ObjectProvider} 漏了 {@code empty()} 兜底，装配阶段就会因缺 bean
 * 而启动失败——那是在生产启动路径上才炸，单测与控制器 slice 测试都覆盖不到。
 *
 * <p>因此本测试刻意<b>不提供</b>任何持久化端口，走最坏的"没接数据源"分支。
 */
class SeahorseAgentKernelEvalLearningLoopAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelEvalAutoConfiguration.class));

    @Test
    void shouldWireLearningLoopAndTicketServiceWithoutAnyPersistencePort() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KernelLearningLoopService.class);
            assertThat(context).hasSingleBean(PolicyAdjustmentTicketManagementInboundPort.class);
        });
    }

    @Test
    void shouldReturnEmptyReportInsteadOfFailingWhenNoDataSourceIsBound() {
        contextRunner.run(context -> {
            PolicyAdjustmentTicketManagementInboundPort port =
                    context.getBean(PolicyAdjustmentTicketManagementInboundPort.class);

            // 没有任何取样端口时，跑闭环应当给出空报告而不是抛异常——
            // 否则前端点一次"运行闭环"就把整个运营页打挂了
            LearningLoopRunResult result = port.runAndGenerateTickets("default", "ops", 200);

            assertThat(result).isNotNull();
            assertThat(result.tickets()).isEmpty();
            LearningLoopReport report = result.report();
            assertThat(report).isNotNull();
            assertThat(report.proposals()).isEmpty();
        });
    }

    @Test
    void shouldHonourLearningLoopPropertyOverrides() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.learning-loop.min-samples=9",
                        "seahorse-agent.learning-loop.disapproval-rate-threshold=0.55")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelLearningLoopService.class);
                });
    }
}
