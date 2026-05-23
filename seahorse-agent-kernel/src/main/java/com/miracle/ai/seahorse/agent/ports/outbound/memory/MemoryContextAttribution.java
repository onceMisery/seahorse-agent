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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Attribution view of a memory recall execution: the fused {@link MemoryContext} plus the
 * per-channel candidate ids that fed into the fusion stage.
 *
 * <p>This view is opt-in via {@link MemoryRetrievalPipelinePort#loadWithAttribution} so existing
 * callers that only need the fused context keep using {@code load} without paying the bookkeeping
 * cost. Default implementations return an empty attribution map for backward compatibility.
 */
public record MemoryContextAttribution(
        MemoryContext context,
        Map<String, List<String>> channelCandidateIds) {

    public MemoryContextAttribution {
        Objects.requireNonNull(context, "context must not be null");
        channelCandidateIds = channelCandidateIds == null
                ? Collections.emptyMap()
                : freezeChannelCandidates(channelCandidateIds);
    }

    public static MemoryContextAttribution withoutChannels(MemoryContext context) {
        return new MemoryContextAttribution(context, Collections.emptyMap());
    }

    private static Map<String, List<String>> freezeChannelCandidates(Map<String, List<String>> source) {
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        source.forEach((channel, candidates) -> {
            if (channel == null || channel.isBlank() || candidates == null) {
                return;
            }
            frozen.put(channel, List.copyOf(candidates));
        });
        return Collections.unmodifiableMap(frozen);
    }
}
