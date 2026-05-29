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
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 评测回归运行服务。
 *
 * <p>对 eval dataset 中的样本重新执行，对比当前模型/prompt 版本的输出质量。
 */
public class KernelEvalRegressionService {

    private static final Pattern CITATION_MARKER = Pattern.compile("\\[[0-9]+]");

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
        return runRegression(datasetId, modelId, null);
    }

    public EvalReport runRegression(String datasetId, String modelId, Double baselinePassRate) {
        List<EvalSample> samples = datasetQueryPort.findByDatasetId(datasetId);
        List<EvalResult> results = new ArrayList<>();

        for (EvalSample sample : samples) {
            EvalResult result = replaySample(sample, modelId);
            results.add(result);
        }

        return EvalReport.aggregate(datasetId, modelId, results, baselinePassRate);
    }

    private EvalResult replaySample(EvalSample sample, String modelId) {
        try {
            String actualResponse = chatModel.chat(modelId, List.of(
                    ChatMessage.user(sample.userQuery())));

            boolean taskCompleted = actualResponse != null && !actualResponse.isBlank();
            boolean citationComplete = hasExpectedCitations(actualResponse, sample.expectedResponse());
            boolean matches = actualResponse != null
                    && !actualResponse.isBlank()
                    && hasSemanticOverlap(actualResponse, sample.expectedResponse())
                    && citationComplete;

            return new EvalResult(sample.sampleId(), matches, actualResponse, null, citationComplete, taskCompleted);
        } catch (Exception e) {
            return new EvalResult(sample.sampleId(), false, null, e.getMessage(), false, false);
        }
    }

    private boolean hasExpectedCitations(String actual, String expected) {
        Set<String> expectedMarkers = citationMarkers(expected);
        if (expectedMarkers.isEmpty()) {
            return true;
        }
        return citationMarkers(actual).containsAll(expectedMarkers);
    }

    private Set<String> citationMarkers(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Matcher matcher = CITATION_MARKER.matcher(text);
        return matcher.results()
                .map(result -> result.group(0))
                .collect(Collectors.toUnmodifiableSet());
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

    public record EvalResult(String sampleId,
                             boolean passed,
                             String actualResponse,
                             String error,
                             boolean citationComplete,
                             boolean taskCompleted) {

        public EvalResult(String sampleId, boolean passed, String actualResponse, String error) {
            this(sampleId, passed, actualResponse, error, passed, actualResponse != null && !actualResponse.isBlank());
        }
    }

    public record EvalReport(String datasetId,
                             String modelId,
                             int total,
                             int passed,
                             int failed,
                             Instant runAt,
                             List<DimensionScore> dimensions,
                             BaselineComparison baseline) {

        public EvalReport(String datasetId, String modelId, int total, int passed, int failed, Instant runAt) {
            this(datasetId, modelId, total, passed, failed, runAt, List.of(),
                    BaselineComparison.withoutBaseline(total == 0 ? 0.0d : (double) passed / total));
        }

        public static EvalReport aggregate(String datasetId, String modelId, List<EvalResult> results) {
            return aggregate(datasetId, modelId, results, null);
        }

        public static EvalReport aggregate(String datasetId, String modelId, List<EvalResult> results,
                                           Double baselinePassRate) {
            int passed = (int) results.stream().filter(EvalResult::passed).count();
            double passRate = results.isEmpty() ? 0.0d : (double) passed / results.size();
            return new EvalReport(
                    datasetId,
                    modelId,
                    results.size(),
                    passed,
                    results.size() - passed,
                    Instant.now(),
                    dimensionScores(results),
                    BaselineComparison.from(passRate, baselinePassRate));
        }

        public double passRate() {
            return total == 0 ? 0.0 : (double) passed / total;
        }

        private static List<DimensionScore> dimensionScores(List<EvalResult> results) {
            return List.of(
                    new DimensionScore(EvalDimension.CITATION_COMPLETENESS, average(results,
                            EvalResult::citationComplete), true,
                            "Checks whether expected citation markers are present in the current output."),
                    new DimensionScore(EvalDimension.TASK_COMPLETION, average(results,
                            EvalResult::taskCompleted), true,
                            "Checks whether the regression replay produced non-empty output."),
                    manual(EvalDimension.ANSWER_QUALITY),
                    manual(EvalDimension.SOURCE_GROUNDEDNESS),
                    manual(EvalDimension.LATENCY),
                    manual(EvalDimension.COST_PER_TASK));
        }

        private static DimensionScore manual(EvalDimension dimension) {
            return new DimensionScore(dimension, null, false, "Manual evaluation required.");
        }

        private static double average(List<EvalResult> results, Predicate<EvalResult> predicate) {
            if (results.isEmpty()) {
                return 0.0d;
            }
            long matched = results.stream().filter(predicate).count();
            return (double) matched / results.size();
        }
    }

    public record DimensionScore(EvalDimension dimension, Double score, boolean automated, String reason) {}

    public record BaselineComparison(Double baselinePassRate,
                                     double currentPassRate,
                                     Double passRateDelta,
                                     String status) {

        public static BaselineComparison from(double currentPassRate, Double baselinePassRate) {
            if (baselinePassRate == null) {
                return withoutBaseline(currentPassRate);
            }
            double delta = currentPassRate - baselinePassRate;
            String status = delta < 0 ? "REGRESSED" : delta > 0 ? "IMPROVED" : "UNCHANGED";
            return new BaselineComparison(baselinePassRate, currentPassRate, delta, status);
        }

        public static BaselineComparison withoutBaseline(double currentPassRate) {
            return new BaselineComparison(null, currentPassRate, null, "NO_BASELINE");
        }
    }
}
