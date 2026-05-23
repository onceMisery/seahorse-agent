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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenCase;

import java.util.List;
import java.util.Objects;

/**
 * Reusable bundle of golden cases shipped or curated by operators.
 *
 * <p>Profiles are intentionally lightweight: a name (used to look the bundle up via
 * {@link MemoryRecallGoldenCaseRepositoryPort}), a default {@code topK}, and the cases themselves.
 * The defaults make it cheap to keep multiple benchmark profiles side-by-side — e.g. one for
 * smoke tests and one for production-scale regression.
 */
public record MemoryRecallGoldenCaseProfile(
        String name,
        int topK,
        List<MemoryRecallGoldenCase> cases
) {

    private static final int DEFAULT_TOP_K = 10;

    public MemoryRecallGoldenCaseProfile {
        name = Objects.requireNonNullElse(name, "").trim();
        topK = topK > 0 ? topK : DEFAULT_TOP_K;
        cases = copyCases(cases);
    }

    public boolean isEmpty() {
        return cases.isEmpty();
    }

    private static List<MemoryRecallGoldenCase> copyCases(List<MemoryRecallGoldenCase> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return List.copyOf(source.stream()
                .filter(Objects::nonNull)
                .toList());
    }
}
