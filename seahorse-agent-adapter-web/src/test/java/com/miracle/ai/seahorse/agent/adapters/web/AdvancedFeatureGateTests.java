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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdvancedFeatureGateTests {

    @Test
    void consumerWebModeShouldEnableCoreFeaturesByDefault() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.demoDefaults();

        assertThat(gate.productMode()).isEqualTo(ProductMode.DEMO);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).as(feature.name())
                    .isEqualTo(isDemoCoreFeature(feature));
        }
    }

    @Test
    void consumerWebModeShouldAllowExplicitFeatureFlags() {
        EnumMap<AdvancedFeature, Boolean> enabledFeatures = new EnumMap<>(AdvancedFeature.class);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            enabledFeatures.put(feature, true);
        }
        AdvancedFeatureGate gate = AdvancedFeatureGate.configured(ProductMode.DEMO, enabledFeatures);

        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).as(feature.name()).isTrue();
        }
    }

    @Test
    void shouldRejectDisabledAdvancedFeatureWithProductModeContext() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.demoDefaults();

        assertThatThrownBy(() -> gate.requireEnabled(AdvancedFeature.AGENT_HANDOFF))
                .isInstanceOf(AdvancedFeatureDisabledException.class)
                .hasMessage("Advanced feature AGENT_HANDOFF is disabled in DEMO mode");
    }

    @Test
    void quarantinedNonCoreFeaturesShouldDefaultToDisabled() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.demoDefaults();

        for (AdvancedFeature feature : new AdvancedFeature[] {
                AdvancedFeature.MARKETPLACE,
                AdvancedFeature.BILLING,
                AdvancedFeature.RUN_EXPERIMENT,
                AdvancedFeature.ADMIN,
                AdvancedFeature.NOTIFICATION,
                AdvancedFeature.PLUGIN}) {
            assertThat(gate.isEnabled(feature)).as(feature.name()).isFalse();
        }
    }

    @Test
    void allEnabledGateShouldKeepLegacyControllerTestsFocusedOnApiMapping() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.allEnabledForTests();

        assertThat(gate.productMode()).isEqualTo(ProductMode.ENTERPRISE);
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).isTrue();
        }
    }

    @Test
    void governanceConfigurationShouldMapMcpToolFlag() {
        SeahorseWebGovernanceConfiguration configuration =
                new SeahorseWebGovernanceConfiguration(false, null);

        boolean[] flags = allFlags(33, false);
        flags[2] = true;
        AdvancedFeatureGate gate = gateFromFlags(configuration, "enterprise", flags);

        assertThat(gate.isEnabled(AdvancedFeature.MCP_TOOL)).isTrue();
        assertThat(gate.isEnabled(AdvancedFeature.SANDBOX)).isFalse();
        assertThat(gate.isEnabled(AdvancedFeature.MARKETPLACE)).isFalse();
        assertThat(gate.isEnabled(AdvancedFeature.ADMIN)).isFalse();
    }

    @Test
    void governanceConfigurationShouldMapEveryAdvancedFeatureFlag() {
        SeahorseWebGovernanceConfiguration configuration =
                new SeahorseWebGovernanceConfiguration(false, null);

        AdvancedFeatureGate gate = gateFromFlags(configuration, "enterprise", allFlags(33, true));

        for (AdvancedFeature feature : AdvancedFeature.values()) {
            assertThat(gate.isEnabled(feature)).as(feature.name()).isTrue();
        }
    }

    private static AdvancedFeatureGate gateFromFlags(SeahorseWebGovernanceConfiguration configuration,
                                                     String productMode,
                                                     boolean... flags) {
        return configuration.seahorseAdvancedFeatureGate(
                productMode,
                flags[0], flags[1], flags[2], flags[3], flags[4], flags[5],
                flags[6], flags[7], flags[8], flags[9], flags[10], flags[11],
                flags[12], flags[13], flags[14], flags[15], flags[16], flags[17],
                flags[18], flags[19], flags[20], flags[21], flags[22], flags[23],
                flags[24], flags[25], flags[26], flags[27], flags[28], flags[29],
                flags[30], flags[31], flags[32]);
    }

    private static boolean[] allFlags(int count, boolean enabled) {
        boolean[] flags = new boolean[count];
        java.util.Arrays.fill(flags, enabled);
        return flags;
    }

    private static boolean isDemoCoreFeature(AdvancedFeature feature) {
        return switch (feature) {
            case TOOL_CATALOG_MANAGEMENT,
                    INGESTION_PIPELINE_MANAGEMENT,
                    INGESTION_TASK_MANAGEMENT -> true;
            default -> false;
        };
    }
}
