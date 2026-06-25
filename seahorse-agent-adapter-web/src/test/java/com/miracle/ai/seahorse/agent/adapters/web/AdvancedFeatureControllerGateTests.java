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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentHandoffInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiConnectorInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdvancedFeatureControllerGateTests {

    @Test
    void consumerWebModeShouldDisableSandboxApis() throws Exception {
        SandboxRuntimeInboundPort port = mock(SandboxRuntimeInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseSandboxController(
                                provider(SandboxRuntimeInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(post("/api/sandbox/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-a",
                                "runId", "run-1",
                                "runtimeType", "CODE_INTERPRETER",
                                "networkRequested", false,
                                "requestedHosts", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature SANDBOX is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    @Test
    void consumerWebModeShouldDisableAgentHandoffApis() throws Exception {
        AgentHandoffInboundPort port = mock(AgentHandoffInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseAgentHandoffController(
                                provider(AgentHandoffInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(get("/api/agent-runs/run-1/handoffs").param("tenantId", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature AGENT_HANDOFF is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    @Test
    void consumerWebModeShouldDisableConnectorManagementApis() throws Exception {
        OpenApiConnectorInboundPort port = mock(OpenApiConnectorInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseOpenApiConnectorController(
                                provider(OpenApiConnectorInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(get("/api/connectors").param("tenantId", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature CONNECTOR_MANAGEMENT is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    @Test
    void consumerWebModeShouldDisableSecretManagementApis() throws Exception {
        SecretManagementInboundPort port = mock(SecretManagementInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseSecretController(
                                provider(SecretManagementInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(post("/api/secrets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-a",
                                "secretValue", "super-secret-token",
                                "metadataJson", "{}"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature SECRET_MANAGEMENT is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    @Test
    void consumerWebModeShouldDisableIntentTreeApis() throws Exception {
        IntentTreeInboundPort port = mock(IntentTreeInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseIntentTreeController(
                                provider(IntentTreeInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(get("/intent-tree/trees"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature INTENT_TREE_MANAGEMENT is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    @Test
    void consumerWebModeShouldAllowCoreIngestionPipelineApis() throws Exception {
        IngestionPipelineInboundPort port = mock(IngestionPipelineInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseIngestionPipelineController(
                                provider(IngestionPipelineInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(get("/ingestion/pipelines"))
                .andExpect(status().isOk());

        verify(port).page(1, 10, null);
    }

    @Test
    void consumerWebModeShouldAllowCoreToolCatalogApis() throws Exception {
        ToolCatalogManagementInboundPort port = mock(ToolCatalogManagementInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseToolCatalogController(
                                provider(ToolCatalogManagementInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk());

        verify(port).page(null, null, null, null, 1, 10, null);
    }

    @Test
    void consumerWebModeShouldDisableAgentRolloutApis() throws Exception {
        AgentRolloutInboundPort port = mock(AgentRolloutInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseAgentRolloutController(
                                provider(AgentRolloutInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(get("/api/agents/agent-1/versions/v1/rollouts/latest")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature AGENT_ROLLOUT_MANAGEMENT is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    @Test
    void consumerWebModeShouldAllowCoreApprovalManagementApis() throws Exception {
        ApprovalManagementInboundPort port = mock(ApprovalManagementInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new SeahorseApprovalController(
                                provider(ApprovalManagementInboundPort.class, port),
                                AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
        when(port.findById(eq("approval-1"))).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/api/approvals"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/approvals/approval-1"))
                .andExpect(status().isNotFound());

        verify(port).page(null, null, 1, 10);
        verify(port).findById("approval-1");
    }

    private static String json(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
