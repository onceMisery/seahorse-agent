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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SeahorseFeatureController {

    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseFeatureController(AdvancedFeatureGate advancedFeatureGate) {
        this.advancedFeatureGate = advancedFeatureGate;
    }

    @GetMapping({"/api/features", "/features"})
    public Map<String, Object> features() {
        Map<String, Object> featureStates = new LinkedHashMap<>();
        for (AdvancedFeature feature : AdvancedFeature.values()) {
            boolean visible = advancedFeatureGate.productMode() != ProductMode.CONSUMER_WEB;
            boolean enabled = advancedFeatureGate.isEnabled(feature);
            featureStates.put(feature.name(), Map.of(
                    "enabled", enabled,
                    "visible", visible,
                    "reason", enabled ? "" : disabledReason(feature)));
        }
        return Map.of(
                "productMode", advancedFeatureGate.productMode().name(),
                "features", featureStates);
    }

    private String disabledReason(AdvancedFeature feature) {
        if (advancedFeatureGate.productMode() == ProductMode.CONSUMER_WEB) {
            return "Current mode is consumer web, " + feature.name() + " is not available";
        }
        return feature.name() + " is not enabled in backend configuration";
    }
}