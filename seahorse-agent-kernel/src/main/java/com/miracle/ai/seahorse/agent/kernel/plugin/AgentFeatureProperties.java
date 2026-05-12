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

import java.util.Map;
import java.util.Objects;

/**
 * Feature 激活配置快照。
 * <p>
 * 该对象面向内核请求链路使用，是启动期配置解析后的只读视图。
 * Spring 配置类可以在启动时转换为该对象，避免请求期反复读取松散 Map。
 *
 * @param enabledFeatures  按 Feature 名称声明的启用开关
 * @param defaultEnabled   未显式配置时是否默认启用
 * @param featureSettings  Feature 透传配置，具体 Feature 自行解释
 */
public record AgentFeatureProperties(
        Map<String, Boolean> enabledFeatures,
        boolean defaultEnabled,
        Map<String, Map<String, Object>> featureSettings
) {

    /**
     * 构造只读配置快照。
     * <p>
     * Map 会被防御性复制，调用方后续修改原始配置不会影响请求链路。
     */
    public AgentFeatureProperties {
        enabledFeatures = Map.copyOf(Objects.requireNonNullElse(enabledFeatures, Map.of()));
        featureSettings = Map.copyOf(Objects.requireNonNullElse(featureSettings, Map.of()));
    }

    /**
     * 创建默认启用的空配置。
     *
     * @return 默认配置快照
     */
    public static AgentFeatureProperties empty() {
        return new AgentFeatureProperties(Map.of(), true, Map.of());
    }

    /**
     * 判断指定 Feature 是否被配置启用。
     *
     * @param featureName Feature 名称
     * @return true 表示启用，false 表示关闭
     */
    public boolean enabled(String featureName) {
        return enabled(featureName, defaultEnabled);
    }

    /**
     * 判断指定 Feature 是否被配置启用，并允许描述符提供默认启用值。
     *
     * @param featureName                 Feature 名称
     * @param descriptorEnabledByDefault  描述符默认启用值
     * @return true 表示启用，false 表示关闭
     */
    public boolean enabled(String featureName, boolean descriptorEnabledByDefault) {
        if (featureName == null || featureName.isBlank()) {
            return descriptorEnabledByDefault && defaultEnabled;
        }
        return enabledFeatures.getOrDefault(featureName, descriptorEnabledByDefault && defaultEnabled);
    }

    /**
     * 获取指定 Feature 的透传配置。
     *
     * @param featureName Feature 名称
     * @return 不可变配置 Map
     */
    public Map<String, Object> settings(String featureName) {
        if (featureName == null || featureName.isBlank()) {
            return Map.of();
        }
        return featureSettings.getOrDefault(featureName, Map.of());
    }
}
