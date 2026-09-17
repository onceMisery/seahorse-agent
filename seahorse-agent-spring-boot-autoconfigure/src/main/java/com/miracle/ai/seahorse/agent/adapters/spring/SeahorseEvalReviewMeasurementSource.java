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

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * 定时回访的<b>默认测量来源</b>：跑一次评测回归，用它的通过率填出 {@link ReviewMeasurement}。
 *
 * <p>为什么需要它：{@code ReviewMeasurement} 一直是调用方的入参——内核没有评测数据源的端口，
 * 硬塞一个进来只会让端口表面积继续膨胀。但这条设计在定时任务上撞了墙：job 是独立运行的，
 * 没有"调用方"，于是永远只能拿到"不可测量"，配了 cron 也只会每轮空转。
 * 这里补的就是这个缺口。
 *
 * <p><b>优先级</b>：接入方自己注册了 {@code Function<String, ReviewMeasurement>} Bean 时
 * 以它为准（显式传入优先）；没有注册才回退到这里。
 *
 * <p><b>代价要说清楚</b>：{@code runRegression} 会把数据集里的每条样本重新打一遍模型，
 * 不是"读上一次报告"这种廉价调用。因此这个来源只在定时任务上使用，且必须显式配置
 * {@code review-dataset-id} 与 {@code review-model-id} 才会真正跑——
 * 没配就直接判定不可测量，不会偷偷发起一次全量评测。
 *
 * <p><b>不可测量时不静默</b>：每一条降级路径都带具体原因写 WARN，并把 datasetId / modelId
 * 一并打出来，避免出现"配了 cron 但什么都没发生又查不出为什么"。
 */
public class SeahorseEvalReviewMeasurementSource implements Function<String, ReviewMeasurement> {

    private static final Logger LOG = LoggerFactory.getLogger(SeahorseEvalReviewMeasurementSource.class);
    private static final String SOURCE_PREFIX = "eval-regression";

    private final KernelEvalRegressionService regressionService;
    private final String datasetId;
    private final String modelId;
    private final Double baselinePassRate;

    public SeahorseEvalReviewMeasurementSource(KernelEvalRegressionService regressionService,
                                               String datasetId,
                                               String modelId,
                                               Double baselinePassRate) {
        this.regressionService = regressionService;
        this.datasetId = datasetId == null ? "" : datasetId.trim();
        this.modelId = modelId == null ? "" : modelId.trim();
        this.baselinePassRate = baselinePassRate;
    }

    @Override
    public ReviewMeasurement apply(String tenantId) {
        String reason = unusableReason();
        if (reason != null) {
            LOG.warn("Learning loop review skipped: {} (tenantId={}, datasetId={}, modelId={})",
                    reason, tenantId, datasetId, modelId);
            return ReviewMeasurement.unavailable();
        }
        try {
            KernelEvalRegressionService.EvalReport report =
                    regressionService.runRegression(datasetId, modelId, baselinePassRate);
            if (report == null) {
                LOG.warn("Learning loop review skipped: eval regression returned no report "
                        + "(tenantId={}, datasetId={}, modelId={})", tenantId, datasetId, modelId);
                return ReviewMeasurement.unavailable();
            }
            if (report.total() <= 0) {
                LOG.warn("Learning loop review skipped: eval dataset has no samples "
                        + "(tenantId={}, datasetId={}, modelId={})", tenantId, datasetId, modelId);
                return ReviewMeasurement.unavailable();
            }
            Double baseline = report.baseline() == null
                    ? baselinePassRate
                    : report.baseline().baselinePassRate();
            if (baseline == null) {
                // 没有基线的"当前通过率"判不出改进还是回归，宁可不写
                LOG.warn("Learning loop review skipped: baseline pass rate is unavailable "
                        + "(tenantId={}, datasetId={}, modelId={}); configure "
                        + "seahorse-agent.learning-loop.review-baseline-pass-rate",
                        tenantId, datasetId, modelId);
                return ReviewMeasurement.unavailable();
            }
            return ReviewMeasurement.of(baseline, report.passRate(), sourceLabel());
        } catch (RuntimeException ex) {
            // 评测数据源抖动不该让定时任务把栈顶刷到监控里，但原因要留下来
            LOG.warn("Learning loop review skipped: eval regression failed "
                    + "(tenantId={}, datasetId={}, modelId={})", tenantId, datasetId, modelId, ex);
            return ReviewMeasurement.unavailable();
        }
    }

    /** 配置缺失的原因；返回 null 表示可以继续跑评测。 */
    private String unusableReason() {
        if (regressionService == null) {
            return "eval regression service is not registered";
        }
        if (datasetId.isEmpty()) {
            return "seahorse-agent.learning-loop.review-dataset-id is not configured";
        }
        if (modelId.isEmpty()) {
            return "seahorse-agent.learning-loop.review-model-id is not configured";
        }
        return null;
    }

    /** 写进回访记录的来源标识，便于日后追溯"这条结论是哪个数据集跑出来的"。 */
    private String sourceLabel() {
        return String.format(Locale.ROOT, "%s:%s", SOURCE_PREFIX, datasetId);
    }

    @Override
    public String toString() {
        return "SeahorseEvalReviewMeasurementSource{datasetId='" + datasetId
                + "', modelId='" + modelId
                + "', baselinePassRate=" + Objects.toString(baselinePassRate)
                + "}";
    }
}
