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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseFeatureControllerTests {

    @Test
    void shouldReturnConfiguredFeatureStates() {
        AdvancedFeatureGate gate = AdvancedFeatureGate.configured(
                ProductMode.ENTERPRISE_PLATFORM,
                Map.of(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT, true));
        SeahorseFeatureController controller = new SeahorseFeatureController(gate);

        Map<String, Object> response = controller.features();

        assertThat(response.get("productMode")).isEqualTo("ENTERPRISE_PLATFORM");
        Map<?, ?> features = (Map<?, ?>) response.get("features");
        Map<?, ?> agentDefinition = (Map<?, ?>) features.get("AGENT_DEFINITION_MANAGEMENT");
        assertThat(agentDefinition.get("enabled")).isEqualTo(true);
        assertThat(agentDefinition.get("visible")).isEqualTo(true);
        assertThat(agentDefinition.get("reason")).isEqualTo("");
    }

    @Test
    void shouldHideEnterpriseFeaturesInConsumerMode() {
        SeahorseFeatureController controller = new SeahorseFeatureController(
                AdvancedFeatureGate.consumerWebDefaults());

        Map<String, Object> response = controller.features();

        Map<?, ?> features = (Map<?, ?>) response.get("features");
        Map<?, ?> sandbox = (Map<?, ?>) features.get("SANDBOX");
        assertThat(sandbox.get("enabled")).isEqualTo(false);
        assertThat(sandbox.get("visible")).isEqualTo(false);
        assertThat((String) sandbox.get("reason")).contains("消费端模式");
    }
}
