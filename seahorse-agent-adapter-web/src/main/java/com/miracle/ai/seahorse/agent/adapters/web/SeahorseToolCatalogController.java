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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool Gateway 工具目录管理 API。
 *
 * <p>Slice 6 第一刀：从 {@code Map.of(code, data)} 重构为 {@link ApiResponse} +
 * {@link ApiResponses#requireService(ObjectProvider, java.util.function.Function)}。
 * 响应形状（{@code {code, data}} 或 {@code {code, message}} 与历史一致；spec §11.3 约束）。
 */
@RestController
public class SeahorseToolCatalogController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<ToolCatalogManagementInboundPort> toolCatalogPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseToolCatalogController(ObjectProvider<ToolCatalogManagementInboundPort> toolCatalogPortProvider) {
        this(toolCatalogPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseToolCatalogController(ObjectProvider<ToolCatalogManagementInboundPort> toolCatalogPortProvider,
                                         ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(toolCatalogPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults));
    }

    public SeahorseToolCatalogController(ObjectProvider<ToolCatalogManagementInboundPort> toolCatalogPortProvider,
                                         AdvancedFeatureGate advancedFeatureGate) {
        this.toolCatalogPortProvider = toolCatalogPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @GetMapping({"/tools", "/api/tools"})
    public ApiResponse<Object> page(@RequestParam(required = false) String resourceType,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size,
                                    @RequestParam(required = false) Boolean enabled) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.TOOL_CATALOG_MANAGEMENT);
        return ApiResponses.requireService(toolCatalogPortProvider,
                port -> port.page(resourceType, keyword, current, size, enabled));
    }

    @GetMapping({"/tools/{toolId}", "/api/tools/{toolId}"})
    public ApiResponse<Object> findById(@PathVariable String toolId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.TOOL_CATALOG_MANAGEMENT);
        return ApiResponses.requireService(toolCatalogPortProvider,
                port -> port.findById(toolId).orElseThrow(() -> new ResourceNotFoundException("Tool not found")));
    }

    @PostMapping({"/tools/{toolId}/enable", "/api/tools/{toolId}/enable"})
    public ApiResponse<Object> enable(@PathVariable String toolId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.TOOL_CATALOG_MANAGEMENT);
        return ApiResponses.requireService(toolCatalogPortProvider, port -> port.enable(toolId));
    }

    @PostMapping({"/tools/{toolId}/disable", "/api/tools/{toolId}/disable"})
    public ApiResponse<Object> disable(@PathVariable String toolId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.TOOL_CATALOG_MANAGEMENT);
        return ApiResponses.requireService(toolCatalogPortProvider, port -> port.disable(toolId));
    }
}
