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

package com.miracle.ai.seahorse.agent.kernel.policy;

import java.util.List;
import java.util.Objects;

/**
 * 记忆路由策略：判定"纠错 / 业务文档 / 情景回忆"三类轨迹的关键词表。
 *
 * <p>这些词表此前以字符串字面量写死在 {@code DefaultMemoryRouter#route} 中，所谓"语义路由"实际是
 * 关键词 contains。抽成载体后词表可由配置覆盖，内核依旧不依赖任何配置框架。
 *
 * <p>中文词表按原文匹配，拉丁词表按小写后的文本匹配，因此两组分开存放。
 */
public final class MemoryRoutingPolicy {

    private static final List<String> DEFAULT_CORRECTION_KEYWORDS =
            List.of("不是", "错了", "改成", "以后别", "忘记");
    private static final List<String> DEFAULT_CORRECTION_KEYWORDS_LATIN =
            List.of("not anymore", "wrong", "correct", "change to", "forget");

    private static final List<String> DEFAULT_BUSINESS_KEYWORDS =
            List.of("知识库", "文档", "规则", "制度", "流程", "阈值", "接口", "API");
    private static final List<String> DEFAULT_BUSINESS_KEYWORDS_LATIN =
            List.of("knowledge", "document", "policy", "rule", "process", "threshold", "api");

    private static final List<String> DEFAULT_EPISODIC_KEYWORDS =
            List.of("上次", "之前", "历史", "项目", "决策", "讨论过", "做过", "长期记忆", "记忆", "记得");
    private static final List<String> DEFAULT_EPISODIC_KEYWORDS_LATIN =
            List.of("last time", "previous", "history", "project", "decision", "discussed",
                    "remember when", "long-term memory");

    private final List<String> correctionKeywords;
    private final List<String> correctionKeywordsLatin;
    private final List<String> businessKeywords;
    private final List<String> businessKeywordsLatin;
    private final List<String> episodicKeywords;
    private final List<String> episodicKeywordsLatin;

    private MemoryRoutingPolicy(Builder b) {
        this.correctionKeywords = copyOf(b.correctionKeywords, "correctionKeywords");
        this.correctionKeywordsLatin = copyOf(b.correctionKeywordsLatin, "correctionKeywordsLatin");
        this.businessKeywords = copyOf(b.businessKeywords, "businessKeywords");
        this.businessKeywordsLatin = copyOf(b.businessKeywordsLatin, "businessKeywordsLatin");
        this.episodicKeywords = copyOf(b.episodicKeywords, "episodicKeywords");
        this.episodicKeywordsLatin = copyOf(b.episodicKeywordsLatin, "episodicKeywordsLatin");
    }

    public List<String> correctionKeywords() {
        return correctionKeywords;
    }

    public List<String> correctionKeywordsLatin() {
        return correctionKeywordsLatin;
    }

    public List<String> businessKeywords() {
        return businessKeywords;
    }

    public List<String> businessKeywordsLatin() {
        return businessKeywordsLatin;
    }

    public List<String> episodicKeywords() {
        return episodicKeywords;
    }

    public List<String> episodicKeywordsLatin() {
        return episodicKeywordsLatin;
    }

    public static MemoryRoutingPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<String> copyOf(List<String> values, String name) {
        return List.copyOf(Objects.requireNonNull(values, name + " must not be null"));
    }

    public static final class Builder {
        private List<String> correctionKeywords = DEFAULT_CORRECTION_KEYWORDS;
        private List<String> correctionKeywordsLatin = DEFAULT_CORRECTION_KEYWORDS_LATIN;
        private List<String> businessKeywords = DEFAULT_BUSINESS_KEYWORDS;
        private List<String> businessKeywordsLatin = DEFAULT_BUSINESS_KEYWORDS_LATIN;
        private List<String> episodicKeywords = DEFAULT_EPISODIC_KEYWORDS;
        private List<String> episodicKeywordsLatin = DEFAULT_EPISODIC_KEYWORDS_LATIN;

        public Builder correctionKeywords(List<String> correctionKeywords) {
            this.correctionKeywords = correctionKeywords;
            return this;
        }

        public Builder correctionKeywordsLatin(List<String> correctionKeywordsLatin) {
            this.correctionKeywordsLatin = correctionKeywordsLatin;
            return this;
        }

        public Builder businessKeywords(List<String> businessKeywords) {
            this.businessKeywords = businessKeywords;
            return this;
        }

        public Builder businessKeywordsLatin(List<String> businessKeywordsLatin) {
            this.businessKeywordsLatin = businessKeywordsLatin;
            return this;
        }

        public Builder episodicKeywords(List<String> episodicKeywords) {
            this.episodicKeywords = episodicKeywords;
            return this;
        }

        public Builder episodicKeywordsLatin(List<String> episodicKeywordsLatin) {
            this.episodicKeywordsLatin = episodicKeywordsLatin;
            return this;
        }

        public MemoryRoutingPolicy build() {
            return new MemoryRoutingPolicy(this);
        }
    }
}
