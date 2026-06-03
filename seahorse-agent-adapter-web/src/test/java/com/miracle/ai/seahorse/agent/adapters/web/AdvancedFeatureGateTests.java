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

import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdvancedFeatureGateTests {

    @Test
    void consumerWebModeShouldDisableNonWebAdvancedFeaturesByDefault() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.consumerWebDefaults();

        assertThat(gate.productMode()).isEqualTo(ProductMode.CONSUMER_WEB);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).isFalse();
        }
    }

    @Test
    void consumerWebModeShouldForceDisableAdvancedFeaturesEvenWhenFlagsAreTrue() {
        EnumMap<AdvancedFeature, Boolean> enabledFeatures = new EnumMap<>(AdvancedFeature.class);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            enabledFeatures.put(feature, true);
        }
        AdvancedFeatureGate gate = AdvancedFeatureGate.configured(ProductMode.CONSUMER_WEB, enabledFeatures);

        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).isFalse();
        }
    }

    @Test
    void shouldRejectDisabledAdvancedFeatureWithProductModeContext() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.consumerWebDefaults();

        assertThatThrownBy(() -> gate.requireEnabled(AdvancedFeature.AGENT_HANDOFF))
                .isInstanceOf(AdvancedFeatureDisabledException.class)
                .hasMessage("Advanced feature AGENT_HANDOFF is disabled in CONSUMER_WEB mode");
    }

    @Test
    void allEnabledGateShouldKeepLegacyControllerTestsFocusedOnApiMapping() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.allEnabledForTests();

        assertThat(gate.productMode()).isEqualTo(ProductMode.ENTERPRISE_PLATFORM);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).isTrue();
        }
    }

    @Test
    void governanceConfigurationShouldMapMcpToolFlag() {
        SeahorseWebGovernanceConfiguration configuration = new SeahorseWebGovernanceConfiguration(false);

        AdvancedFeatureGate gate = configuration.seahorseAdvancedFeatureGate(
                "enterprise-platform",
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);

        assertThat(gate.isEnabled(AdvancedFeature.MCP_TOOL)).isTrue();
        assertThat(gate.isEnabled(AdvancedFeature.SANDBOX)).isFalse();
    }

    @Test
    void governanceConfigurationShouldMapEveryAdvancedFeatureFlag() {
        SeahorseWebGovernanceConfiguration configuration = new SeahorseWebGovernanceConfiguration(false);

        AdvancedFeatureGate gate = configuration.seahorseAdvancedFeatureGate(
                "enterprise-platform",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true);

        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).as(feature.name()).isTrue();
        }
    }
}
