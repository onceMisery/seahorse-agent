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

package com.miracle.ai.seahorse.agent.adapters.web;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class AdvancedFeatureGate {

    private final ProductMode productMode;
    private final EnumMap<AdvancedFeature, Boolean> enabledFeatures;

    private AdvancedFeatureGate(ProductMode productMode, Map<AdvancedFeature, Boolean> enabledFeatures) {
        this.productMode = Objects.requireNonNull(productMode, "productMode must not be null");
        this.enabledFeatures = new EnumMap<>(AdvancedFeature.class);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            this.enabledFeatures.put(feature, Boolean.TRUE.equals(enabledFeatures.get(feature)));
        }
    }

    public static AdvancedFeatureGate demoDefaults() {
        return new AdvancedFeatureGate(ProductMode.DEMO, Map.of());
    }

    public static AdvancedFeatureGate allEnabledForTests() {
        EnumMap<AdvancedFeature, Boolean> features = new EnumMap<>(AdvancedFeature.class);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            features.put(feature, true);
        }
        return new AdvancedFeatureGate(ProductMode.ENTERPRISE, features);
    }

    public static AdvancedFeatureGate configured(ProductMode productMode, Map<AdvancedFeature, Boolean> enabledFeatures) {
        return new AdvancedFeatureGate(productMode, enabledFeatures);
    }

    public ProductMode productMode() {
        return productMode;
    }

    public boolean isEnabled(AdvancedFeature feature) {
        // DEMO 模式下，核心功能默认启用，通过配额而非开关来限制使用量
        if (productMode == ProductMode.DEMO) {
            return isDemoCoreFeature(feature) || Boolean.TRUE.equals(enabledFeatures.get(feature));
        }
        return Boolean.TRUE.equals(enabledFeatures.get(feature));
    }

    /**
     * 判断是否为 Demo 模式核心功能。
     * <p>
     * 核心功能在所有产品模式下都应该可用，通过配额而非开关来限制使用量。
     * 这样设计的好处：
     * <ul>
     *   <li>免费用户可以体验完整的核心功能</li>
     *   <li>通过使用量限制引导付费升级</li>
     *   <li>符合行业标准（ChatGPT、Claude、Coze 等都采用此策略）</li>
     * </ul>
     *
     * @param feature 要检查的功能
     * @return 如果是核心功能返回 true，否则返回 false
     */
    private boolean isDemoCoreFeature(AdvancedFeature feature) {
        return switch (feature) {
            // 知识处理核心能力 - RAG 平台的文档处理能力
            case TOOL_CATALOG_MANAGEMENT ->       // 工具目录：普通用户可查看可用工具能力
                    true;
            case INGESTION_PIPELINE_MANAGEMENT -> // 文档处理流水线：自定义文档处理流程
                    true;
            case INGESTION_TASK_MANAGEMENT ->     // 任务管理：监控文档处理任务
                    true;

            // 其他功能默认禁用，需要升级到 RAG 或 ENTERPRISE 模式
            // 包括：
            // - SKILL_MANAGEMENT / AGENT_RUN_MANAGEMENT: Builder/Admin 管理入口
            // - SANDBOX: 代码沙箱隔离（安全相关，成本较高）
            // - AUDIT_LOG: 审计日志（企业合规需求）
            // - COST_ANALYTICS: 成本分析（企业财务管理）
            // - MEMORY_GOVERNANCE: 内存治理（企业级管理）
            // - METADATA_GOVERNANCE: 元数据治理（企业级管理）
            // - ENTERPRISE_PILOT_READINESS: 企业试点就绪（企业专属）
            // - 等等...
            default -> false;
        };
    }

    public void requireEnabled(AdvancedFeature feature) {
        if (!isEnabled(feature)) {
            throw new AdvancedFeatureDisabledException(feature, productMode);
        }
    }
}
