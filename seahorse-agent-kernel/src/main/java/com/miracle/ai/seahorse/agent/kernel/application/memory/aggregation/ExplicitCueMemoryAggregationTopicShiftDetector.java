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

package com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ExplicitCueMemoryAggregationTopicShiftDetector implements MemoryAggregationTopicShiftDetector {

    private static final List<String> DEFAULT_TOPIC_SHIFT_PREFIXES = List.of(
            "new topic:",
            "switch topic:",
            "change topic:",
            "换个话题",
            "新话题",
            "另一个话题"
    );

    private final List<String> topicShiftPrefixes;

    public ExplicitCueMemoryAggregationTopicShiftDetector() {
        this(DEFAULT_TOPIC_SHIFT_PREFIXES);
    }

    public ExplicitCueMemoryAggregationTopicShiftDetector(List<String> topicShiftPrefixes) {
        this.topicShiftPrefixes = Objects.requireNonNullElse(topicShiftPrefixes, DEFAULT_TOPIC_SHIFT_PREFIXES)
                .stream()
                .map(this::normalize)
                .filter(prefix -> !prefix.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public boolean shouldStartNewTopic(MemoryTurnEvent event, MemoryBufferState currentState) {
        if (event == null || currentState == null || currentState.turnCount() <= 0) {
            return false;
        }
        String text = normalize(event.userText());
        return topicShiftPrefixes.stream().anyMatch(text::startsWith);
    }

    private String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
    }
}
