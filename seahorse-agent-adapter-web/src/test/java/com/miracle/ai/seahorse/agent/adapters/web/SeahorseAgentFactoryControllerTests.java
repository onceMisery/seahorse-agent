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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentPublishValidationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionRollbackCommand;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseAgentFactoryControllerTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeAgentFactoryApis() throws Exception {
        AgentFactoryInboundPort port = mock(AgentFactoryInboundPort.class);
        when(port.listTemplates(false)).thenReturn(List.of(template()));
        when(port.createFromTemplate(any())).thenReturn(definition());
        when(port.validatePublish(any())).thenReturn(report());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentFactoryController(provider(AgentFactoryInboundPort.class, port))).build();

        mvc.perform(get("/api/agent-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].templateId").value("KNOWLEDGE_ASSISTANT"))
                .andExpect(jsonPath("$.data[0].allowedToolIds[0]").value("search"));

        mvc.perform(post("/api/agents/from-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "templateId", "KNOWLEDGE_ASSISTANT",
                                "tenantId", "tenant-a",
                                "agentId", "hr-assistant",
                                "name", "HR Assistant",
                                "description", "Answers HR policy questions",
                                "ownerUserId", "owner-1",
                                "ownerTeam", "people",
                                "requestedToolIds", List.of("search"),
                                "riskLevel", "LOW",
                                "instructionsOverlay", "Only answer approved HR policy questions."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value("hr-assistant"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        ArgumentCaptor<AgentFactoryCreateCommand> createCaptor =
                ArgumentCaptor.forClass(AgentFactoryCreateCommand.class);
        verify(port).createFromTemplate(createCaptor.capture());
        assertThat(createCaptor.getValue().templateId()).isEqualTo(AgentTemplateId.KNOWLEDGE_ASSISTANT);
        assertThat(createCaptor.getValue().requestedToolIds()).containsExactly("search");

        mvc.perform(post("/api/agents/hr-assistant/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "versionId", "hr-assistant-v1",
                                "instructions", "Use approved sources.",
                                "toolIds", List.of("search"),
                                "ownerUserId", "owner-1",
                                "ownerTeam", "people",
                                "changeSummary", "initial release"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WARN"))
                .andExpect(jsonPath("$.data.items[0].code").value("EVAL_PRESENT"));

        ArgumentCaptor<AgentPublishValidationCommand> validateCaptor =
                ArgumentCaptor.forClass(AgentPublishValidationCommand.class);
        verify(port).validatePublish(validateCaptor.capture());
        assertThat(validateCaptor.getValue().agentId()).isEqualTo("hr-assistant");
        assertThat(validateCaptor.getValue().toolIds()).containsExactly("search");
    }

    @Test
    void shouldExposePublishReadyApis() throws Exception {
        AgentFactoryInboundPort port = mock(AgentFactoryInboundPort.class);
        when(port.latestPublishCheck("hr-assistant")).thenReturn(Optional.of(report()));
        when(port.rollback(any())).thenReturn(rollbackResult());
        when(port.catalog(any())).thenReturn(catalogPage());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentFactoryController(provider(AgentFactoryInboundPort.class, port))).build();

        mvc.perform(get("/api/agents/hr-assistant/publish-checks/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.checkId").value("check-1"))
                .andExpect(jsonPath("$.data.status").value("WARN"));

        mvc.perform(post("/api/agents/hr-assistant/versions/hr-assistant-v1/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-a",
                                "operator", "admin-1",
                                "reasonCode", "INCIDENT_RESPONSE",
                                "comment", "rollback after failed validation"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.data.targetVersionId").value("hr-assistant-v1"));

        ArgumentCaptor<AgentVersionRollbackCommand> rollbackCaptor =
                ArgumentCaptor.forClass(AgentVersionRollbackCommand.class);
        verify(port).rollback(rollbackCaptor.capture());
        assertThat(rollbackCaptor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(rollbackCaptor.getValue().agentId()).isEqualTo("hr-assistant");
        assertThat(rollbackCaptor.getValue().versionId()).isEqualTo("hr-assistant-v1");
        assertThat(rollbackCaptor.getValue().reasonCode()).isEqualTo(AgentRollbackReasonCode.INCIDENT_RESPONSE);

        mvc.perform(get("/api/agent-catalog")
                        .queryParam("tenantId", "tenant-a")
                        .queryParam("keyword", "HR")
                        .queryParam("current", "1")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].agentId").value("hr-assistant"))
                .andExpect(jsonPath("$.data.records[0].latestVersionId").value("hr-assistant-v1"));

        ArgumentCaptor<AgentCatalogQuery> catalogCaptor = ArgumentCaptor.forClass(AgentCatalogQuery.class);
        verify(port).catalog(catalogCaptor.capture());
        assertThat(catalogCaptor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(catalogCaptor.getValue().keyword()).isEqualTo("HR");
        assertThat(catalogCaptor.getValue().current()).isEqualTo(1L);
        assertThat(catalogCaptor.getValue().size()).isEqualTo(20L);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static AgentTemplate template() {
        return new AgentTemplate(
                AgentTemplateId.KNOWLEDGE_ASSISTANT,
                AgentTemplateStatus.ENABLED,
                "Knowledge Assistant",
                "Enterprise knowledge assistant",
                AgentType.ASSISTANT,
                AgentRiskLevel.LOW,
                List.of("search", "memory-read"),
                "Answer from approved enterprise knowledge.",
                "{\"grounded\":true}");
    }

    private static AgentDefinition definition() {
        return new AgentDefinition(
                "hr-assistant",
                "tenant-a",
                "HR Assistant",
                "Answers HR policy questions",
                "owner-1",
                "people",
                AgentType.ASSISTANT,
                AgentTemplateId.KNOWLEDGE_ASSISTANT.value(),
                AgentStatus.DRAFT,
                AgentRiskLevel.LOW,
                null,
                NOW,
                NOW);
    }

    private static AgentPublishCheckReport report() {
        return new AgentPublishCheckReport(
                "check-1",
                "hr-assistant",
                "hr-assistant-v1",
                AgentPublishCheckStatus.WARN,
                List.of(AgentPublishCheckItem.warn(
                        AgentPublishCheckCode.EVAL_PRESENT,
                        "Evaluation platform is not enabled yet.")),
                NOW);
    }

    private static AgentRollbackResult rollbackResult() {
        return new AgentRollbackResult(
                "rollback-1",
                "hr-assistant",
                "hr-assistant-v2",
                "hr-assistant-v1",
                AgentRollbackStatus.ROLLED_BACK,
                AgentRollbackReasonCode.INCIDENT_RESPONSE,
                NOW);
    }

    private static AgentCatalogPage catalogPage() {
        return new AgentCatalogPage(
                List.of(new AgentCatalogEntry(
                        "hr-assistant",
                        "tenant-a",
                        "HR Assistant",
                        "Answers HR policy questions",
                        "owner-1",
                        "people",
                        AgentType.ASSISTANT,
                        AgentRiskLevel.LOW,
                        "hr-assistant-v1",
                        NOW)),
                1L,
                20L,
                1L,
                1L);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
