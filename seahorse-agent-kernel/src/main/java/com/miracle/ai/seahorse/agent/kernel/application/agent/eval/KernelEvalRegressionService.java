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

package com.miracle.ai.seahorse.agent.kernel.application.agent.eval;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 评测回归运行服务。
 *
 * <p>对 eval dataset 中的样本重新执行，对比当前模型/prompt 版本的输出质量。
 */
public class KernelEvalRegressionService {

    private final EvalDatasetQueryPort datasetQueryPort;
    private final ChatModelPort chatModel;

    public KernelEvalRegressionService(EvalDatasetQueryPort datasetQueryPort, ChatModelPort chatModel) {
        this.datasetQueryPort = Objects.requireNonNull(datasetQueryPort);
        this.chatModel = Objects.requireNonNull(chatModel);
    }

    /**
     * 执行回归评测。
     */
    public EvalReport runRegression(String datasetId, String modelId) {
        List<EvalSample> samples = datasetQueryPort.findByDatasetId(datasetId);
        List<EvalResult> results = new ArrayList<>();

        for (EvalSample sample : samples) {
            EvalResult result = replaySample(sample, modelId);
            results.add(result);
        }

        return EvalReport.aggregate(datasetId, modelId, results);
    }

    private EvalResult replaySample(EvalSample sample, String modelId) {
        try {
            String actualResponse = chatModel.chat(modelId, List.of(
                    ChatMessage.user(sample.userQuery())));

            boolean matches = actualResponse != null
                    && !actualResponse.isBlank()
                    && hasSemanticOverlap(actualResponse, sample.expectedResponse());

            return new EvalResult(sample.sampleId(), matches, actualResponse, null);
        } catch (Exception e) {
            return new EvalResult(sample.sampleId(), false, null, e.getMessage());
        }
    }

    private boolean hasSemanticOverlap(String actual, String expected) {
        if (expected == null || expected.isBlank()) return true;
        String normalizedActual = actual.toLowerCase();
        String[] keywords = expected.toLowerCase().split("\\s+");
        int matched = 0;
        for (String keyword : keywords) {
            if (keyword.length() > 2 && normalizedActual.contains(keyword)) {
                matched++;
            }
        }
        return keywords.length == 0 || (double) matched / keywords.length > 0.3;
    }

    public record EvalResult(String sampleId, boolean passed, String actualResponse, String error) {}

    public record EvalReport(String datasetId, String modelId, int total, int passed, int failed, Instant runAt) {
        public static EvalReport aggregate(String datasetId, String modelId, List<EvalResult> results) {
            int passed = (int) results.stream().filter(EvalResult::passed).count();
            return new EvalReport(datasetId, modelId, results.size(), passed, results.size() - passed, Instant.now());
        }

        public double passRate() {
            return total == 0 ? 0.0 : (double) passed / total;
        }
    }
}
