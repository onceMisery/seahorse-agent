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
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeahorseEvalReviewMeasurementSourceTests {

    private static final String DATASET_ID = "ds-benchmark";
    private static final String MODEL_ID = "model-a";

    @Test
    void shouldDeriveBothRatesFromTheEvalRegression() {
        // 4 条样本、前 3 条命中预期 -> 当前通过率 0.75
        KernelEvalRegressionService regression = regression(4, 3);
        SeahorseEvalReviewMeasurementSource source =
                new SeahorseEvalReviewMeasurementSource(regression, DATASET_ID, MODEL_ID, 0.60D);

        ReviewMeasurement measurement = source.apply("default");

        assertTrue(measurement.measurable(), "配齐数据集与模型时应当能取到可比的一对通过率");
        assertEquals(0.60D, measurement.baselinePassRate(), 0.0001D);
        assertEquals(0.75D, measurement.currentPassRate(), 0.0001D);
        assertEquals("eval-regression:ds-benchmark", measurement.source(),
                "来源要能追溯到是哪个数据集跑出来的");
    }

    @Test
    void shouldDegradeWhenNoBaselineIsAvailable() {
        KernelEvalRegressionService regression = regression(4, 3);
        SeahorseEvalReviewMeasurementSource source =
                new SeahorseEvalReviewMeasurementSource(regression, DATASET_ID, MODEL_ID, null);

        ReviewMeasurement measurement = source.apply("default");

        assertFalse(measurement.measurable(),
                "没有基线的\"当前通过率\"判不出改进还是回归，应当不写回访而不是硬写一个方向");
    }

    @Test
    void shouldDegradeWithoutDatasetId() {
        SeahorseEvalReviewMeasurementSource source =
                new SeahorseEvalReviewMeasurementSource(regression(2, 1), "  ", MODEL_ID, 0.5D);

        assertFalse(source.apply("default").measurable(),
                "没配数据集就判定不可测量，不能偷偷发起一次全量回归");
    }

    @Test
    void shouldDegradeWithoutModelId() {
        SeahorseEvalReviewMeasurementSource source =
                new SeahorseEvalReviewMeasurementSource(regression(2, 1), DATASET_ID, null, 0.5D);

        assertFalse(source.apply("default").measurable());
    }

    @Test
    void shouldDegradeWhenTheRegressionServiceIsMissing() {
        SeahorseEvalReviewMeasurementSource source =
                new SeahorseEvalReviewMeasurementSource(null, DATASET_ID, MODEL_ID, 0.5D);

        assertFalse(source.apply("default").measurable(),
                "评测服务未装配时不能抛异常，定时任务不该因为少一个 bean 就炸");
    }

    @Test
    void shouldDegradeWhenTheDatasetHasNoSamples() {
        SeahorseEvalReviewMeasurementSource source =
                new SeahorseEvalReviewMeasurementSource(regression(0, 0), DATASET_ID, MODEL_ID, 0.5D);

        assertFalse(source.apply("default").measurable(),
                "空数据集的通过率是 0/0，拿它当\"回归\"会把人引到错误的方向");
    }

    @Test
    void shouldDegradeWhenTheRegressionThrows() {
        KernelEvalRegressionService failing = new KernelEvalRegressionService(
                datasetId -> {
                    throw new IllegalStateException("upstream eval backend is down");
                },
                (ChatRequest request, String modelId) -> "unused");

        SeahorseEvalReviewMeasurementSource source =
                new SeahorseEvalReviewMeasurementSource(failing, DATASET_ID, MODEL_ID, 0.5D);

        assertFalse(source.apply("default").measurable(),
                "评测源抖动不该让定时任务把异常栈刷到监控里");
    }

    /**
     * 造一个"total 条样本、其中 passed 条命中预期"的评测服务。
     *
     * <p>命中判定走的是 {@code KernelEvalRegressionService} 自己的语义重叠逻辑，
     * 所以桩直接按序号返回"预期文本"或"完全不相关的文本"即可，不用关心它内部怎么算。
     */
    private static KernelEvalRegressionService regression(int total, int passed) {
        List<EvalSample> samples = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            samples.add(new EvalSample(
                    "sample-" + i,
                    DATASET_ID,
                    "query-" + i,
                    "alpha beta gamma",
                    null,
                    null));
        }
        EvalDatasetQueryPort datasetPort = datasetId -> samples;
        ChatModelPort model = (ChatRequest request, String modelId) -> {
            // 用请求里的 query 文本定位当前是第几条样本：前 passed 条返回预期文本，其余不相关
            String content = request.getMessages().isEmpty()
                    ? ""
                    : String.valueOf(request.getMessages().get(0).getContent());
            int sampleIndex = content.startsWith("query-")
                    ? Integer.parseInt(content.substring("query-".length()))
                    : Integer.MAX_VALUE;
            return sampleIndex < passed ? "alpha beta gamma" : "zzz qqq xxx";
        };
        return new KernelEvalRegressionService(datasetPort, model);
    }
}
