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

package com.miracle.ai.seahorse.agent.ports.inbound.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MemoryRecallEvaluationReport(
        int caseCount,
        int scoredCaseCount,
        int hitCount,
        double hitRate,
        double meanReciprocalRank,
        double averageRecall,
        double averagePrecision,
        double averageNoiseRate,
        List<MemoryRecallEvaluationResult> results,
        Map<String, Integer> channelHitCounts
) {

    public MemoryRecallEvaluationReport {
        results = List.copyOf(Objects.requireNonNullElse(results, List.of()));
        channelHitCounts = freezeChannelHitCounts(channelHitCounts);
    }

    public MemoryRecallEvaluationReport(int caseCount,
                                        int scoredCaseCount,
                                        int hitCount,
                                        double hitRate,
                                        double meanReciprocalRank,
                                        double averageRecall,
                                        double averagePrecision,
                                        double averageNoiseRate,
                                        List<MemoryRecallEvaluationResult> results) {
        this(caseCount,
                scoredCaseCount,
                hitCount,
                hitRate,
                meanReciprocalRank,
                averageRecall,
                averagePrecision,
                averageNoiseRate,
                results,
                Collections.emptyMap());
    }

    private static Map<String, Integer> freezeChannelHitCounts(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> frozen = new LinkedHashMap<>();
        source.forEach((channel, count) -> {
            if (channel == null || channel.isBlank() || count == null) {
                return;
            }
            frozen.put(channel, Math.max(0, count));
        });
        return Collections.unmodifiableMap(frozen);
    }
}
