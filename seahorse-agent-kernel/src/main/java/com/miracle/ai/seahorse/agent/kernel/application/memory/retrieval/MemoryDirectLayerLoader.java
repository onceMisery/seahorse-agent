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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 直接层加载协作者：纠错台账、画像事实与业务文档三层直接记忆的读取与物化。
 * 只依赖三个直接层端口与融合策略；候选项物化复用
 * {@link MemoryCandidateItemSupport}。
 */
final class MemoryDirectLayerLoader {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryDirectLayerLoader.class);

    private final CorrectionLedgerPort correctionLedgerPort;
    private final ProfileMemoryPort profileMemoryPort;
    private final MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort;
    private final MemoryFusionPolicy fusionPolicy;
    private final MemoryCandidateItemSupport itemSupport;

    MemoryDirectLayerLoader(CorrectionLedgerPort correctionLedgerPort,
                            ProfileMemoryPort profileMemoryPort,
                            MemoryBusinessDocumentRetrieverPort businessDocumentRetrieverPort,
                            MemoryFusionPolicy fusionPolicy,
                            MemoryCandidateItemSupport itemSupport) {
        this.correctionLedgerPort = correctionLedgerPort;
        this.profileMemoryPort = profileMemoryPort;
        this.businessDocumentRetrieverPort = businessDocumentRetrieverPort;
        this.fusionPolicy = fusionPolicy;
        this.itemSupport = itemSupport;
    }

    List<MemoryItem> loadCorrections(String userId, String tenantId) {
        try {
            return correctionLedgerPort.listActive(userId, tenantId, fusionPolicy.finalTopK()).stream()
                    .map(this::toCorrectionItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("load correction ledger failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    List<MemoryItem> loadProfileFacts(String userId, String tenantId) {
        try {
            return profileMemoryPort.listActive(userId, tenantId, fusionPolicy.finalTopK()).stream()
                    .map(this::toProfileItem)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.warn("load profile facts failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private MemoryItem toCorrectionItem(CorrectionRule rule) {
        return MemoryItem.builder()
                .id(rule.id())
                .userId(rule.userId())
                .layer(MemoryLayer.SEMANTIC)
                .type("CORRECTION")
                .content(rule.ruleText())
                .metadataJson(itemSupport.serializeMetadata(Map.of(
                        "userId", rule.userId(),
                        "tenantId", rule.tenantId(),
                        "targetKind", rule.targetKind(),
                        "targetKey", rule.targetKey(),
                        "incorrectValue", rule.incorrectValue(),
                        "correctValue", rule.correctValue(),
                        "priority", rule.priority(),
                        "generationId", rule.generationId())))
                .importanceScore(1D)
                .confidenceLevel(1D)
                .createTime(rule.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private MemoryItem toProfileItem(ProfileFact fact) {
        return MemoryItem.builder()
                .id(fact.id())
                .userId(fact.userId())
                .layer(MemoryLayer.SEMANTIC)
                .type("PROFILE")
                .content(fact.valueText())
                .metadataJson(itemSupport.serializeMetadata(Map.of(
                        "userId", fact.userId(),
                        "tenantId", fact.tenantId(),
                        "profileSlot", fact.slotKey(),
                        "sourceType", fact.sourceType(),
                        "generationId", fact.generationId(),
                        "status", fact.status())))
                .importanceScore(1D)
                .confidenceLevel(fact.confidenceLevel())
                .createTime(fact.updatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    List<MemoryItem> loadBusinessDocuments(String userId,
                                                   String tenantId,
                                                   String query,
                                                   int limit,
                                                   List<String> knowledgeBaseIds) {
        if (itemSupport.isBlank(query)) {
            return Collections.emptyList();
        }
        try {
            return businessDocumentRetrieverPort.retrieve(tenantId, query, limit, knowledgeBaseIds);
        } catch (RuntimeException ex) {
            LOG.warn("load business document memories failed: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }
}
