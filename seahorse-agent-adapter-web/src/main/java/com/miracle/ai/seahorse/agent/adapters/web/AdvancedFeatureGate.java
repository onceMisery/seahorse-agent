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

    public static AdvancedFeatureGate consumerWebDefaults() {
        return new AdvancedFeatureGate(ProductMode.CONSUMER_WEB, Map.of());
    }

    public static AdvancedFeatureGate allEnabledForTests() {
        EnumMap<AdvancedFeature, Boolean> features = new EnumMap<>(AdvancedFeature.class);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            features.put(feature, true);
        }
        return new AdvancedFeatureGate(ProductMode.ENTERPRISE_PLATFORM, features);
    }

    public static AdvancedFeatureGate configured(ProductMode productMode, Map<AdvancedFeature, Boolean> enabledFeatures) {
        return new AdvancedFeatureGate(productMode, enabledFeatures);
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
        if (productMode == ProductMode.CONSUMER_WEB) {
            return false;
        }
        return Boolean.TRUE.equals(enabledFeatures.get(feature));
    }

    public void requireEnabled(AdvancedFeature feature) {
        if (!isEnabled(feature)) {
            throw new AdvancedFeatureDisabledException(feature, productMode);
        }
    }
}
