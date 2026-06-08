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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Spring Boot binding model for Seahorse infrastructure adapter options.
 */
@ConfigurationProperties(prefix = "seahorse-agent.adapters")
public class AgentAdapterProperties {

    private Map<String, String> selectedAdapters = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> adapterSettings = new LinkedHashMap<>();

    public Map<String, String> getSelectedAdapters() {
        return selectedAdapters;
    }

    public void setSelectedAdapters(Map<String, String> selectedAdapters) {
        this.selectedAdapters = new LinkedHashMap<>(Objects.requireNonNullElse(selectedAdapters, Map.of()));
    }

    public Map<String, Map<String, Object>> getAdapterSettings() {
        return adapterSettings;
    }

    public void setAdapterSettings(Map<String, Map<String, Object>> adapterSettings) {
        this.adapterSettings = new LinkedHashMap<>(Objects.requireNonNullElse(adapterSettings, Map.of()));
    }

    public String adapterName(String portName) {
        if (portName == null || portName.isBlank()) {
            return "";
        }
        return selectedAdapters.getOrDefault(portName, "");
    }
}
