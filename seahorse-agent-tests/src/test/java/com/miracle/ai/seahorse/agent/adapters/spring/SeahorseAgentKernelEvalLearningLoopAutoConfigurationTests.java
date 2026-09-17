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

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalDatasetQueryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalSample;
import com.miracle.ai.seahorse.agent.kernel.application.learning.KernelLearningLoopService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    // ------------------------------------------------------------------
    // 定时回访的测量来源接线
    // ------------------------------------------------------------------

    @Test
    void shouldNotRegisterReviewJobWhenNoCronIsConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SeahorseLearningLoopReviewJob.class);
        });
    }

    @Test
    void shouldPreferTheExplicitlyRegisteredMeasurementSource() {
        contextRunner
                .withPropertyValues("seahorse-agent.learning-loop.review-cron=" + CRON)
                .withUserConfiguration(ExplicitMeasurementSourceConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SeahorseLearningLoopReviewJob job =
                            context.getBean(SeahorseLearningLoopReviewJob.class);
                    assertThat(job.measurementSource())
                            .isSameAs(ExplicitMeasurementSourceConfig.SOURCE);
                });
    }

    @Test
    void shouldFallBackToTheEvalSourceWhenNoExplicitBeanExists() {
        contextRunner
                .withPropertyValues("seahorse-agent.learning-loop.review-cron=" + CRON)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SeahorseLearningLoopReviewJob job =
                            context.getBean(SeahorseLearningLoopReviewJob.class);

                    assertThat(job.measurementSource())
                            .isInstanceOf(SeahorseEvalReviewMeasurementSource.class);
                    // 没接评测也没配数据集：降级为不可测量，且不能抛异常——
                    // 否则一个没配全的 cron 会让定时任务每轮都往监控里刷栈
                    assertThat(job.measurementSource().apply("default").measurable()).isFalse();
                });
    }

    @Test
    void shouldObtainComparableRatesThroughTheDefaultSourceOnceEvalIsBound() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.learning-loop.review-cron=" + CRON,
                        "seahorse-agent.learning-loop.review-dataset-id=ds-benchmark",
                        "seahorse-agent.learning-loop.review-model-id=model-a",
                        "seahorse-agent.learning-loop.review-baseline-pass-rate=0.60")
                .withUserConfiguration(EvalBackendConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SeahorseLearningLoopReviewJob job =
                            context.getBean(SeahorseLearningLoopReviewJob.class);

                    ReviewMeasurement measurement = job.measurementSource().apply("default");

                    // 这条要证明的是"配齐之后 job 独立运行也能拿到数据"——
                    // 此前 job 只能拿到不可测量，配了 cron 也只是每轮空转
                    assertThat(measurement.measurable()).isTrue();
                    assertThat(measurement.baselinePassRate()).isEqualTo(0.60D);
                    assertThat(measurement.currentPassRate()).isEqualTo(0.75D);
                });
    }

    private static final String CRON = "0 0 3 * * *";

    @Configuration(proxyBeanMethods = false)
    static class ExplicitMeasurementSourceConfig {

        static final Function<String, ReviewMeasurement> SOURCE =
                tenantId -> ReviewMeasurement.of(0.10D, 0.90D, "explicit-bean");

        @Bean
        Function<String, ReviewMeasurement> explicitMeasurementSource() {
            return SOURCE;
        }
    }

    /**
     * 一个最小的评测后端：4 条样本、前 3 条命中预期，因此当前通过率是 0.75。
     *
     * <p>提供 {@link EvalDatasetQueryPort} 与 {@link ChatModelPort} 是为了让
     * {@code KernelEvalRegressionService} 的条件装配成立——默认测量来源依赖它，
     * 它不注册的话整条回退链就只剩"不可测量"。
     */
    @Configuration(proxyBeanMethods = false)
    static class EvalBackendConfig {

        static final String DATASET_ID = "ds-benchmark";
        static final String EXPECTED = "alpha beta gamma";

        @Bean
        EvalDatasetQueryPort evalDatasetQueryPort() {
            List<EvalSample> samples = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                samples.add(new EvalSample(
                        "sample-" + i, DATASET_ID, "query-" + i, EXPECTED, null, null));
            }
            return datasetId -> samples;
        }

        @Bean
        ChatModelPort chatModelPort() {
            return (ChatRequest request, String modelId) -> {
                String content = request.getMessages().isEmpty()
                        ? ""
                        : String.valueOf(request.getMessages().get(0).getContent());
                int index = content.startsWith("query-")
                        ? Integer.parseInt(content.substring("query-".length()))
                        : Integer.MAX_VALUE;
                return index < 3 ? EXPECTED : "zzz qqq xxx";
            };
        }
    }
}
