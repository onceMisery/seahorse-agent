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

import java.util.List;
import java.util.Objects;

/**
 * Slice 7 续：将 {@code MemoryCaptureCandidateExtractor} 中的长度阈值、语言前缀、疑问句
 * 关键字等 magic value 抽离为可配置规则。
 *
 * <p>本对象只表达内核可理解的策略值；starter 通过
 * {@code MemoryCaptureRuleProperties.toRules()} 将外部配置转换为该值对象，保持 kernel
 * 对 Spring 框架无感知（spec §0 - kernel 纯洁性）。
 *
 * <p>{@link #defaults()} 返回的默认值与原硬编码字符串集合 1:1 对齐，确保历史行为零回归。
 *
 * <p>spec §12.3 规避点：本对象不承载 i18n 文案，也不引入外部 NLP 依赖；后续切片再扩展英文
 * 子集与显式 cue 正则的外部化。
 *
 * @param minCandidateLength            候选文本最小字符数；默认 {@value #DEFAULT_MIN_CANDIDATE_LENGTH}
 * @param maxCandidateLength            候选文本最大字符数；默认 {@value #DEFAULT_MAX_CANDIDATE_LENGTH}
 * @param profileStatementPrefixes      标识身份陈述的前缀
 * @param preferenceStatementPrefixes   标识偏好陈述的前缀
 * @param questionEndingCharacters      触发疑问句判定的句尾字符
 * @param questionContainsKeywords      触发疑问句判定的关键词（出现即视为疑问）
 */
public record MemoryCaptureRules(int minCandidateLength,
                                 int maxCandidateLength,
                                 List<String> profileStatementPrefixes,
                                 List<String> preferenceStatementPrefixes,
                                 List<String> questionEndingCharacters,
                                 List<String> questionContainsKeywords) {

    public static final int DEFAULT_MIN_CANDIDATE_LENGTH = 2;
    public static final int DEFAULT_MAX_CANDIDATE_LENGTH = 120;

    public static final List<String> DEFAULT_PROFILE_STATEMENT_PREFIXES = List.of(
            "我是", "我在", "我来自", "我负责", "我擅长");

    public static final List<String> DEFAULT_PREFERENCE_STATEMENT_PREFIXES = List.of(
            "我喜欢", "我偏好", "我习惯", "我常用");

    public static final List<String> DEFAULT_QUESTION_ENDING_CHARACTERS = List.of("?", "？");

    public static final List<String> DEFAULT_QUESTION_CONTAINS_KEYWORDS = List.of(
            "是什么", "怎么", "如何");

    public MemoryCaptureRules {
        if (minCandidateLength <= 0) {
            throw new IllegalArgumentException("minCandidateLength must be > 0");
        }
        if (maxCandidateLength < minCandidateLength) {
            throw new IllegalArgumentException("maxCandidateLength must be >= minCandidateLength");
        }
        profileStatementPrefixes = immutableNonBlank(profileStatementPrefixes,
                DEFAULT_PROFILE_STATEMENT_PREFIXES);
        preferenceStatementPrefixes = immutableNonBlank(preferenceStatementPrefixes,
                DEFAULT_PREFERENCE_STATEMENT_PREFIXES);
        questionEndingCharacters = immutableNonBlank(questionEndingCharacters,
                DEFAULT_QUESTION_ENDING_CHARACTERS);
        questionContainsKeywords = immutableNonBlank(questionContainsKeywords,
                DEFAULT_QUESTION_CONTAINS_KEYWORDS);
    }

    public static MemoryCaptureRules defaults() {
        return new MemoryCaptureRules(
                DEFAULT_MIN_CANDIDATE_LENGTH,
                DEFAULT_MAX_CANDIDATE_LENGTH,
                DEFAULT_PROFILE_STATEMENT_PREFIXES,
                DEFAULT_PREFERENCE_STATEMENT_PREFIXES,
                DEFAULT_QUESTION_ENDING_CHARACTERS,
                DEFAULT_QUESTION_CONTAINS_KEYWORDS);
    }

    private static List<String> immutableNonBlank(List<String> input, List<String> fallback) {
        if (input == null || input.isEmpty()) {
            return List.copyOf(Objects.requireNonNull(fallback, "fallback must not be null"));
        }
        List<String> filtered = input.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return filtered.isEmpty() ? List.copyOf(fallback) : List.copyOf(filtered);
    }
}
