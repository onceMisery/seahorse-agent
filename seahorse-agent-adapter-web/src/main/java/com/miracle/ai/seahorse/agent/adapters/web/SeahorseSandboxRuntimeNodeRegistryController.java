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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeNodeRegistryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseSandboxRuntimeNodeRegistryController {

    private final ObjectProvider<SandboxRuntimeNodeRegistryInboundPort> registryProvider;
    private final ObjectProvider<CurrentUserPort> currentUserProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseSandboxRuntimeNodeRegistryController(
            ObjectProvider<SandboxRuntimeNodeRegistryInboundPort> registryProvider,
            ObjectProvider<CurrentUserPort> currentUserProvider,
            AdvancedFeatureGate advancedFeatureGate) {
        this.registryProvider = registryProvider;
        this.currentUserProvider = currentUserProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @GetMapping("/api/admin/sandbox/runtime/registrations")
    public ApiResponse<Object> listRegistrations(@RequestParam(defaultValue = "100") int limit) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        CurrentUserPort currentUser = currentUserProvider.getIfAvailable();
        if (currentUser == null) {
            throw new IllegalStateException("Current user service is unavailable");
        }
        currentUser.requireRole("admin");
        return ApiResponses.requireService(registryProvider, registry -> registry.listRegistrations(limit));
    }

    @PostMapping("/api/admin/sandbox/runtime/registrations/{nodeId}/drain")
    public ApiResponse<Object> drain(@PathVariable String nodeId) {
        return setDraining(nodeId, true);
    }

    @PostMapping("/api/admin/sandbox/runtime/registrations/{nodeId}/resume")
    public ApiResponse<Object> resume(@PathVariable String nodeId) {
        return setDraining(nodeId, false);
    }

    private ApiResponse<Object> setDraining(String nodeId, boolean draining) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        CurrentUserPort currentUser = currentUserProvider.getIfAvailable();
        if (currentUser == null) {
            throw new IllegalStateException("Current user service is unavailable");
        }
        var operator = currentUser.requireCurrentUser();
        if (!operator.hasRole("admin")) {
            throw new SecurityException("Insufficient permissions");
        }
        return ApiResponses.requireService(
                registryProvider,
                registry -> registry.setOperatorDraining(
                        nodeId, draining, operator.operator(), operator.effectiveTenantId()));
    }
}
