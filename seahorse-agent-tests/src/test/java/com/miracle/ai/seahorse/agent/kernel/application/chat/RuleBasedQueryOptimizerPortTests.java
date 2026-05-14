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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermExpansionPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * {@link RuleBasedQueryOptimizerPort} 契约测试。
 */
class RuleBasedQueryOptimizerPortTests {

    @Test
    void shouldDetectProperNouns() {
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(QueryTermExpansionPort.noop());

        QueryOptimizationResult result = optimizer.optimize(
                "HNSW 索引和 pgvector 的区别是什么", List.of(), null);

        Assertions.assertEquals("HNSW 索引和 pgvector 的区别是什么", result.originalQuestion());
        Assertions.assertEquals("HNSW 索引和 pgvector 的区别是什么", result.optimizedQuestion());
        Assertions.assertTrue(result.protectedTerms().containsKey("HNSW"));
        Assertions.assertTrue(result.appliedRules().contains("proper_noun_protection"));
    }

    @Test
    void shouldExpandTermsFromMapping() {
        QueryTermExpansionPort expansionPort = query -> {
            if (query.contains("消息队列")) {
                return Map.of("消息队列", List.of("MQ", "Pulsar", "Kafka"));
            }
            return Map.of();
        };
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(expansionPort);

        QueryOptimizationResult result = optimizer.optimize(
                "消息队列怎么选型", List.of(), null);

        Assertions.assertEquals(List.of("MQ", "Pulsar", "Kafka"), result.expandedTerms());
        Assertions.assertTrue(result.appliedRules().contains("term_expansion"));
    }

    @Test
    void shouldNotModifyOptimizedQuestionInPhase3A() {
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(QueryTermExpansionPort.noop());

        QueryOptimizationResult result = optimizer.optimize(
                "这个怎么做", List.of(), null);

        // Phase 3A 不修改查询文本
        Assertions.assertEquals("这个怎么做", result.optimizedQuestion());
    }

    @Test
    void shouldReturnPassthroughForBlankQuestion() {
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(QueryTermExpansionPort.noop());

        QueryOptimizationResult result = optimizer.optimize("", List.of(), null);

        Assertions.assertEquals("", result.originalQuestion());
        Assertions.assertEquals("", result.optimizedQuestion());
        Assertions.assertTrue(result.appliedRules().contains("passthrough"));
    }

    @Test
    void shouldReturnPassthroughForNullQuestion() {
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(QueryTermExpansionPort.noop());

        QueryOptimizationResult result = optimizer.optimize(null, List.of(), null);

        Assertions.assertEquals("", result.originalQuestion());
        Assertions.assertTrue(result.appliedRules().contains("passthrough"));
    }

    @Test
    void shouldReturnNoMatchWhenNothingDetected() {
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(QueryTermExpansionPort.noop());

        QueryOptimizationResult result = optimizer.optimize(
                "今天天气怎么样", List.of(), null);

        Assertions.assertTrue(result.protectedTerms().isEmpty());
        Assertions.assertTrue(result.expandedTerms().isEmpty());
        Assertions.assertTrue(result.appliedRules().contains("no_match"));
    }

    @Test
    void shouldDetectCamelCaseTerms() {
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(QueryTermExpansionPort.noop());

        QueryOptimizationResult result = optimizer.optimize(
                "SpringBoot 怎么配置", List.of(), null);

        Assertions.assertTrue(result.protectedTerms().containsKey("SpringBoot"));
    }

    @Test
    void shouldCombineProperNounAndTermExpansion() {
        QueryTermExpansionPort expansionPort = query -> Map.of("索引", List.of("index", "INDEX"));
        RuleBasedQueryOptimizerPort optimizer = new RuleBasedQueryOptimizerPort(expansionPort);

        QueryOptimizationResult result = optimizer.optimize(
                "HNSW 索引优化", List.of(), null);

        Assertions.assertTrue(result.protectedTerms().containsKey("HNSW"));
        Assertions.assertEquals(List.of("index", "INDEX"), result.expandedTerms());
        Assertions.assertEquals(2, result.appliedRules().size());
    }
}
