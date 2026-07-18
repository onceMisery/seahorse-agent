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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeAdmissionOverride;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeMaintenanceStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeNodeRegistryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseSandboxRuntimeNodeRegistryControllerTests {

    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void adminShouldDrainAndResumeRuntimeNodeWithOperatorIdentity() throws Exception {
        SandboxRuntimeNodeRegistryInboundPort registry = mock(SandboxRuntimeNodeRegistryInboundPort.class);
        when(registry.setOperatorDraining("sandbox-node-b", true, "operator-admin", "default"))
                .thenReturn(new SandboxRuntimeNodeAdmissionOverride(
                        "sandbox-node-b", true, "operator-admin", NOW));
        when(registry.setOperatorDraining("sandbox-node-b", false, "operator-admin", "default"))
                .thenReturn(new SandboxRuntimeNodeAdmissionOverride(
                        "sandbox-node-b", false, "operator-admin", NOW.plusSeconds(1)));
        MockMvc mvc = mvc(registry, currentUser("operator-admin", "admin"),
                AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(post("/api/admin/sandbox/runtime/registrations/sandbox-node-b/drain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeId").value("sandbox-node-b"))
                .andExpect(jsonPath("$.data.draining").value(true))
                .andExpect(jsonPath("$.data.operatorId").value("operator-admin"));

        mvc.perform(post("/api/admin/sandbox/runtime/registrations/sandbox-node-b/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeId").value("sandbox-node-b"))
                .andExpect(jsonPath("$.data.draining").value(false))
                .andExpect(jsonPath("$.data.operatorId").value("operator-admin"));

        verify(registry).setOperatorDraining("sandbox-node-b", true, "operator-admin", "default");
        verify(registry).setOperatorDraining("sandbox-node-b", false, "operator-admin", "default");
    }

    @Test
    void adminShouldReadDatabaseBackedMaintenanceStatus() throws Exception {
        SandboxRuntimeNodeRegistryInboundPort registry = mock(SandboxRuntimeNodeRegistryInboundPort.class);
        when(registry.maintenanceStatus("sandbox-node-b"))
                .thenReturn(new SandboxRuntimeNodeMaintenanceStatus(
                        "sandbox-node-b", true, 1, 2, true, 1, NOW.minusSeconds(60), NOW));
        MockMvc mvc = mvc(registry, currentUser("operator-admin", "admin"),
                AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(get("/api/admin/sandbox/runtime/registrations/sandbox-node-b/maintenance-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeId").value("sandbox-node-b"))
                .andExpect(jsonPath("$.data.operatorDraining").value(true))
                .andExpect(jsonPath("$.data.persistedActiveSessionCount").value(1))
                .andExpect(jsonPath("$.data.pendingReservationCount").value(2))
                .andExpect(jsonPath("$.data.createOperationTrackingAvailable").value(true))
                .andExpect(jsonPath("$.data.inFlightCreateOperationCount").value(1))
                .andExpect(jsonPath("$.data.stabilizationElapsed").value(false))
                .andExpect(jsonPath("$.data.maintenanceReady").value(false));

        verify(registry).maintenanceStatus("sandbox-node-b");
    }

    @Test
    void nonAdminShouldNotAccessRuntimeNodeAdminOperations() throws Exception {
        SandboxRuntimeNodeRegistryInboundPort registry = mock(SandboxRuntimeNodeRegistryInboundPort.class);
        MockMvc mvc = mvc(registry, currentUser("ordinary-user", "user"),
                AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(post("/api/admin/sandbox/runtime/registrations/sandbox-node-b/drain"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/admin/sandbox/runtime/registrations/sandbox-node-b/maintenance-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/admin/sandbox/runtime/registrations"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(registry);
    }

    @Test
    void disabledSandboxFeatureShouldRejectAdmissionCommandsBeforeCallingPorts() throws Exception {
        SandboxRuntimeNodeRegistryInboundPort registry = mock(SandboxRuntimeNodeRegistryInboundPort.class);
        CurrentUserPort currentUser = currentUser("operator-admin", "admin");
        MockMvc mvc = mvc(registry, currentUser, AdvancedFeatureGate.demoDefaults());

        mvc.perform(post("/api/admin/sandbox/runtime/registrations/sandbox-node-b/drain"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));

        mvc.perform(post("/api/admin/sandbox/runtime/registrations/sandbox-node-b/resume"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));

        mvc.perform(get("/api/admin/sandbox/runtime/registrations/sandbox-node-b/maintenance-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));

        verifyNoInteractions(registry);
    }

    private static MockMvc mvc(SandboxRuntimeNodeRegistryInboundPort registry,
                               CurrentUserPort currentUser,
                               AdvancedFeatureGate featureGate) {
        return MockMvcBuilders.standaloneSetup(new SeahorseSandboxRuntimeNodeRegistryController(
                        provider(SandboxRuntimeNodeRegistryInboundPort.class, registry),
                        provider(CurrentUserPort.class, currentUser),
                        featureGate))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }

    private static CurrentUserPort currentUser(String username, String role) {
        return () -> Optional.of(new CurrentUser(1L, username, role, null, "default"));
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
