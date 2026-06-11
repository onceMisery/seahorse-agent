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

package com.miracle.ai.seahorse.agent.adapters.spring.properties;

import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryCaptureRules;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice 7 续：记忆 capture 规则的 Spring properties 绑定。
 *
 * <p>spec §12.2 PR 顺序的第二步：先落 {@code MemoryCaptureRejectionReason} 枚举，再落本类。
 * 默认值与 {@link MemoryCaptureRules#defaults()} 严格一致；通过 {@link #toRules()} 可转换为
 * kernel 端的不可变值对象，保持 kernel 对 Spring 无感知。
 *
 * <p>{@code @ConfigurationProperties("seahorse.agent.memory.capture")} 配置前缀；
 * 子键见 {@link #getProfileStatementPrefixes()} 等 getter 名（kebab-case 自动映射）。
 */
@ConfigurationProperties(prefix = "seahorse.agent.memory.capture")
public class MemoryCaptureRuleProperties {

    private int minCandidateLength = MemoryCaptureRules.DEFAULT_MIN_CANDIDATE_LENGTH;
    private int maxCandidateLength = MemoryCaptureRules.DEFAULT_MAX_CANDIDATE_LENGTH;
    private List<String> profileStatementPrefixes =
            new ArrayList<>(MemoryCaptureRules.DEFAULT_PROFILE_STATEMENT_PREFIXES);
    private List<String> preferenceStatementPrefixes =
            new ArrayList<>(MemoryCaptureRules.DEFAULT_PREFERENCE_STATEMENT_PREFIXES);
    private List<String> questionEndingCharacters =
            new ArrayList<>(MemoryCaptureRules.DEFAULT_QUESTION_ENDING_CHARACTERS);
    private List<String> questionContainsKeywords =
            new ArrayList<>(MemoryCaptureRules.DEFAULT_QUESTION_CONTAINS_KEYWORDS);

    public int getMinCandidateLength() {
        return minCandidateLength;
    }

    public void setMinCandidateLength(int minCandidateLength) {
        this.minCandidateLength = minCandidateLength;
    }

    public int getMaxCandidateLength() {
        return maxCandidateLength;
    }

    public void setMaxCandidateLength(int maxCandidateLength) {
        this.maxCandidateLength = maxCandidateLength;
    }

    public List<String> getProfileStatementPrefixes() {
        return profileStatementPrefixes;
    }

    public void setProfileStatementPrefixes(List<String> profileStatementPrefixes) {
        this.profileStatementPrefixes = profileStatementPrefixes;
    }

    public List<String> getPreferenceStatementPrefixes() {
        return preferenceStatementPrefixes;
    }

    public void setPreferenceStatementPrefixes(List<String> preferenceStatementPrefixes) {
        this.preferenceStatementPrefixes = preferenceStatementPrefixes;
    }

    public List<String> getQuestionEndingCharacters() {
        return questionEndingCharacters;
    }

    public void setQuestionEndingCharacters(List<String> questionEndingCharacters) {
        this.questionEndingCharacters = questionEndingCharacters;
    }

    public List<String> getQuestionContainsKeywords() {
        return questionContainsKeywords;
    }

    public void setQuestionContainsKeywords(List<String> questionContainsKeywords) {
        this.questionContainsKeywords = questionContainsKeywords;
    }

    public MemoryCaptureRules toRules() {
        return new MemoryCaptureRules(
                minCandidateLength,
                maxCandidateLength,
                profileStatementPrefixes,
                preferenceStatementPrefixes,
                questionEndingCharacters,
                questionContainsKeywords);
    }
}
