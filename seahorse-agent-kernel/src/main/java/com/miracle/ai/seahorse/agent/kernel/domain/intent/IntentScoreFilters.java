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

package com.miracle.ai.seahorse.agent.kernel.domain.intent;

import java.util.List;

/**
 * 意图分数过滤工具。
 */
public final class IntentScoreFilters {

    private IntentScoreFilters() {
    }

    public static List<IntentScore> kb(List<IntentScore> scores) {
        return safeScores(scores).stream()
                .filter(score -> score.getNode() != null && score.getNode().isKb())
                .toList();
    }

    public static List<IntentScore> mcp(List<IntentScore> scores) {
        return safeScores(scores).stream()
                .filter(score -> score.getNode() != null && score.getNode().isMcp())
                .filter(score -> score.getNode().getMcpToolId() != null)
                .filter(score -> !score.getNode().getMcpToolId().isBlank())
                .toList();
    }

    private static List<IntentScore> safeScores(List<IntentScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }
        return scores;
    }
}
