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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tool Gateway 工具目录管理 API。
 */
@RestController
public class SeahorseToolCatalogController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String SERVICE_NOT_AVAILABLE = "Service not available";
    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<ToolCatalogManagementInboundPort> toolCatalogPortProvider;

    public SeahorseToolCatalogController(ObjectProvider<ToolCatalogManagementInboundPort> toolCatalogPortProvider) {
        this.toolCatalogPortProvider = toolCatalogPortProvider;
    }

    @GetMapping("/api/tools")
    public Map<String, Object> page(@RequestParam(required = false) String resourceType,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size,
                                    @RequestParam(required = false) Boolean enabled) {
        ToolCatalogManagementInboundPort port = requirePort();
        return ok(port.page(resourceType, keyword, current, size, enabled));
    }

    @GetMapping("/api/tools/{toolId}")
    public Map<String, Object> findById(@PathVariable String toolId) {
        ToolCatalogManagementInboundPort port = requirePort();
        return ok(port.findById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found")));
    }

    @PostMapping("/api/tools/{toolId}/enable")
    public Map<String, Object> enable(@PathVariable String toolId) {
        ToolCatalogManagementInboundPort port = requirePort();
        return ok(port.enable(toolId));
    }

    @PostMapping("/api/tools/{toolId}/disable")
    public Map<String, Object> disable(@PathVariable String toolId) {
        ToolCatalogManagementInboundPort port = requirePort();
        return ok(port.disable(toolId));
    }

    private ToolCatalogManagementInboundPort requirePort() {
        ToolCatalogManagementInboundPort port = toolCatalogPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(SERVICE_NOT_AVAILABLE);
        }
        return port;
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }
}
