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

import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import org.springframework.beans.factory.annotation.Autowired;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodePayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Seahorse 原生意图树 Web adapter。
 */
@RestController
public class SeahorseIntentTreeController {

    private final ObjectProvider<IntentTreeInboundPort> intentTreePortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseIntentTreeController(ObjectProvider<IntentTreeInboundPort> intentTreePortProvider) {
        this(intentTreePortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseIntentTreeController(ObjectProvider<IntentTreeInboundPort> intentTreePortProvider,
                                        ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(intentTreePortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseIntentTreeController(ObjectProvider<IntentTreeInboundPort> intentTreePortProvider,
                                        AdvancedFeatureGate advancedFeatureGate) {
        this.intentTreePortProvider = intentTreePortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @GetMapping("/intent-tree/trees")
    public ApiResponse<Object> tree() {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INTENT_TREE_MANAGEMENT);
        return ApiResponses.requireServiceOrError(intentTreePortProvider, IntentTreeInboundPort::tree);
    }

    @PostMapping("/intent-tree")
    public ApiResponse<Object> createNode(@RequestBody IntentNodePayload request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INTENT_TREE_MANAGEMENT);
        return ApiResponses.requireServiceOrError(intentTreePortProvider,
                port -> port.create(Objects.requireNonNull(request, "request must not be null")));
    }

    @PutMapping("/intent-tree/{id}")
    public ApiResponse<Object> updateNode(@PathVariable String id, @RequestBody IntentNodePayload request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INTENT_TREE_MANAGEMENT);
        return ApiResponses.requireServiceOrError(intentTreePortProvider, port -> {
            port.update(id, Objects.requireNonNull(request, "request must not be null"));
            return null;
        });
    }

    @DeleteMapping("/intent-tree/{id}")
    public ApiResponse<Object> deleteNode(@PathVariable String id) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INTENT_TREE_MANAGEMENT);
        return ApiResponses.requireServiceOrError(intentTreePortProvider, port -> {
            port.delete(id);
            return null;
        });
    }

    @PostMapping("/intent-tree/batch/enable")
    public ApiResponse<Object> batchEnable(@RequestBody IntentNodeBatchRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INTENT_TREE_MANAGEMENT);
        return ApiResponses.requireServiceOrError(intentTreePortProvider, port -> {
            port.batchEnable(Objects.requireNonNull(request, "request must not be null").ids());
            return null;
        });
    }

    @PostMapping("/intent-tree/batch/disable")
    public ApiResponse<Object> batchDisable(@RequestBody IntentNodeBatchRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INTENT_TREE_MANAGEMENT);
        return ApiResponses.requireServiceOrError(intentTreePortProvider, port -> {
            port.batchDisable(Objects.requireNonNull(request, "request must not be null").ids());
            return null;
        });
    }

    @PostMapping("/intent-tree/batch/delete")
    public ApiResponse<Object> batchDelete(@RequestBody IntentNodeBatchRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.INTENT_TREE_MANAGEMENT);
        return ApiResponses.requireServiceOrError(intentTreePortProvider, port -> {
            port.batchDelete(Objects.requireNonNull(request, "request must not be null").ids());
            return null;
        });
    }
}
