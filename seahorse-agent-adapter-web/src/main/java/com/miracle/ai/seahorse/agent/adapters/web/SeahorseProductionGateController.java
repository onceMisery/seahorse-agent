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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.ProductionGateInboundPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseProductionGateController {

    private final ObjectProvider<ProductionGateInboundPort> productionGatePortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseProductionGateController(ObjectProvider<ProductionGateInboundPort> productionGatePortProvider) {
        this(productionGatePortProvider, AdvancedFeatureGate.consumerWebDefaults());
    }

    @Autowired
    public SeahorseProductionGateController(ObjectProvider<ProductionGateInboundPort> productionGatePortProvider,
                                            ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(productionGatePortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseProductionGateController(ObjectProvider<ProductionGateInboundPort> productionGatePortProvider,
                                            AdvancedFeatureGate advancedFeatureGate) {
        this.productionGatePortProvider = productionGatePortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/agents/{agentId}/production-gate")
    public ApiResponse<Object> generate(@PathVariable String agentId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.PRODUCTION_GATE);
        return ApiResponses.requireService(productionGatePortProvider, port -> port.generate(agentId));
    }

    @GetMapping("/api/agents/{agentId}/production-gate/latest")
    public ApiResponse<Object> latest(@PathVariable String agentId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.PRODUCTION_GATE);
        return ApiResponses.requireService(productionGatePortProvider, port -> port.latest(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Production gate report not found")));
    }
}
