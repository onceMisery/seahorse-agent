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

import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureHealthAggregator;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Feature Plugin 管理面 Web adapter。
 */
@RestController
public class SeahorsePluginController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<FeatureHealthAggregator> healthAggregator;
    private final ObjectProvider<AgentExtensionStatusPort> statusPort;
    private final ObjectProvider<ExtensionRegistry> extensionRegistry;

    public SeahorsePluginController(ObjectProvider<FeatureHealthAggregator> healthAggregator,
                                    ObjectProvider<AgentExtensionStatusPort> statusPort,
                                    ObjectProvider<ExtensionRegistry> extensionRegistry) {
        this.healthAggregator = Objects.requireNonNull(healthAggregator, "healthAggregator must not be null");
        this.statusPort = Objects.requireNonNull(statusPort, "statusPort must not be null");
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry must not be null");
    }

    @GetMapping("/agent/plugins/health")
    public Map<String, Object> health() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                healthAggregator.getIfAvailable(() -> new FeatureHealthAggregator(List.of(), List.of())).health());
    }

    @GetMapping("/agent/plugins/status")
    public Map<String, Object> statuses() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                statusPort.getIfAvailable(AgentExtensionStatusPort::empty).listStatuses());
    }

    @GetMapping("/agent/plugins/registry")
    public Map<String, Object> registry() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                extensionRegistry.getIfAvailable(ExtensionRegistry::empty).registeredExtensions());
    }

    @PostMapping("/agent/plugins/status")
    public Map<String, Object> saveStatus(@RequestBody PluginStatusRequest request) {
        AgentExtensionStatus status = request.toStatus();
        statusPort.getIfAvailable(AgentExtensionStatusPort::empty).saveStatus(status);
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, status);
    }

    public record PluginStatusRequest(
            String name,
            String portType,
            String featureType,
            String version,
            Boolean enabled,
            Boolean healthy,
            Set<String> capabilities,
            String message,
            String lastError,
            Map<String, Object> details,
            String updatedBy
    ) {

        private AgentExtensionStatus toStatus() {
            return new AgentExtensionStatus(
                    name,
                    portType,
                    featureType,
                    version,
                    Boolean.TRUE.equals(enabled),
                    healthy == null || healthy,
                    capabilities,
                    message,
                    lastError,
                    details,
                    updatedBy,
                    Instant.now());
        }
    }
}
