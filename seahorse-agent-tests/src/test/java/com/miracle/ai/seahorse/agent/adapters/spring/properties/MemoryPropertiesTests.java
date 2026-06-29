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
    void recallDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Recall recall = context.getBean(MemoryProperties.class).getRecall();
            assertThat(recall.getRrfK()).isEqualTo(60);
            assertThat(recall.getDecayLambda()).isEqualTo(0.05d);
            assertThat(recall.getFinalTopK()).isEqualTo(8);
            assertThat(recall.isTimeDecayEnabled()).isTrue();
            assertThat(recall.getChannelTimeoutMs()).isEqualTo(50L);
            assertThat(recall.getChannelWeights()).isEmpty();
            assertThat(recall.getRerankModel()).isEmpty();
            assertThat(recall.getRerankInputTopK()).isEqualTo(8);
            assertThat(recall.getRerankMaxTextChars()).isEqualTo(4000);
            assertThat(recall.getVectorCollection()).isEqualTo("memory_vectors");
            assertThat(recall.getEmbeddingModel()).isEmpty();
            assertThat(recall.getGraphMaxHops()).isEqualTo(1);
            assertThat(recall.getChannelTopK()).isEqualTo(20);
        });
    }

    @Test
    void recallCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.recall.rrf-k=120",
                        "seahorse-agent.memory.recall.decay-lambda=0.12",
                        "seahorse-agent.memory.recall.final-top-k=24",
                        "seahorse-agent.memory.recall.time-decay-enabled=false",
                        "seahorse-agent.memory.recall.channel-timeout-ms=250",
                        "seahorse-agent.memory.recall.channel-weights.vector=0.6",
                        "seahorse-agent.memory.recall.channel-weights.keyword=0.4",
                        "seahorse-agent.memory.recall.rerank-model=bge-reranker",
                        "seahorse-agent.memory.recall.rerank-input-top-k=32",
                        "seahorse-agent.memory.recall.rerank-max-text-chars=2048",
                        "seahorse-agent.memory.recall.vector-collection=custom_vectors",
                        "seahorse-agent.memory.recall.embedding-model=text-embedding-3-small",
                        "seahorse-agent.memory.recall.graph-max-hops=3",
                        "seahorse-agent.memory.recall.channel-top-k=64")
                .run(context -> {
                    MemoryProperties.Recall recall = context.getBean(MemoryProperties.class).getRecall();
                    assertThat(recall.getRrfK()).isEqualTo(120);
                    assertThat(recall.getDecayLambda()).isEqualTo(0.12d);
                    assertThat(recall.getFinalTopK()).isEqualTo(24);
                    assertThat(recall.isTimeDecayEnabled()).isFalse();
                    assertThat(recall.getChannelTimeoutMs()).isEqualTo(250L);
                    assertThat(recall.getChannelWeights())
                            .containsEntry("vector", 0.6d)
                            .containsEntry("keyword", 0.4d);
                    assertThat(recall.getRerankModel()).isEqualTo("bge-reranker");
                    assertThat(recall.getRerankInputTopK()).isEqualTo(32);
                    assertThat(recall.getRerankMaxTextChars()).isEqualTo(2048);
                    assertThat(recall.getVectorCollection()).isEqualTo("custom_vectors");
                    assertThat(recall.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
                    assertThat(recall.getGraphMaxHops()).isEqualTo(3);
                    assertThat(recall.getChannelTopK()).isEqualTo(64);
                });
    }

    @Test
    void traceDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Trace trace = context.getBean(MemoryProperties.class).getTrace();
            assertThat(trace.getMaxEvents()).isEqualTo(1000);
        });
    }

    @Test
    void traceCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues("seahorse-agent.memory.trace.max-events=2500")
                .run(context -> {
                    MemoryProperties.Trace trace = context.getBean(MemoryProperties.class).getTrace();
                    assertThat(trace.getMaxEvents()).isEqualTo(2500);
                });
    }

    @Test
    void engineDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties memoryProperties = context.getBean(MemoryProperties.class);
            assertThat(memoryProperties.getShortTermLimit()).isEqualTo(5);
            assertThat(memoryProperties.getLongTermLimit()).isEqualTo(3);
            assertThat(memoryProperties.getSemanticLimit()).isEqualTo(10);
            assertThat(memoryProperties.isCaptureEnabled()).isTrue();
            assertThat(memoryProperties.isInferenceEnabled()).isFalse();
            assertThat(memoryProperties.getLongTermImportanceThreshold()).isEqualTo(0.6d);
        });
    }

    @Test
    void engineCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.short-term-limit=20",
                        "seahorse-agent.memory.long-term-limit=15",
                        "seahorse-agent.memory.semantic-limit=40",
                        "seahorse-agent.memory.capture-enabled=false",
                        "seahorse-agent.memory.inference-enabled=true",
                        "seahorse-agent.memory.long-term-importance-threshold=0.42")
                .run(context -> {
                    MemoryProperties memoryProperties = context.getBean(MemoryProperties.class);
                    assertThat(memoryProperties.getShortTermLimit()).isEqualTo(20);
                    assertThat(memoryProperties.getLongTermLimit()).isEqualTo(15);
                    assertThat(memoryProperties.getSemanticLimit()).isEqualTo(40);
                    assertThat(memoryProperties.isCaptureEnabled()).isFalse();
                    assertThat(memoryProperties.isInferenceEnabled()).isTrue();
                    assertThat(memoryProperties.getLongTermImportanceThreshold()).isEqualTo(0.42d);
                });
    }

    @Test
    void derivedIndexDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.DerivedIndex derivedIndex = context.getBean(MemoryProperties.class).getDerivedIndex();
            assertThat(derivedIndex.isKeywordEnabled()).isTrue();
            assertThat(derivedIndex.isGraphEnabled()).isTrue();
        });
    }

    @Test
    void derivedIndexCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.derived-index.keyword-enabled=false",
                        "seahorse-agent.memory.derived-index.graph-enabled=false")
                .run(context -> {
                    MemoryProperties.DerivedIndex derivedIndex = context.getBean(MemoryProperties.class)
                            .getDerivedIndex();
                    assertThat(derivedIndex.isKeywordEnabled()).isFalse();
                    assertThat(derivedIndex.isGraphEnabled()).isFalse();
                });
    }

    @Test
    void decayDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Decay decay = context.getBean(MemoryProperties.class).getDecay();
            assertThat(decay.getScanLimit()).isEqualTo(500);
            assertThat(decay.getThreshold()).isEqualTo(0.1d);
            assertThat(decay.isDryRun()).isFalse();
        });
    }

    @Test
    void decayCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.decay.scan-limit=1500",
                        "seahorse-agent.memory.decay.threshold=0.3",
                        "seahorse-agent.memory.decay.dry-run=true")
                .run(context -> {
                    MemoryProperties.Decay decay = context.getBean(MemoryProperties.class).getDecay();
                    assertThat(decay.getScanLimit()).isEqualTo(1500);
                    assertThat(decay.getThreshold()).isEqualTo(0.3d);
                    assertThat(decay.isDryRun()).isTrue();
                });
    }

    @Test
    void aggregationScanLimitDefaultMatchesHistoricalAtValueDefault() {
        contextRunner.run(context -> {
            MemoryProperties.Aggregation aggregation = context.getBean(MemoryProperties.class).getAggregation();
            assertThat(aggregation.getScanLimit()).isEqualTo(100);
        });
    }

    @Test
    void aggregationScanLimitCustomKeyOverridesDefault() {
        contextRunner
                .withPropertyValues("seahorse-agent.memory.aggregation.scan-limit=750")
                .run(context -> {
                    MemoryProperties.Aggregation aggregation = context.getBean(MemoryProperties.class)
                            .getAggregation();
                    assertThat(aggregation.getScanLimit()).isEqualTo(750);
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
    void aliasResolutionDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.AliasResolution aliasResolution = context.getBean(MemoryProperties.class)
                    .getAliasResolution();
            assertThat(aliasResolution.getScanLimit()).isEqualTo(100);
            assertThat(aliasResolution.getAutoResolveConfidenceThreshold()).isEqualTo(0.95d);
            assertThat(aliasResolution.getDictionary()).isEmpty();
        });
    }

    @Test
    void aliasResolutionCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.alias-resolution.scan-limit=500",
                        "seahorse-agent.memory.alias-resolution.auto-resolve-confidence-threshold=0.88",
                        "seahorse-agent.memory.alias-resolution.dictionary.gpt.user-id=user-1",
                        "seahorse-agent.memory.alias-resolution.dictionary.gpt.tenant-id=tenant-x",
                        "seahorse-agent.memory.alias-resolution.dictionary.gpt.alias-text=GPT",
                        "seahorse-agent.memory.alias-resolution.dictionary.gpt.canonical-entity-id=entity-1",
                        "seahorse-agent.memory.alias-resolution.dictionary.gpt.canonical-name=ChatGPT",
                        "seahorse-agent.memory.alias-resolution.dictionary.gpt.entity-type=PRODUCT",
                        "seahorse-agent.memory.alias-resolution.dictionary.gpt.confidence-level=0.91")
                .run(context -> {
                    MemoryProperties.AliasResolution aliasResolution = context.getBean(MemoryProperties.class)
                            .getAliasResolution();
                    assertThat(aliasResolution.getScanLimit()).isEqualTo(500);
                    assertThat(aliasResolution.getAutoResolveConfidenceThreshold()).isEqualTo(0.88d);
                    assertThat(aliasResolution.getDictionary()).hasSize(1);
                    MemoryProperties.AliasResolution.DictionaryEntry entry = aliasResolution.getDictionary()
                            .get("gpt");
                    assertThat(entry).isNotNull();
                    assertThat(entry.getUserId()).isEqualTo("user-1");
                    assertThat(entry.getTenantId()).isEqualTo("tenant-x");
                    assertThat(entry.getAliasText()).isEqualTo("GPT");
                    assertThat(entry.getCanonicalEntityId()).isEqualTo("entity-1");
                    assertThat(entry.getCanonicalName()).isEqualTo("ChatGPT");
                    assertThat(entry.getEntityType()).isEqualTo("PRODUCT");
                    assertThat(entry.getConfidenceLevel()).isEqualTo(0.91d);
                });
    }

    @Test
    void maintenanceEnabledFlagsMatchCurrentDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Maintenance maintenance = context.getBean(MemoryProperties.class).getMaintenance();
            assertThat(maintenance.isCompactionEnabled()).isTrue();
            assertThat(maintenance.isAliasEnabled()).isFalse();
            assertThat(maintenance.isGcEnabled()).isTrue();
        });
    }

    @Test
    void maintenanceEnabledFlagsCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.maintenance.compaction-enabled=true",
                        "seahorse-agent.memory.maintenance.alias-enabled=true",
                        "seahorse-agent.memory.maintenance.gc-enabled=false")
                .run(context -> {
                    MemoryProperties.Maintenance maintenance = context.getBean(MemoryProperties.class).getMaintenance();
                    assertThat(maintenance.isCompactionEnabled()).isTrue();
                    assertThat(maintenance.isAliasEnabled()).isTrue();
                    assertThat(maintenance.isGcEnabled()).isFalse();
                });
    }

    @Test
    void maintenanceGcDefaultsMatchHistoricalAtValueDefaults() {
        contextRunner.run(context -> {
            MemoryProperties.Maintenance.Gc gc = context.getBean(MemoryProperties.class)
                    .getMaintenance()
                    .getGc();
            assertThat(gc.getScanLimit()).isEqualTo(100);
            assertThat(gc.getRetentionDays()).isEqualTo(7L);
            assertThat(gc.isDryRun()).isFalse();
            assertThat(gc.isVectorIndexEnabled()).isTrue();
            assertThat(gc.isKeywordIndexEnabled()).isTrue();
            assertThat(gc.isGraphIndexEnabled()).isTrue();
            assertThat(gc.isArchiveEnabled()).isFalse();
            assertThat(gc.getArchiveIdleDays()).isEqualTo(90L);
            assertThat(gc.getArchiveScoreThreshold()).isEqualTo(0.15d);
            assertThat(gc.isPhysicalDeleteEnabled()).isFalse();
            assertThat(gc.getPhysicalDeleteRetentionDays()).isEqualTo(30L);
        });
    }

    @Test
    void maintenanceGcCustomKeysOverrideDefaults() {
        contextRunner
                .withPropertyValues(
                        "seahorse-agent.memory.maintenance.gc.scan-limit=500",
                        "seahorse-agent.memory.maintenance.gc.retention-days=14",
                        "seahorse-agent.memory.maintenance.gc.dry-run=true",
                        "seahorse-agent.memory.maintenance.gc.vector-index-enabled=false",
                        "seahorse-agent.memory.maintenance.gc.keyword-index-enabled=false",
                        "seahorse-agent.memory.maintenance.gc.graph-index-enabled=false",
                        "seahorse-agent.memory.maintenance.gc.archive-enabled=true",
                        "seahorse-agent.memory.maintenance.gc.archive-idle-days=180",
                        "seahorse-agent.memory.maintenance.gc.archive-score-threshold=0.25",
                        "seahorse-agent.memory.maintenance.gc.physical-delete-enabled=true",
                        "seahorse-agent.memory.maintenance.gc.physical-delete-retention-days=60")
                .run(context -> {
                    MemoryProperties.Maintenance.Gc gc = context.getBean(MemoryProperties.class)
                            .getMaintenance()
                            .getGc();
                    assertThat(gc.getScanLimit()).isEqualTo(500);
                    assertThat(gc.getRetentionDays()).isEqualTo(14L);
                    assertThat(gc.isDryRun()).isTrue();
                    assertThat(gc.isVectorIndexEnabled()).isFalse();
                    assertThat(gc.isKeywordIndexEnabled()).isFalse();
                    assertThat(gc.isGraphIndexEnabled()).isFalse();
                    assertThat(gc.isArchiveEnabled()).isTrue();
                    assertThat(gc.getArchiveIdleDays()).isEqualTo(180L);
                    assertThat(gc.getArchiveScoreThreshold()).isEqualTo(0.25d);
                    assertThat(gc.isPhysicalDeleteEnabled()).isTrue();
                    assertThat(gc.getPhysicalDeleteRetentionDays()).isEqualTo(60L);
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
