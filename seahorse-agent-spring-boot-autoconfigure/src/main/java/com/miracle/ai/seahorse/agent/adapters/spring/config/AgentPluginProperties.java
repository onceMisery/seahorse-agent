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

package com.miracle.ai.seahorse.agent.adapters.spring.config;

import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeatureProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Spring Boot binding model for Seahorse feature plugin options.
 */
@ConfigurationProperties(prefix = "seahorse-agent.plugins")
public class AgentPluginProperties {

    private boolean defaultEnabled = true;
    private Map<String, Boolean> enabledFeatures = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> featureSettings = new LinkedHashMap<>();

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public Map<String, Boolean> getEnabledFeatures() {
        return enabledFeatures;
    }

    public void setEnabledFeatures(Map<String, Boolean> enabledFeatures) {
        this.enabledFeatures = new LinkedHashMap<>(Objects.requireNonNullElse(enabledFeatures, Map.of()));
    }

    public Map<String, Map<String, Object>> getFeatureSettings() {
        return featureSettings;
    }

    public void setFeatureSettings(Map<String, Map<String, Object>> featureSettings) {
        this.featureSettings = new LinkedHashMap<>(Objects.requireNonNullElse(featureSettings, Map.of()));
    }

    public AgentFeatureProperties toFeatureProperties() {
        return new AgentFeatureProperties(enabledFeatures, defaultEnabled, featureSettings);
    }
}
