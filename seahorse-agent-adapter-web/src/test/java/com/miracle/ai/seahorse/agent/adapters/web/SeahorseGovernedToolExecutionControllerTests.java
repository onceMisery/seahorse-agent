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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolExecutionPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolPermission;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseGovernedToolExecutionControllerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldPreflightToolInvocationThroughGovernedGateway() throws Exception {
        GovernedToolExecutionPort port = mock(GovernedToolExecutionPort.class);
        when(port.preflight(any(ToolInvocationRequest.class))).thenReturn(
                GovernedToolPermission.approvalRequired(
                        "approval:1",
                        ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                        "Tool requires approval"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseGovernedToolExecutionController(
                        provider(GovernedToolExecutionPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests()))
                .build();

        mvc.perform(post("/api/tools/echo/preflight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(preflightPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.effect").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.data.approvalId").value("approval:1"))
                .andExpect(jsonPath("$.data.reasonCode").value(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED));

        ArgumentCaptor<ToolInvocationRequest> requestCaptor = ArgumentCaptor.forClass(ToolInvocationRequest.class);
        verify(port).preflight(requestCaptor.capture());
        ToolInvocationRequest request = requestCaptor.getValue();
        assertThat(request.toolId()).isEqualTo("echo");
        assertThat(request.runId()).isEqualTo("run-1");
        assertThat(request.stepId()).isEqualTo("step-1");
        assertThat(request.toolCallId()).isEqualTo("call-1");
        assertThat(request.agentId()).isEqualTo("legacy-react-agent");
        assertThat(request.versionId()).isEqualTo("version-1");
        assertThat(request.rolloutId()).isEqualTo("rollout-1");
        assertThat(request.tenantId()).isEqualTo("tenant-a");
        assertThat(request.userId()).isEqualTo("user-1");
        assertThat(request.agentIdentityId()).isEqualTo("identity-1");
        assertThat(request.arguments()).containsEntry("text", "hello");
        assertThat(request.resourceRefs()).containsEntry("document", "doc-1");
        assertThat(request.idempotencyKey()).isEqualTo("run-1:call-1");
        assertThat(request.allowedToolIds()).containsExactly("echo");
    }

    @Test
    void shouldInvokeToolThroughGovernedGateway() throws Exception {
        GovernedToolExecutionPort port = mock(GovernedToolExecutionPort.class);
        when(port.invoke(any(ToolInvocationRequest.class))).thenReturn(
                ToolInvocationResult.ok("{\"statusCode\":200,\"body\":{\"ok\":true}}"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseGovernedToolExecutionController(
                        provider(GovernedToolExecutionPort.class, port),
                        AdvancedFeatureGate.allEnabledForTests()))
                .build();

        mvc.perform(post("/api/tools/echo/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(preflightPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.content")
                        .value("{\"statusCode\":200,\"body\":{\"ok\":true}}"));

        ArgumentCaptor<ToolInvocationRequest> requestCaptor = ArgumentCaptor.forClass(ToolInvocationRequest.class);
        verify(port).invoke(requestCaptor.capture());
        ToolInvocationRequest request = requestCaptor.getValue();
        assertThat(request.toolId()).isEqualTo("echo");
        assertThat(request.runId()).isEqualTo("run-1");
        assertThat(request.stepId()).isEqualTo("step-1");
        assertThat(request.toolCallId()).isEqualTo("call-1");
        assertThat(request.arguments()).containsEntry("text", "hello");
        assertThat(request.idempotencyKey()).isEqualTo("run-1:call-1");
        assertThat(request.allowedToolIds()).containsExactly("echo");
    }

    @Test
    void consumerWebModeShouldDisableToolPreflight() throws Exception {
        GovernedToolExecutionPort port = mock(GovernedToolExecutionPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseGovernedToolExecutionController(
                        provider(GovernedToolExecutionPort.class, port),
                        AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(post("/api/tools/echo/preflight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stepId", "step-1",
                                "toolCallId", "call-1",
                                "allowedToolIds", List.of("echo")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature AGENT_RUN_MANAGEMENT is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    @Test
    void consumerWebModeShouldDisableToolInvoke() throws Exception {
        GovernedToolExecutionPort port = mock(GovernedToolExecutionPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseGovernedToolExecutionController(
                        provider(GovernedToolExecutionPort.class, port),
                        AdvancedFeatureGate.demoDefaults()))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(post("/api/tools/echo/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "stepId", "step-1",
                                "toolCallId", "call-1",
                                "allowedToolIds", List.of("echo")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature AGENT_RUN_MANAGEMENT is disabled in DEMO mode"));

        verifyNoInteractions(port);
    }

    private static String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, Object> preflightPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", "run-1");
        payload.put("stepId", "step-1");
        payload.put("toolCallId", "call-1");
        payload.put("agentId", "legacy-react-agent");
        payload.put("versionId", "version-1");
        payload.put("rolloutId", "rollout-1");
        payload.put("tenantId", "tenant-a");
        payload.put("userId", "user-1");
        payload.put("agentIdentityId", "identity-1");
        payload.put("arguments", Map.of("text", "hello"));
        payload.put("resourceRefs", Map.of("document", "doc-1"));
        payload.put("idempotencyKey", "run-1:call-1");
        payload.put("allowedToolIds", List.of("echo"));
        return payload;
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
