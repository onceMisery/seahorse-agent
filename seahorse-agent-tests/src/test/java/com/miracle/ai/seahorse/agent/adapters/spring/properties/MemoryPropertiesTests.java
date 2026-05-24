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

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 4：验证 {@link MemoryProperties} 默认值与 binding 行为，确保现存
 * {@code seahorse-agent.memory.aggregation.*} key 不发生破坏性变更。
 */
class MemoryPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesHolderConfiguration.class);

    @Test
    void aggregationDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties properties = context.getBean(MemoryProperties.class);
            MemoryProperties.Aggregation aggregation = properties.getAggregation();
            assertThat(aggregation.isEnabled()).isFalse();
            assertThat(aggregation.getIdleFlushMillis()).isEqualTo(40000L);
            assertThat(aggregation.getMaxTurns()).isEqualTo(10);
            assertThat(aggregation.getMaxTokens()).isEqualTo(2000);
            assertThat(aggregation.getMaxContextBlocks()).isEqualTo(32);
            assertThat(aggregation.getBufferTtlMillis()).isEqualTo(86400000L);
            assertThat(aggregation.isCaptureOnError()).isFalse();
            assertThat(aggregation.isTopicShiftFlushEnabled()).isFalse();
        });
    }

    @Test
    void aggregationCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.aggregation.enabled=true",
                        "seahorse-agent.memory.aggregation.idle-flush-millis=12345",
                        "seahorse-agent.memory.aggregation.max-turns=99",
                        "seahorse-agent.memory.aggregation.max-tokens=4096",
                        "seahorse-agent.memory.aggregation.max-context-blocks=64",
                        "seahorse-agent.memory.aggregation.buffer-ttl-millis=200000",
                        "seahorse-agent.memory.aggregation.capture-on-error=true",
                        "seahorse-agent.memory.aggregation.topic-shift-flush-enabled=true")
                .run(context -> {
                    MemoryProperties.Aggregation aggregation = context.getBean(MemoryProperties.class)
                            .getAggregation();
                    assertThat(aggregation.isEnabled()).isTrue();
                    assertThat(aggregation.getIdleFlushMillis()).isEqualTo(12345L);
                    assertThat(aggregation.getMaxTurns()).isEqualTo(99);
                    assertThat(aggregation.getMaxTokens()).isEqualTo(4096);
                    assertThat(aggregation.getMaxContextBlocks()).isEqualTo(64);
                    assertThat(aggregation.getBufferTtlMillis()).isEqualTo(200000L);
                    assertThat(aggregation.isCaptureOnError()).isTrue();
                    assertThat(aggregation.isTopicShiftFlushEnabled()).isTrue();
                });
    }

    @Test
    void allSixNestedSectionsExist() {
        contextRunner.run(context -> {
            MemoryProperties properties = context.getBean(MemoryProperties.class);
            assertThat(properties.getPolicy()).isNotNull();
            assertThat(properties.getRecall()).isNotNull();
            assertThat(properties.getAggregation()).isNotNull();
            assertThat(properties.getOutbox()).isNotNull();
            assertThat(properties.getMaintenance()).isNotNull();
            assertThat(properties.getRefiner()).isNotNull();
        });
    }

    @Test
    void policyDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties properties = context.getBean(MemoryProperties.class);
            MemoryProperties.Policy policy = properties.getPolicy();
            assertThat(policy.getCaptureAcceptThreshold()).isEqualTo(0.4d);
            assertThat(policy.getHighValueThreshold()).isEqualTo(0.75d);
            assertThat(policy.getRiskRejectThreshold()).isEqualTo(0.7d);
            assertThat(policy.getTokenBudget()).isEqualTo(2400);
            assertThat(policy.isReviewEnabled()).isFalse();
            assertThat(policy.getRefinerDropConfidenceThreshold()).isEqualTo(0.5d);
            assertThat(policy.getRefinerAutoCommitConfidenceThreshold()).isEqualTo(0.85d);
            assertThat(policy.getRefinerReviewRiskThreshold()).isEqualTo(0.7d);
            assertThat(policy.getSchemaFailureAlertThreshold()).isEqualTo(0);
            assertThat(policy.getOutboxBacklogAlertThreshold()).isEqualTo(0);
            assertThat(policy.getGreyReleaseKey()).isEmpty();
        });
    }

    @Test
    void refinerDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Refiner refiner = context.getBean(MemoryProperties.class).getRefiner();
            assertThat(refiner.isFailOpen()).isTrue();
            assertThat(refiner.getMaxBatchOperations()).isEqualTo(8);
            assertThat(refiner.getMaxDeleteRatio()).isEqualTo(0.7d);
            assertThat(refiner.getReadMaskPerLayerLimit()).isEqualTo(3);
            assertThat(refiner.getTargetZoneTurnCount()).isEqualTo(3);
            assertThat(refiner.getStickyAnchorLimit()).isEqualTo(5);
            assertThat(refiner.getFeedbackExampleLimit()).isEqualTo(3);
            assertThat(refiner.getStickyAnchorImportanceThreshold()).isEqualTo(0.85d);
            assertThat(refiner.getStickyAnchorConfidenceThreshold()).isEqualTo(0.90d);
        });
    }

    @Test
    void refinerCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.refiner.fail-open=false",
                        "seahorse-agent.memory.refiner.max-batch-operations=32",
                        "seahorse-agent.memory.refiner.max-delete-ratio=0.5",
                        "seahorse-agent.memory.refiner.read-mask-per-layer-limit=10",
                        "seahorse-agent.memory.refiner.target-zone-turn-count=6",
                        "seahorse-agent.memory.refiner.sticky-anchor-limit=12",
                        "seahorse-agent.memory.refiner.feedback-example-limit=7",
                        "seahorse-agent.memory.refiner.sticky-anchor-importance-threshold=0.55",
                        "seahorse-agent.memory.refiner.sticky-anchor-confidence-threshold=0.66")
                .run(context -> {
                    MemoryProperties.Refiner refiner = context.getBean(MemoryProperties.class).getRefiner();
                    assertThat(refiner.isFailOpen()).isFalse();
                    assertThat(refiner.getMaxBatchOperations()).isEqualTo(32);
                    assertThat(refiner.getMaxDeleteRatio()).isEqualTo(0.5d);
                    assertThat(refiner.getReadMaskPerLayerLimit()).isEqualTo(10);
                    assertThat(refiner.getTargetZoneTurnCount()).isEqualTo(6);
                    assertThat(refiner.getStickyAnchorLimit()).isEqualTo(12);
                    assertThat(refiner.getFeedbackExampleLimit()).isEqualTo(7);
                    assertThat(refiner.getStickyAnchorImportanceThreshold()).isEqualTo(0.55d);
                    assertThat(refiner.getStickyAnchorConfidenceThreshold()).isEqualTo(0.66d);
                });
    }

    @Test
    void outboxDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Outbox outbox = context.getBean(MemoryProperties.class).getOutbox();
            assertThat(outbox.getRelayBatchSize()).isEqualTo(50);
        });
    }

    @Test
    void outboxCustomKeyOverridesDefault() {
        contextRunner
                .withPropertyValues("seahorse-agent.memory.outbox.relay-batch-size=200")
                .run(context -> {
                    MemoryProperties.Outbox outbox = context.getBean(MemoryProperties.class).getOutbox();
                    assertThat(outbox.getRelayBatchSize()).isEqualTo(200);
                });
    }

    @Test
    void maintenanceCompactionDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Maintenance.Compaction compaction = context.getBean(MemoryProperties.class)
                    .getMaintenance()
                    .getCompaction();
            assertThat(compaction.getScanLimit()).isEqualTo(100);
            assertThat(compaction.getMinGroupSize()).isEqualTo(3);
            assertThat(compaction.isVectorIndexEnabled()).isTrue();
            assertThat(compaction.isKeywordIndexEnabled()).isTrue();
            assertThat(compaction.isGraphIndexEnabled()).isTrue();
            assertThat(compaction.getEmbeddingModel()).isEqualTo("default");
        });
    }

    @Test
    void maintenanceCompactionCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.maintenance.compaction.scan-limit=300",
                        "seahorse-agent.memory.maintenance.compaction.min-group-size=5",
                        "seahorse-agent.memory.maintenance.compaction.vector-index-enabled=false",
                        "seahorse-agent.memory.maintenance.compaction.keyword-index-enabled=false",
                        "seahorse-agent.memory.maintenance.compaction.graph-index-enabled=false",
                        "seahorse-agent.memory.maintenance.compaction.embedding-model=ada-002")
                .run(context -> {
                    MemoryProperties.Maintenance.Compaction compaction = context.getBean(MemoryProperties.class)
                            .getMaintenance()
                            .getCompaction();
                    assertThat(compaction.getScanLimit()).isEqualTo(300);
                    assertThat(compaction.getMinGroupSize()).isEqualTo(5);
                    assertThat(compaction.isVectorIndexEnabled()).isFalse();
                    assertThat(compaction.isKeywordIndexEnabled()).isFalse();
                    assertThat(compaction.isGraphIndexEnabled()).isFalse();
                    assertThat(compaction.getEmbeddingModel()).isEqualTo("ada-002");
                });
    }

    @Test
    void policyCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.policy.capture-accept-threshold=0.55",
                        "seahorse-agent.memory.policy.high-value-threshold=0.9",
                        "seahorse-agent.memory.policy.risk-reject-threshold=0.85",
                        "seahorse-agent.memory.policy.token-budget=4096",
                        "seahorse-agent.memory.policy.review-enabled=true",
                        "seahorse-agent.memory.policy.refiner-drop-confidence-threshold=0.42",
                        "seahorse-agent.memory.policy.refiner-auto-commit-confidence-threshold=0.92",
                        "seahorse-agent.memory.policy.refiner-review-risk-threshold=0.66",
                        "seahorse-agent.memory.policy.schema-failure-alert-threshold=10",
                        "seahorse-agent.memory.policy.outbox-backlog-alert-threshold=500",
                        "seahorse-agent.memory.policy.grey-release-key=tenant-canary")
                .run(context -> {
                    MemoryProperties.Policy policy = context.getBean(MemoryProperties.class).getPolicy();
                    assertThat(policy.getCaptureAcceptThreshold()).isEqualTo(0.55d);
                    assertThat(policy.getHighValueThreshold()).isEqualTo(0.9d);
                    assertThat(policy.getRiskRejectThreshold()).isEqualTo(0.85d);
                    assertThat(policy.getTokenBudget()).isEqualTo(4096);
                    assertThat(policy.isReviewEnabled()).isTrue();
                    assertThat(policy.getRefinerDropConfidenceThreshold()).isEqualTo(0.42d);
                    assertThat(policy.getRefinerAutoCommitConfidenceThreshold()).isEqualTo(0.92d);
                    assertThat(policy.getRefinerReviewRiskThreshold()).isEqualTo(0.66d);
                    assertThat(policy.getSchemaFailureAlertThreshold()).isEqualTo(10);
                    assertThat(policy.getOutboxBacklogAlertThreshold()).isEqualTo(500);
                    assertThat(policy.getGreyReleaseKey()).isEqualTo("tenant-canary");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MemoryProperties.class)
    static class PropertiesHolderConfiguration {
    }
}
