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

package com.miracle.ai.seahorse.agent.ports.inbound.retrieval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * 策略相对基线的评测指标差值。
 */
public record RetrievalEvaluationStrategyDelta(
        @JsonProperty("strategyName") String strategyName,
        @JsonProperty("recallAtKDelta") double recallAtKDelta,
        @JsonProperty("precisionAtKDelta") double precisionAtKDelta,
        @JsonProperty("mrrDelta") double mrrDelta,
        @JsonProperty("ndcgAtKDelta") double ndcgAtKDelta,
        @JsonProperty("emptyRecallRateDelta") double emptyRecallRateDelta,
        @JsonProperty("averageLatencyMsDelta") double averageLatencyMsDelta,
        @JsonProperty("p95LatencyMsDelta") double p95LatencyMsDelta
) {

    public RetrievalEvaluationStrategyDelta(String strategyName,
                                            double recallAtKDelta,
                                            double mrrDelta,
                                            double ndcgAtKDelta,
                                            double emptyRecallRateDelta,
                                            double averageLatencyMsDelta,
                                            double p95LatencyMsDelta) {
        this(strategyName, recallAtKDelta, 0D, mrrDelta, ndcgAtKDelta, emptyRecallRateDelta,
                averageLatencyMsDelta, p95LatencyMsDelta);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RetrievalEvaluationStrategyDelta {
        strategyName = Objects.requireNonNullElse(strategyName, "");
    }
}
