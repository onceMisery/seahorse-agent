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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermExpansionPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于规则的查询优化器。
 *
 * <p>Phase 3A 实现，不调用 LLM。执行以下确定性优化：
 * <ul>
 *   <li>术语映射：通过 {@link QueryTermExpansionPort} 匹配已注册术语</li>
 *   <li>专有名词保护：检测 camelCase、全大写缩写、技术术语模式</li>
 * </ul>
 */
public class RuleBasedQueryOptimizerPort implements QueryOptimizerPort {

    // 匹配 camelCase 或全大写缩写（2+ 大写字母），如 HNSW、MCP、pgvector、Kafka
    private static final Pattern PROPER_NOUN_PATTERN = Pattern.compile(
            "\\b([A-Z][a-zA-Z]*[A-Z][a-zA-Z]*|[A-Z]{2,}|[a-z]+[A-Z][a-zA-Z]+)\\b");

    // 匹配已知技术术语模式（大小写混合、含数字的术语）
    private static final Pattern TECH_TERM_PATTERN = Pattern.compile(
            "\\b([A-Za-z]+[0-9]+|[a-z]+\\.[a-z]+|[A-Z][a-z]+[A-Z])\\b");

    private final QueryTermExpansionPort termExpansionPort;

    public RuleBasedQueryOptimizerPort(QueryTermExpansionPort termExpansionPort) {
        this.termExpansionPort = Objects.requireNonNull(termExpansionPort, "termExpansionPort must not be null");
    }

    @Override
    public QueryOptimizationResult optimize(String originalQuestion,
                                            List<ChatMessage> history,
                                            MemoryContext memoryContext) {
        String safeQuestion = Objects.requireNonNullElse(originalQuestion, "");
        if (safeQuestion.isBlank()) {
            return passthrough(safeQuestion);
        }

        List<String> appliedRules = new ArrayList<>();
        Map<String, String> protectedTerms = new LinkedHashMap<>();
        List<String> expandedTerms = new ArrayList<>();

        // 1. 检测并保护专有名词
        detectProperNouns(safeQuestion, protectedTerms);
        if (!protectedTerms.isEmpty()) {
            appliedRules.add("proper_noun_protection");
        }

        // 2. 术语映射扩展
        Map<String, List<String>> expansions = termExpansionPort.expand(safeQuestion);
        for (Map.Entry<String, List<String>> entry : expansions.entrySet()) {
            String source = entry.getKey();
            List<String> targets = entry.getValue();
            if (targets != null && !targets.isEmpty()) {
                expandedTerms.addAll(targets);
                protectedTerms.putIfAbsent(source, "term_mapping");
            }
        }
        if (!expansions.isEmpty()) {
            appliedRules.add("term_expansion");
        }

        // Phase 3A 不修改查询文本，只记录保护词和扩展词
        return new QueryOptimizationResult(
                safeQuestion,
                safeQuestion,
                protectedTerms,
                expandedTerms,
                appliedRules.isEmpty() ? List.of("no_match") : appliedRules);
    }

    private void detectProperNouns(String question, Map<String, String> protectedTerms) {
        Matcher camelMatcher = PROPER_NOUN_PATTERN.matcher(question);
        while (camelMatcher.find()) {
            String term = camelMatcher.group();
            protectedTerms.putIfAbsent(term, "proper_noun");
        }
        Matcher techMatcher = TECH_TERM_PATTERN.matcher(question);
        while (techMatcher.find()) {
            String term = techMatcher.group();
            protectedTerms.putIfAbsent(term, "tech_term");
        }
    }

    private QueryOptimizationResult passthrough(String question) {
        return new QueryOptimizationResult(question, question, Map.of(), List.of(), List.of("passthrough"));
    }
}
