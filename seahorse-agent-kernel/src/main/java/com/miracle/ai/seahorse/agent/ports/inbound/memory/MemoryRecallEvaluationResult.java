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

public record MemoryRecallEvaluationResult(
        String caseId,
        String query,
        List<String> expectedMemoryIds,
        List<String> retrievedMemoryIds,
        List<String> missingExpectedMemoryIds,
        boolean scored,
        boolean hit,
        double reciprocalRank,
        double recall,
        double precision,
        double noiseRate,
        Map<String, Integer> channelHitCounts
) {

    public MemoryRecallEvaluationResult {
        caseId = normalize(caseId);
        query = normalize(query);
        expectedMemoryIds = copyIds(expectedMemoryIds);
        retrievedMemoryIds = copyIds(retrievedMemoryIds);
        missingExpectedMemoryIds = copyIds(missingExpectedMemoryIds);
        channelHitCounts = freezeChannelHitCounts(channelHitCounts);
    }

    public MemoryRecallEvaluationResult(String caseId,
                                        String query,
                                        List<String> expectedMemoryIds,
                                        List<String> retrievedMemoryIds,
                                        List<String> missingExpectedMemoryIds,
                                        boolean scored,
                                        boolean hit,
                                        double reciprocalRank,
                                        double recall,
                                        double precision,
                                        double noiseRate) {
        this(caseId,
                query,
                expectedMemoryIds,
                retrievedMemoryIds,
                missingExpectedMemoryIds,
                scored,
                hit,
                reciprocalRank,
                recall,
                precision,
                noiseRate,
                Collections.emptyMap());
    }

    private static List<String> copyIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(ids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .toList());
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
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
