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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SandboxApiDisabledByDefaultTests {

    @Test
    void demoDefaultsShouldRejectEverySandboxEndpointBeforeCallingPort() throws Exception {
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
                .andExpect(jsonPath("$.message")
                        .value("Advanced feature SANDBOX is disabled in DEMO mode"));

        mvc.perform(post("/api/sandbox/sessions/session-1/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "input", "print('hello')",
                                "networkRequested", false,
                                "requestedHosts", List.of()))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/sandbox/sessions/session-1/close"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/sandbox/sessions/session-1/artifacts"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(port);
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
