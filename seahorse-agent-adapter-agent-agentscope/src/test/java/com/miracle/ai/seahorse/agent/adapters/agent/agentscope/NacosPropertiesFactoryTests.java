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
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosPropertiesFactoryTests {

    private final NacosPropertiesFactory factory = new NacosPropertiesFactory();

    @Test
    void buildsNacos3PropertiesForRegistryAndConfigCenterWithM3Metadata() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getNacos().setServerAddr("nacos.example:8848");
        properties.getNacos().setNamespace("tenant-ns");
        properties.getNacos().setUsername("nacos");
        properties.getNacos().setPassword("secret");
        properties.getNacos().getM3().setEnabled(true);
        properties.getNacos().getM3().setNamespace("m3-ns");
        properties.getNacos().getM3().setGroup("m3-group");
        properties.getNacos().getM3().setClusterName("m3-cluster");
        properties.getNacos().getM3().setMetadata(Map.of("zone", "cn-shanghai"));

        Properties result = factory.nacosProperties(properties);

        assertEquals("nacos.example:8848", result.getProperty(PropertyKeyConst.SERVER_ADDR));
        assertEquals("tenant-ns", result.getProperty(PropertyKeyConst.NAMESPACE));
        assertEquals("nacos", result.getProperty(PropertyKeyConst.USERNAME));
        assertEquals("secret", result.getProperty(PropertyKeyConst.PASSWORD));
        assertEquals("M3", result.getProperty(NacosPropertiesFactory.PROP_NACOS_AI_MODE));
        assertEquals("true", result.getProperty(NacosPropertiesFactory.PROP_M3_ENABLED));
        assertEquals("m3-ns", result.getProperty(NacosPropertiesFactory.PROP_M3_NAMESPACE));
        assertEquals("m3-group", result.getProperty(NacosPropertiesFactory.PROP_M3_GROUP));
        assertEquals("m3-cluster", result.getProperty(NacosPropertiesFactory.PROP_M3_CLUSTER_NAME));
        assertEquals("cn-shanghai", result.getProperty(NacosPropertiesFactory.PROP_M3_METADATA_PREFIX + "zone"));
    }

    @Test
    void buildsA2aRegistryEndpointProperties() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setSetAsLatest(true);
        properties.getA2a().setRegisterEndpoint(true);
        properties.getA2a().setTransport("jsonrpc");
        properties.getA2a().setHost("127.0.0.1");
        properties.getA2a().setPort(8080);
        properties.getA2a().setPath("/a2a");

        NacosA2aRegistryProperties result = factory.a2aRegistryProperties(properties);

        assertTrue(result.isSetAsLatest());
        assertTrue(result.enabledRegisterEndpoint());
        String transport = TransportProtocol.JSONRPC.asString();
        assertEquals(transport, result.transportProperties().get(transport).transport());
        assertEquals("127.0.0.1", result.transportProperties().get(transport).host());
        assertEquals(8080, result.transportProperties().get(transport).port());
    }
}
