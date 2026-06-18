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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillBindingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseSkillControllerTests {

    private static final Instant NOW = Instant.parse("2026-06-03T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeSkillManagementApi() throws Exception {
        AgentSkillManagementInboundPort managementPort = mock(AgentSkillManagementInboundPort.class);
        AgentSkillBindingInboundPort bindingPort = mock(AgentSkillBindingInboundPort.class);
        AgentSkill skill = skill("research-helper", true);
        when(managementPort.page("tenant-a", 1L, 10L, "research"))
                .thenReturn(new AgentSkillPage(List.of(skill), 1L, 10L, 1L, 1L));
        when(managementPort.find("tenant-a", "research-helper")).thenReturn(Optional.of(skill));
        when(managementPort.createCustom("tenant-a", "# Research")).thenReturn(skill);
        when(managementPort.updateCustom("tenant-a", "research-helper", "# Research v2")).thenReturn(skill);
        when(managementPort.enable("tenant-a", "research-helper")).thenReturn(skill);
        when(managementPort.disable("tenant-a", "research-helper")).thenReturn(skill("research-helper", false));
        when(managementPort.deleteCustom("tenant-a", "research-helper")).thenReturn(skill("research-helper", false));
        when(managementPort.history("tenant-a", "research-helper")).thenReturn(List.of(revision("rev-1")));
        when(managementPort.rollbackCustom("tenant-a", "research-helper", "rev-1")).thenReturn(skill);
        when(managementPort.install("tenant-a", "# Installed")).thenReturn(skill("installed-helper", true));

        MockMvc mvc = mvc(managementPort, bindingPort, AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(get("/api/skills")
                        .param("tenantId", "tenant-a")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("research-helper"));

        mvc.perform(get("/api/skills/research-helper")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestRevisionId").value("rev-1"));

        mvc.perform(post("/api/skills/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantId", "tenant-a", "content", "# Research"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("research-helper"));

        mvc.perform(put("/api/skills/custom/research-helper")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantId", "tenant-a", "content", "# Research v2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("research-helper"));

        mvc.perform(post("/api/skills/research-helper/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantId", "tenant-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mvc.perform(post("/api/skills/research-helper/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantId", "tenant-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mvc.perform(get("/api/skills/custom/research-helper/history")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].revisionId").value("rev-1"));

        mvc.perform(post("/api/skills/custom/research-helper/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantId", "tenant-a", "revisionId", "rev-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("research-helper"));

        mvc.perform(post("/api/skills/install")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantId", "tenant-a", "content", "# Installed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("installed-helper"))
                .andExpect(jsonPath("$.data.category").value("CUSTOM"))
                .andExpect(jsonPath("$.data.source").value("MANUAL"));

        mvc.perform(delete("/api/skills/custom/research-helper")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("research-helper"));
    }

    @Test
    void shouldExposeAgentSkillBindingApi() throws Exception {
        AgentSkillManagementInboundPort managementPort = mock(AgentSkillManagementInboundPort.class);
        AgentSkillBindingInboundPort bindingPort = mock(AgentSkillBindingInboundPort.class);
        when(bindingPort.listBindings("tenant-a", "agent-1"))
                .thenReturn(List.of(binding("research-helper", "rev-1", SkillInjectMode.METADATA_AND_BODY)));
        when(bindingPort.replaceBindings(eq("tenant-a"), eq("agent-1"), any()))
                .thenReturn(List.of(binding("writing-helper", "rev-2", SkillInjectMode.METADATA_ONLY)));
        when(bindingPort.snapshotJson("tenant-a", "agent-1"))
                .thenReturn("{\"skills\":[{\"name\":\"writing-helper\"}]}");

        MockMvc mvc = mvc(managementPort, bindingPort, AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(get("/api/agents/agent-1/skills")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skillName").value("research-helper"));

        mvc.perform(put("/api/agents/agent-1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-a",
                                "bindings", List.of(Map.of(
                                        "skillName", "writing-helper",
                                        "revisionId", "rev-2",
                                        "injectMode", "METADATA_ONLY"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skillName").value("writing-helper"));

        ArgumentCaptor<List<AgentSkillBinding>> bindingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bindingPort).replaceBindings(eq("tenant-a"), eq("agent-1"), bindingsCaptor.capture());
        assertThat(bindingsCaptor.getValue()).hasSize(1);
        assertThat(bindingsCaptor.getValue().get(0).agentId()).isEqualTo("agent-1");
        assertThat(bindingsCaptor.getValue().get(0).injectMode()).isEqualTo(SkillInjectMode.METADATA_ONLY);

        mvc.perform(get("/api/agents/agent-1/skills/snapshot")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("{\"skills\":[{\"name\":\"writing-helper\"}]}"));
    }

    @Test
    void shouldRejectSkillApiWhenFeatureDisabled() throws Exception {
        AgentSkillManagementInboundPort managementPort = mock(AgentSkillManagementInboundPort.class);
        AgentSkillBindingInboundPort bindingPort = mock(AgentSkillBindingInboundPort.class);
        MockMvc mvc = mvc(managementPort, bindingPort, AdvancedFeatureGate.demoDefaults());

        mvc.perform(get("/api/skills"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));

        verifyNoInteractions(managementPort, bindingPort);
    }

    private MockMvc mvc(AgentSkillManagementInboundPort managementPort,
                        AgentSkillBindingInboundPort bindingPort,
                        AdvancedFeatureGate gate) {
        return MockMvcBuilders.standaloneSetup(new SeahorseSkillController(
                        provider(AgentSkillManagementInboundPort.class, managementPort),
                        provider(AgentSkillBindingInboundPort.class, bindingPort),
                        provider(AdvancedFeatureGate.class, gate)))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static AgentSkill skill(String name, boolean enabled) {
        return new AgentSkill(name, "tenant-a", AgentSkillCategory.CUSTOM, AgentSkillSource.MANUAL,
                AgentSkillStatus.ACTIVE, enabled, "rev-1", "Research helper", List.of("analysis"),
                List.of("load_skill"), "admin-1", "admin-1", NOW, NOW);
    }

    private static AgentSkillRevision revision(String revisionId) {
        return new AgentSkillRevision(revisionId, "research-helper", "tenant-a", 1L, "sha256-1",
                "# Research", "{\"name\":\"research-helper\"}", SkillScanDecision.ALLOW,
                "{\"decision\":\"ALLOW\"}", "admin-1", NOW);
    }

    private static AgentSkillBinding binding(String skillName, String revisionId, SkillInjectMode injectMode) {
        return new AgentSkillBinding("agent-1", "tenant-a", skillName, revisionId, injectMode, "admin-1", NOW);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getName(), bean);
        return factory.getBeanProvider(type);
    }
}
