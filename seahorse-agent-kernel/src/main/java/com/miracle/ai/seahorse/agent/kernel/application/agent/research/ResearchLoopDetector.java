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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ResearchLoopDetector {

    private static final int MAX_SAME_QUERY = 3;
    private static final int MAX_SAME_STEP = 3;

    public boolean isSearchLooping(List<String> recentQueries) {
        if (recentQueries == null || recentQueries.size() < MAX_SAME_QUERY) {
            return false;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String query : recentQueries) {
            String normalized = normalizeQuery(query);
            if (normalized == null) {
                continue;
            }
            int count = counts.merge(normalized, 1, Integer::sum);
            if (count >= MAX_SAME_QUERY) {
                return true;
            }
        }
        return false;
    }

    public boolean isStepLooping(List<ResearchStepType> recentSteps) {
        if (recentSteps == null || recentSteps.size() < MAX_SAME_STEP) {
            return false;
        }
        ResearchStepType previous = null;
        int streak = 0;
        for (ResearchStepType step : recentSteps) {
            if (step == null) {
                previous = null;
                streak = 0;
                continue;
            }
            if (Objects.equals(previous, step)) {
                streak++;
            } else {
                previous = step;
                streak = 1;
            }
            if (streak >= MAX_SAME_STEP) {
                return true;
            }
        }
        return false;
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
