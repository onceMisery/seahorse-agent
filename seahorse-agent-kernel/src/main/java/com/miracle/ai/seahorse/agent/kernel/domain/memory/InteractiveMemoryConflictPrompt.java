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

package com.miracle.ai.seahorse.agent.kernel.domain.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record InteractiveMemoryConflictPrompt(
        String conflictId,
        String memoryId1,
        String memoryId2,
        String contentA,
        String contentB,
        String conflictType,
        String severity,
        String question,
        List<Option> options
) {

    public static final List<Option> DEFAULT_OPTIONS = List.of(
            new Option("keep_a", "保留记忆 A"),
            new Option("keep_b", "保留记忆 B"),
            new Option("merge", "合并"),
            new Option("discard", "都不保留"));

    public InteractiveMemoryConflictPrompt {
        conflictId = text(conflictId);
        memoryId1 = text(memoryId1);
        memoryId2 = text(memoryId2);
        contentA = text(contentA);
        contentB = text(contentB);
        conflictType = text(conflictType);
        severity = text(severity);
        question = text(question);
        options = options == null || options.isEmpty() ? DEFAULT_OPTIONS : List.copyOf(options);
    }

    public Map<String, Object> toEventPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conflictId", conflictId);
        payload.put("memoryId1", memoryId1);
        payload.put("memoryId2", memoryId2);
        payload.put("contentA", contentA);
        payload.put("contentB", contentB);
        payload.put("conflictType", conflictType);
        payload.put("severity", severity);
        payload.put("question", question);
        payload.put("options", options.stream()
                .map(Option::toEventPayload)
                .toList());
        return payload;
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    public record Option(String value, String label) {

        public Option {
            value = text(value);
            label = text(label);
        }

        private Map<String, String> toEventPayload() {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("value", value);
            payload.put("label", label);
            return payload;
        }
    }
}
