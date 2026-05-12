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

import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthStatus;

import java.util.List;
import java.util.Objects;

/**
 * Feature 与 Adapter 健康聚合结果。
 *
 * @param up 是否整体健康
 * @param features Feature 健康状态
 * @param adapters Adapter 健康状态
 */
public record FeatureHealthReport(
        boolean up,
        List<FeatureHealth> features,
        List<AdapterHealthStatus> adapters
) {

    public FeatureHealthReport {
        features = List.copyOf(Objects.requireNonNullElse(features, List.of()));
        adapters = List.copyOf(Objects.requireNonNullElse(adapters, List.of()));
    }
}

