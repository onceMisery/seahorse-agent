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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileAuditSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileProductionGateCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileRiskSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseRunProfileControllerTests {

    @Test
    void shouldInstantiateWhenDiscoveredBySpringContainer() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RunProfileInboundPort.class, () -> mock(RunProfileInboundPort.class));
            context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
            context.register(SeahorseRunProfileController.class);
            context.refresh();

            assertThat(context.getBean(SeahorseRunProfileController.class)).isNotNull();
        }
    }

    @Test
    void shouldCreateRunProfileWithToolBindings() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.save(org.mockito.ArgumentMatchers.any())).thenReturn(12L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(post("/api/run-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Research AgentScope",
                                  "description": "Long research",
                                  "roleCardId": 9,
                                  "executorEngine": "agentscope",
                                  "executorConfig": {"studioTraceEnabled": true},
                                  "modelConfig": {"model": "gpt-4.1-mini"},
                                  "memoryScope": {"longTerm": true},
                                  "guardrailConfig": {"highRiskToolApproval": true},
                                  "toolBindings": [
                                    {"toolId": "filesystem.read_file", "provider": "MCP", "enabled": true}
                                  ]
                                }
                                """)
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(12));

        ArgumentCaptor<RunProfileCommand> captor = ArgumentCaptor.forClass(RunProfileCommand.class);
        verify(port).save(captor.capture());
        RunProfileCommand command = captor.getValue();
        assertThat(command.getUserId()).isEqualTo("100");
        assertThat(command.getName()).isEqualTo("Research AgentScope");
        assertThat(command.getExecutorEngine()).isEqualTo("agentscope");
        assertThat(command.getExecutorConfigJson()).contains("studioTraceEnabled");
        assertThat(command.getToolBindings()).hasSize(1);
        assertThat(command.getToolBindings().get(0).getToolId()).isEqualTo("filesystem.read_file");
    }

    @Test
    void shouldExposeAvailableExecutorEngines() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.supportedExecutorEngines()).thenReturn(List.of("kernel", "agentscope"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(get("/api/run-profiles/executor-engines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0]").value("kernel"))
                .andExpect(jsonPath("$.data[1]").value("agentscope"));

        verify(port).supportedExecutorEngines();
    }

    @Test
    void shouldRejectNonNumericRunProfileIdPathsBeforeControllerInvocation() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(get("/api/run-profiles/not-a-number"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRouteSystemRunProfileNegativeIds() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.resolvePreview("100", -9105L)).thenReturn(Optional.of(RunProfileResolvedPreview.builder()
                .runProfileId(-9105L)
                .roleCardId(-9004L)
                .executorEngine("kernel")
                .explicitToolAllowlist(false)
                .toolIds(List.of())
                .mcpToolIds(List.of())
                .a2aAgentIds(List.of())
                .build()));
        when(port.applyToConversation("100", "101", -9105L)).thenReturn(RunProfileResolvedPreview.builder()
                .runProfileId(-9105L)
                .roleCardId(-9004L)
                .executorEngine("kernel")
                .explicitToolAllowlist(false)
                .toolIds(List.of())
                .mcpToolIds(List.of())
                .a2aAgentIds(List.of())
                .build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(post("/api/run-profiles/-9105/resolve-preview").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runProfileId").value(-9105));
        mvc.perform(post("/api/conversations/101/run-profile/-9105/apply").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runProfileId").value(-9105));

        verify(port).resolvePreview("100", -9105L);
        verify(port).applyToConversation("100", "101", -9105L);
    }

    @Test
    void shouldListGetActivateUpdateAndDeleteRunProfiles() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        RunProfileRecord profile = profile(12L);
        when(port.list("100")).thenReturn(List.of(profile));
        when(port.findById("100", 12L)).thenReturn(Optional.of(RunProfileDetails.builder()
                .profile(profile)
                .toolBindings(List.of(RunProfileToolBindingRecord.builder()
                        .profileId(12L)
                        .toolId("clock")
                        .provider("BUILT_IN")
                        .enabled(1)
                        .build()))
                .build()));
        when(port.save(org.mockito.ArgumentMatchers.any())).thenReturn(12L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(get("/api/run-profiles").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(12));
        mvc.perform(get("/api/run-profiles/12").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.id").value(12))
                .andExpect(jsonPath("$.data.toolBindings[0].toolId").value("clock"));
        mvc.perform(put("/api/run-profiles/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated","executorEngine":"kernel","toolBindings":[]}
                                """)
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(12));
        mvc.perform(post("/api/run-profiles/12/activate").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(delete("/api/run-profiles/12").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).list("100");
        verify(port).findById("100", 12L);
        verify(port).activate("100", 12L);
        verify(port).delete("100", 12L);
    }

    @Test
    void shouldApplyRunProfileToConversation() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.applyToConversation("100", "101", 12L)).thenReturn(RunProfileResolvedPreview.builder()
                .runProfileId(12L)
                .roleCardId(9L)
                .executorEngine("agentscope")
                .explicitToolAllowlist(true)
                .toolIds(List.of("get_current_datetime"))
                .mcpToolIds(List.of())
                .a2aAgentIds(List.of())
                .build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(post("/api/conversations/101/run-profile/12/apply").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.runProfileId").value(12))
                .andExpect(jsonPath("$.data.executorEngine").value("agentscope"));

        verify(port).applyToConversation("100", "101", 12L);
    }

    @Test
    void shouldGetRunProfileAppliedToConversation() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        RunProfileRecord profile = profile(12L);
        when(port.findAppliedToConversation("100", "101")).thenReturn(Optional.of(RunProfileDetails.builder()
                .profile(profile)
                .toolBindings(List.of(RunProfileToolBindingRecord.builder()
                        .profileId(12L)
                        .toolId("clock")
                        .provider("BUILT_IN")
                        .enabled(1)
                        .build()))
                .build()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(get("/api/conversations/101/run-profile").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.profile.id").value(12))
                .andExpect(jsonPath("$.data.profile.executorEngine").value("agentscope"))
                .andExpect(jsonPath("$.data.toolBindings[0].toolId").value("clock"));

        verify(port).findAppliedToConversation("100", "101");
    }

    @Test
    void shouldResolveRunProfilePreviewWithEngineAndToolGroups() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.resolvePreview("100", 12L)).thenReturn(Optional.of(RunProfileResolvedPreview.builder()
                .runProfileId(12L)
                .roleCardId(9L)
                .executorEngine("agentscope")
                .executorConfigJson("{\"studioTraceEnabled\":true}")
                .explicitToolAllowlist(true)
                .toolIds(List.of("get_current_datetime"))
                .mcpToolIds(List.of("filesystem.read_file"))
                .a2aAgentIds(List.of("seahorse-researcher"))
                .build()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(post("/api/run-profiles/12/resolve-preview").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.runProfileId").value(12))
                .andExpect(jsonPath("$.data.roleCardId").value(9))
                .andExpect(jsonPath("$.data.executorEngine").value("agentscope"))
                .andExpect(jsonPath("$.data.explicitToolAllowlist").value(true))
                .andExpect(jsonPath("$.data.toolIds[0]").value("get_current_datetime"))
                .andExpect(jsonPath("$.data.mcpToolIds[0]").value("filesystem.read_file"))
                .andExpect(jsonPath("$.data.a2aAgentIds[0]").value("seahorse-researcher"));

        verify(port).resolvePreview("100", 12L);
    }

    @Test
    void shouldExposeRunProfileRiskSummary() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.riskSummary("100", 12L)).thenReturn(Optional.of(RunProfileRiskSummary.builder()
                .runProfileId(12L)
                .riskLevel("HIGH")
                .riskCodes(List.of("EXECUTOR_AGENTSCOPE", "TOOL_MCP", "TOOL_A2A"))
                .riskItems(List.of(
                        RunProfileRiskSummary.RiskItem.builder()
                                .code("EXECUTOR_AGENTSCOPE")
                                .level("MEDIUM")
                                .message("AgentScope execution engine is enabled")
                                .build(),
                        RunProfileRiskSummary.RiskItem.builder()
                                .code("TOOL_MCP")
                                .level("HIGH")
                                .message("MCP tool is enabled: filesystem.read_file")
                                .build(),
                        RunProfileRiskSummary.RiskItem.builder()
                                .code("TOOL_A2A")
                                .level("HIGH")
                                .message("A2A remote agent is enabled: seahorse-researcher")
                                .build()))
                .build()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(get("/api/run-profiles/12/risk-summary").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.runProfileId").value(12))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.riskCodes[0]").value("EXECUTOR_AGENTSCOPE"))
                .andExpect(jsonPath("$.data.riskItems[1].code").value("TOOL_MCP"));

        verify(port).riskSummary("100", 12L);
    }

    @Test
    void shouldExposeRunProfileProductionGateCheck() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.productionGateCheck("100", 12L)).thenReturn(Optional.of(RunProfileProductionGateCheck.builder()
                .runProfileId(12L)
                .passed(false)
                .riskLevel("HIGH")
                .blockingCodes(List.of("APPROVAL_NOT_ENFORCED"))
                .checkItems(List.of(RunProfileProductionGateCheck.CheckItem.builder()
                        .code("APPROVAL_NOT_ENFORCED")
                        .status("BLOCK")
                        .message("High-risk tool approval must be enabled before production")
                        .build()))
                .build()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(post("/api/run-profiles/12/production-gate/check").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.runProfileId").value(12))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.blockingCodes[0]").value("APPROVAL_NOT_ENFORCED"))
                .andExpect(jsonPath("$.data.checkItems[0].status").value("BLOCK"));

        verify(port).productionGateCheck("100", 12L);
    }

    @Test
    void shouldExposeRunProfileGovernanceActionsAndAuditSummary() throws Exception {
        RunProfileInboundPort port = mock(RunProfileInboundPort.class);
        when(port.auditSummary("100", 12L)).thenReturn(Optional.of(RunProfileAuditSummary.builder()
                .runProfileId(12L)
                .approvalStatus("APPROVED")
                .riskLevel("HIGH")
                .runCount(3L)
                .failureCount(1L)
                .estimatedCost(0.42D)
                .enabledToolCount(2)
                .highRiskToolCount(1)
                .highRiskToolIds(List.of("filesystem.read_file"))
                .build()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseRunProfileController(provider(port))).build();

        mvc.perform(post("/api/run-profiles/12/submit-approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"request production share\"}")
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(post("/api/run-profiles/12/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"admin\",\"comment\":\"approved\"}")
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(post("/api/run-profiles/12/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"security\",\"comment\":\"narrow tools\"}")
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/api/run-profiles/12/audit-summary").param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runProfileId").value(12))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.highRiskToolIds[0]").value("filesystem.read_file"));

        verify(port).submitApproval("100", 12L, "request production share");
        verify(port).approve("100", 12L, "admin", "approved");
        verify(port).reject("100", 12L, "security", "narrow tools");
        verify(port).auditSummary("100", 12L);
    }

    private static RunProfileRecord profile(Long id) {
        RunProfileRecord record = new RunProfileRecord();
        record.setId(id);
        record.setUserId("100");
        record.setName("Research AgentScope");
        record.setExecutorEngine("agentscope");
        record.setEnabled(1);
        return record;
    }

    private static <T> ObjectProvider<T> provider(T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(instance.getClass().getName(), instance);
        return beanFactory.getBeanProvider((Class<T>) instance.getClass().getInterfaces()[0]);
    }
}
