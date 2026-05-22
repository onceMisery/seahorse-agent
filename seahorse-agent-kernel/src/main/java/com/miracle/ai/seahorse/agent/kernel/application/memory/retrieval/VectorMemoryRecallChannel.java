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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorHit;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VectorMemoryRecallChannel implements MemoryRecallChannelPort {

    public static final String CHANNEL_NAME = "vector";
    private static final int ORDER = 100;

    private final ScoredMemoryVectorPort vectorPort;

    public VectorMemoryRecallChannel(ScoredMemoryVectorPort vectorPort) {
        this.vectorPort = Objects.requireNonNull(vectorPort, "vectorPort must not be null");
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public List<MemoryRecallCandidate> recall(MemoryRecallRequest request) {
        if (request == null || request.query().isBlank()) {
            return List.of();
        }
        List<ScoredMemoryVectorHit> hits = vectorPort.search(
                request.userId(),
                request.tenantId(),
                request.query(),
                request.topK());
        List<MemoryRecallCandidate> candidates = new ArrayList<>();
        int rank = 0;
        for (ScoredMemoryVectorHit hit : hits == null ? List.<ScoredMemoryVectorHit>of() : hits) {
            if (hit == null || hit.memoryId().isBlank()) {
                continue;
            }
            rank++;
            Map<String, Object> metadata = new LinkedHashMap<>(hit.metadata());
            metadata.put("embeddingModel", hit.embeddingModel());
            candidates.add(new MemoryRecallCandidate(
                    hit.memoryId(),
                    CHANNEL_NAME,
                    rank,
                    hit.score(),
                    request.userId(),
                    request.tenantId(),
                    Objects.toString(metadata.getOrDefault("layer", ""), ""),
                    Objects.toString(metadata.getOrDefault("type", ""), ""),
                    "",
                    hit.generationId(),
                    Objects.toString(metadata.getOrDefault("status", "ACTIVE"), "ACTIVE"),
                    metadata));
        }
        return MemoryRecallAliasRanker.rank(candidates, request);
    }
}
