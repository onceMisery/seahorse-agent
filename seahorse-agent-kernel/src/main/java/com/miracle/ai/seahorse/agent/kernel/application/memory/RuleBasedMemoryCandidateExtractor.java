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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.InferredMemory;
import com.miracle.ai.seahorse.agent.kernel.policy.MemoryExtractionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * 基于规则的记忆候选提取器。
 *
 * <p>Phase 4A 实现，不调用 LLM。从用户消息中提取明确的事实和偏好声明。
 * 只识别高确定性的模式，避免把噪声写入记忆。
 */
public class RuleBasedMemoryCandidateExtractor implements MemoryInferencePort {

    private final MemoryExtractionPolicy policy;

    public RuleBasedMemoryCandidateExtractor() {
        this(MemoryExtractionPolicy.defaults());
    }

    public RuleBasedMemoryCandidateExtractor(MemoryExtractionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public List<InferredMemory> infer(String userId,
                                      List<MemoryRecord> shortTermMemories,
                                      List<MemoryRecord> semanticMemories) {
        if (shortTermMemories == null || shortTermMemories.isEmpty()) {
            return List.of();
        }

        List<InferredMemory> candidates = new ArrayList<>();

        for (MemoryRecord record : shortTermMemories) {
            String content = record.content();
            if (content == null || content.isBlank()) continue;

            // 只从用户消息中提取
            if (!isUserMessage(record)) continue;

            extractProfile(content, record.id(), candidates);
            extractPreference(content, record.id(), candidates);
        }

        return candidates;
    }

    private void extractProfile(String content, String sourceId, List<InferredMemory> candidates) {
        Matcher matcher = policy.profilePattern().matcher(content);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (value.length() < policy.minValueLength()) continue;
            candidates.add(new InferredMemory(
                    "semantic",
                    "profile:" + normalizeKey(value),
                    "PROFILE",
                    "用户" + matcher.group(0).trim(),
                    policy.profileConfidence(),
                    List.of(sourceId),
                    "规则提取: 自我声明模式"));
        }
    }

    private void extractPreference(String content, String sourceId, List<InferredMemory> candidates) {
        Matcher matcher = policy.preferencePattern().matcher(content);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (value.length() < policy.minValueLength()) continue;
            candidates.add(new InferredMemory(
                    "long_term",
                    "",
                    "PREFERENCE",
                    "用户" + matcher.group(0).trim(),
                    policy.preferenceConfidence(),
                    List.of(sourceId),
                    "规则提取: 偏好声明模式"));
        }
    }

    private boolean isUserMessage(MemoryRecord record) {
        Object role = record.metadata().get("role");
        if (role != null) {
            return "user".equalsIgnoreCase(role.toString());
        }
        // metadata 中无 role 字段时，按 type 兜底
        return "CONVERSATION".equalsIgnoreCase(record.type());
    }

    private String normalizeKey(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
