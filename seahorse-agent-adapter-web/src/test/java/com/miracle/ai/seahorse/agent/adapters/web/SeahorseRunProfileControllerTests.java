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
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
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
