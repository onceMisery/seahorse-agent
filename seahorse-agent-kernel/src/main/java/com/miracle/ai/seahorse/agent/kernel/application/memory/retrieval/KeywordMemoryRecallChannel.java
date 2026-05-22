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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KeywordMemoryRecallChannel implements MemoryRecallChannelPort {

    public static final String CHANNEL_NAME = "keyword";
    private static final int ORDER = 200;

    private final MemoryKeywordSearchPort keywordSearchPort;

    public KeywordMemoryRecallChannel(MemoryKeywordSearchPort keywordSearchPort) {
        this.keywordSearchPort = Objects.requireNonNull(keywordSearchPort, "keywordSearchPort must not be null");
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
        List<MemoryKeywordSearchPort.MemoryKeywordHit> hits = keywordSearchPort.search(
                request.userId(),
                request.tenantId(),
                request.query(),
                request.topK());
        List<MemoryRecallCandidate> candidates = new ArrayList<>();
        int rank = 0;
        for (MemoryKeywordSearchPort.MemoryKeywordHit hit
                : hits == null ? List.<MemoryKeywordSearchPort.MemoryKeywordHit>of() : hits) {
            if (hit == null || hit.memoryId().isBlank()) {
                continue;
            }
            rank++;
            Map<String, Object> metadata = hit.metadata();
            candidates.add(new MemoryRecallCandidate(
                    hit.memoryId(),
                    CHANNEL_NAME,
                    rank,
                    hit.score(),
                    request.userId(),
                    request.tenantId(),
                    hit.layer(),
                    Objects.toString(metadata.getOrDefault("type", ""), ""),
                    "",
                    Objects.toString(metadata.getOrDefault("generationId", ""), ""),
                    Objects.toString(metadata.getOrDefault("status", "ACTIVE"), "ACTIVE"),
                    metadata));
        }
        return MemoryRecallAliasRanker.rank(candidates, request);
    }
}
