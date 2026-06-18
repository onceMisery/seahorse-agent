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

    public static AdvancedFeatureGate configured(ProductMode productMode,
                                                 boolean sandboxEnabled,
                                                 boolean connectorManagementEnabled,
                                                 boolean mcpToolEnabled,
                                                 boolean secretManagementEnabled,
                                                 boolean agentHandoffEnabled,
                                                 boolean remoteAgentEnabled,
                                                 boolean localAgentEnabled,
                                                 boolean intentTreeManagementEnabled,
                                                 boolean ingestionTaskManagementEnabled,
                                                 boolean ingestionPipelineManagementEnabled,
                                                 boolean toolCatalogManagementEnabled,
                                                 boolean skillManagementEnabled,
                                                 boolean agentDefinitionManagementEnabled,
                                                 boolean agentFactoryManagementEnabled,
                                                 boolean agentToolBindingManagementEnabled,
                                                 boolean agentRunManagementEnabled,
                                                 boolean agentEvaluationEnabled,
                                                 boolean productionGateEnabled,
                                                 boolean enterprisePilotReadinessEnabled,
                                                 boolean agentRolloutManagementEnabled,
                                                 boolean quotaManagementEnabled,
                                                 boolean resourceAclManagementEnabled,
                                                 boolean memoryGovernanceEnabled,
                                                 boolean ragEvaluationEnabled,
                                                 boolean metadataGovernanceEnabled,
                                                 boolean auditLogEnabled,
                                                 boolean costAnalyticsEnabled) {
        EnumMap<AdvancedFeature, Boolean> features = new EnumMap<>(AdvancedFeature.class);
        features.put(AdvancedFeature.SANDBOX, sandboxEnabled);
        features.put(AdvancedFeature.CONNECTOR_MANAGEMENT, connectorManagementEnabled);
        features.put(AdvancedFeature.MCP_TOOL, mcpToolEnabled);
        features.put(AdvancedFeature.SECRET_MANAGEMENT, secretManagementEnabled);
        features.put(AdvancedFeature.AGENT_HANDOFF, agentHandoffEnabled);
        features.put(AdvancedFeature.REMOTE_AGENT, remoteAgentEnabled);
        features.put(AdvancedFeature.LOCAL_AGENT, localAgentEnabled);
        features.put(AdvancedFeature.INTENT_TREE_MANAGEMENT, intentTreeManagementEnabled);
        features.put(AdvancedFeature.INGESTION_TASK_MANAGEMENT, ingestionTaskManagementEnabled);
        features.put(AdvancedFeature.INGESTION_PIPELINE_MANAGEMENT, ingestionPipelineManagementEnabled);
        features.put(AdvancedFeature.TOOL_CATALOG_MANAGEMENT, toolCatalogManagementEnabled);
        features.put(AdvancedFeature.SKILL_MANAGEMENT, skillManagementEnabled);
        features.put(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT, agentDefinitionManagementEnabled);
        features.put(AdvancedFeature.AGENT_FACTORY_MANAGEMENT, agentFactoryManagementEnabled);
        features.put(AdvancedFeature.AGENT_TOOL_BINDING_MANAGEMENT, agentToolBindingManagementEnabled);
        features.put(AdvancedFeature.AGENT_RUN_MANAGEMENT, agentRunManagementEnabled);
        features.put(AdvancedFeature.AGENT_EVALUATION, agentEvaluationEnabled);
        features.put(AdvancedFeature.PRODUCTION_GATE, productionGateEnabled);
        features.put(AdvancedFeature.ENTERPRISE_PILOT_READINESS, enterprisePilotReadinessEnabled);
        features.put(AdvancedFeature.AGENT_ROLLOUT_MANAGEMENT, agentRolloutManagementEnabled);
        features.put(AdvancedFeature.QUOTA_MANAGEMENT, quotaManagementEnabled);
        features.put(AdvancedFeature.RESOURCE_ACL_MANAGEMENT, resourceAclManagementEnabled);
        features.put(AdvancedFeature.MEMORY_GOVERNANCE, memoryGovernanceEnabled);
        features.put(AdvancedFeature.RAG_EVALUATION, ragEvaluationEnabled);
        features.put(AdvancedFeature.METADATA_GOVERNANCE, metadataGovernanceEnabled);
        features.put(AdvancedFeature.AUDIT_LOG, auditLogEnabled);
        features.put(AdvancedFeature.COST_ANALYTICS, costAnalyticsEnabled);
        return new AdvancedFeatureGate(productMode, features);
    }

    public static AdvancedFeatureGate configured(ProductMode productMode,
                                                 boolean sandboxEnabled,
                                                 boolean connectorManagementEnabled,
                                                 boolean mcpToolEnabled,
                                                 boolean secretManagementEnabled,
                                                 boolean agentHandoffEnabled,
                                                 boolean remoteAgentEnabled,
                                                 boolean localAgentEnabled,
                                                 boolean intentTreeManagementEnabled,
                                                 boolean ingestionTaskManagementEnabled,
                                                 boolean ingestionPipelineManagementEnabled,
                                                 boolean toolCatalogManagementEnabled,
                                                 boolean agentDefinitionManagementEnabled,
                                                 boolean agentFactoryManagementEnabled,
                                                 boolean agentToolBindingManagementEnabled,
                                                 boolean agentRunManagementEnabled,
                                                 boolean agentEvaluationEnabled,
                                                 boolean productionGateEnabled,
                                                 boolean enterprisePilotReadinessEnabled,
                                                 boolean agentRolloutManagementEnabled,
                                                 boolean quotaManagementEnabled,
                                                 boolean resourceAclManagementEnabled,
                                                 boolean memoryGovernanceEnabled,
                                                 boolean ragEvaluationEnabled,
                                                 boolean metadataGovernanceEnabled,
                                                 boolean auditLogEnabled,
                                                 boolean costAnalyticsEnabled) {
        return configured(productMode,
                sandboxEnabled,
                connectorManagementEnabled,
                mcpToolEnabled,
                secretManagementEnabled,
                agentHandoffEnabled,
                remoteAgentEnabled,
                localAgentEnabled,
                intentTreeManagementEnabled,
                ingestionTaskManagementEnabled,
                ingestionPipelineManagementEnabled,
                toolCatalogManagementEnabled,
                false,
                agentDefinitionManagementEnabled,
                agentFactoryManagementEnabled,
                agentToolBindingManagementEnabled,
                agentRunManagementEnabled,
                agentEvaluationEnabled,
                productionGateEnabled,
                enterprisePilotReadinessEnabled,
                agentRolloutManagementEnabled,
                quotaManagementEnabled,
                resourceAclManagementEnabled,
                memoryGovernanceEnabled,
                ragEvaluationEnabled,
                metadataGovernanceEnabled,
                auditLogEnabled,
                costAnalyticsEnabled);
    }

    public static AdvancedFeatureGate configured(ProductMode productMode,
                                                 boolean sandboxEnabled,
                                                 boolean connectorManagementEnabled,
                                                 boolean secretManagementEnabled,
                                                 boolean agentHandoffEnabled,
                                                 boolean remoteAgentEnabled,
                                                 boolean localAgentEnabled,
                                                 boolean mcpToolEnabled,
                                                 boolean agentRunManagementEnabled,
                                                 boolean agentEvaluationEnabled,
                                                 boolean productionGateEnabled) {
        EnumMap<AdvancedFeature, Boolean> features = new EnumMap<>(AdvancedFeature.class);
        features.put(AdvancedFeature.SANDBOX, sandboxEnabled);
        features.put(AdvancedFeature.CONNECTOR_MANAGEMENT, connectorManagementEnabled);
        features.put(AdvancedFeature.SECRET_MANAGEMENT, secretManagementEnabled);
        features.put(AdvancedFeature.AGENT_HANDOFF, agentHandoffEnabled);
        features.put(AdvancedFeature.REMOTE_AGENT, remoteAgentEnabled);
        features.put(AdvancedFeature.LOCAL_AGENT, localAgentEnabled);
        features.put(AdvancedFeature.MCP_TOOL, mcpToolEnabled);
        features.put(AdvancedFeature.AGENT_RUN_MANAGEMENT, agentRunManagementEnabled);
        features.put(AdvancedFeature.AGENT_EVALUATION, agentEvaluationEnabled);
        features.put(AdvancedFeature.PRODUCTION_GATE, productionGateEnabled);
        return new AdvancedFeatureGate(productMode, features);
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
            // Agent 核心能力 - 这些是 RAG Agent 平台的基础功能
            case SKILL_MANAGEMENT ->            // Skills 系统：Agent 调用外部能力的机制
                    true;
            case AGENT_RUN_MANAGEMENT ->        // Agent 执行：运行和管理 Agent 实例
                    true;
            case AGENT_DEFINITION_MANAGEMENT -> // Agent 定义：创建和配置 Agent
                    true;
            case TOOL_CATALOG_MANAGEMENT ->     // 工具目录：管理可用的工具和 API
                    true;

            // 知识处理核心能力 - RAG 平台的文档处理能力
            case INGESTION_PIPELINE_MANAGEMENT -> // 文档处理流水线：自定义文档处理流程
                    true;
            case INGESTION_TASK_MANAGEMENT ->     // 任务管理：监控文档处理任务
                    true;

            // 其他功能默认禁用，需要升级到 RAG 或 ENTERPRISE 模式
            // 包括：
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
