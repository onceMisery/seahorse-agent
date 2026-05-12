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

package com.miracle.ai.seahorse.agent.kernel.plugin;

import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthIndicatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthStatus;

import java.util.List;
import java.util.Objects;

/**
 * Feature 与 Adapter 健康状态聚合器。
 *
 * <p>该聚合器只在诊断入口或启动检查中调用，不进入请求主链路。
 */
public class FeatureHealthAggregator {

    private final List<AgentFeature> features;
    private final List<AdapterHealthIndicatorPort> adapterIndicators;

    public FeatureHealthAggregator(List<AgentFeature> features,
                                   List<AdapterHealthIndicatorPort> adapterIndicators) {
        this.features = List.copyOf(Objects.requireNonNullElse(features, List.of()));
        this.adapterIndicators = List.copyOf(Objects.requireNonNullElse(adapterIndicators, List.of()));
    }

    public FeatureHealthReport health() {
        List<FeatureHealth> featureHealth = features.stream()
                .map(this::featureHealth)
                .toList();
        List<AdapterHealthStatus> adapterHealth = adapterIndicators.stream()
                .filter(Objects::nonNull)
                .map(AdapterHealthIndicatorPort::health)
                .toList();
        boolean up = featureHealth.stream().allMatch(FeatureHealth::up)
                && adapterHealth.stream().allMatch(AdapterHealthStatus::up);
        return new FeatureHealthReport(up, featureHealth, adapterHealth);
    }

    private FeatureHealth featureHealth(AgentFeature feature) {
        try {
            return feature.health();
        } catch (RuntimeException ex) {
            return FeatureHealth.down(feature.name(), ex.getMessage());
        }
    }
}

