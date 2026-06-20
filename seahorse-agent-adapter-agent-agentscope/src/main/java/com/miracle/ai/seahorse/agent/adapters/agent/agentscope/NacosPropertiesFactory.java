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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.alibaba.nacos.api.PropertyKeyConst;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryTransportProperties;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class NacosPropertiesFactory {

    public static final String PROP_NACOS_AI_MODE = "nacos.ai.mode";
    public static final String PROP_M3_ENABLED = "nacos.ai.m3.enabled";
    public static final String PROP_M3_NAMESPACE = "nacos.ai.m3.namespace";
    public static final String PROP_M3_GROUP = "nacos.ai.m3.group";
    public static final String PROP_M3_CLUSTER_NAME = "nacos.ai.m3.cluster-name";
    public static final String PROP_M3_METADATA_PREFIX = "nacos.ai.m3.metadata.";

    public Properties nacosProperties(AgentScopeProperties properties) {
        AgentScopeProperties safeProperties = Objects.requireNonNull(properties, "properties must not be null");
        AgentScopeProperties.Nacos nacos = safeProperties.getNacos();
        Properties result = new Properties();
        putIfPresent(result, PropertyKeyConst.SERVER_ADDR,
                firstText(safeProperties.getA2a().getNacosServer(), nacos.getServerAddr()));
        putIfPresent(result, PropertyKeyConst.NAMESPACE, nacos.getNamespace());
        putIfPresent(result, PropertyKeyConst.USERNAME, nacos.getUsername());
        putIfPresent(result, PropertyKeyConst.PASSWORD, nacos.getPassword());
        putIfPresent(result, PropertyKeyConst.ACCESS_KEY, nacos.getAccessKey());
        putIfPresent(result, PropertyKeyConst.SECRET_KEY, nacos.getSecretKey());
        nacos.getProperties().forEach((key, value) -> putIfPresent(result, key, value));
        putM3Properties(result, nacos.getM3());
        return result;
    }

    public NacosA2aRegistryProperties a2aRegistryProperties(AgentScopeProperties properties) {
        AgentScopeProperties.A2a a2a = Objects.requireNonNull(properties, "properties must not be null").getA2a();
        NacosA2aRegistryProperties registryProperties = NacosA2aRegistryProperties.builder()
                .setAsLatest(a2a.isSetAsLatest())
                .enabledRegisterEndpoint(a2a.isRegisterEndpoint())
                .overwritePreferredTransport(trimToNull(a2a.getPreferredTransport()))
                .build();
        if (!isBlank(a2a.getTransport())) {
            String transport = transport(a2a.getTransport());
            registryProperties.addTransport(NacosA2aRegistryTransportProperties.builder()
                    .transport(transport)
                    .host(trimToNull(a2a.getHost()))
                    .port(a2a.getPort())
                    .path(trimToNull(a2a.getPath()))
                    .supportTls(a2a.isSupportTls())
                    .protocol(trimToNull(a2a.getProtocol()))
                    .query(trimToNull(a2a.getQuery()))
                    .build());
        }
        return registryProperties;
    }

    private String transport(String value) {
        String trimmed = value.trim();
        return "jsonrpc".equalsIgnoreCase(trimmed) ? TransportProtocol.JSONRPC.asString() : trimmed;
    }

    private void putM3Properties(Properties result, AgentScopeProperties.M3 m3) {
        if (m3 == null || !m3.isEnabled()) {
            return;
        }
        putIfPresent(result, PROP_NACOS_AI_MODE, firstText(m3.getMode(), "M3"));
        putIfPresent(result, PROP_M3_ENABLED, "true");
        putIfPresent(result, PROP_M3_NAMESPACE, m3.getNamespace());
        putIfPresent(result, PROP_M3_GROUP, m3.getGroup());
        putIfPresent(result, PROP_M3_CLUSTER_NAME, m3.getClusterName());
        for (Map.Entry<String, String> entry : m3.getMetadata().entrySet()) {
            putIfPresent(result, PROP_M3_METADATA_PREFIX + entry.getKey(), entry.getValue());
        }
    }

    private static void putIfPresent(Properties properties, String key, String value) {
        if (!isBlank(key) && !isBlank(value)) {
            properties.setProperty(key, value.trim());
        }
    }

    private static String firstText(String first, String second) {
        return isBlank(first) ? Objects.requireNonNullElse(second, "") : first;
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
