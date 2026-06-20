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

import java.util.Objects;
import java.util.Properties;

public class NacosPropertiesFactory {

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
