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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureHealthAggregator;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginResult;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.dashboard.DashboardInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexRebuildResult;
import com.miracle.ai.seahorse.agent.ports.inbound.mapping.QueryTermMappingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceRunResult;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunResult;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceTaskOutcome;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryPage;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationResult;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillRunResult;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataDictionaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataExtractionResultInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCaseResult;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategyDelta;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardKpi;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardKpiGroup;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardPerformance;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrends;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodeTree;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPage;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryHealthReport;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunAggregate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerFeedbackExportRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPendingSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillCountItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillOperationsOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverageDelta;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonDelta;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineReasonCount;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewFeedbackSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldCapabilityRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.VersionQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseWebApiContractTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepAuthAndUserApiContracts() throws Exception {
        AuthInboundPort authPort = mock(AuthInboundPort.class);
        when(authPort.login(any())).thenReturn(new LoginResult("1", "admin", "token-1", "avatar.png"));

        UserInboundPort userPort = mock(UserInboundPort.class);
        when(userPort.currentUser()).thenReturn(new CurrentUser("1", "admin", "admin", "avatar.png"));
        when(userPort.page(anyLong(), anyLong(), any())).thenReturn(new UserPage(List.of(), 0, 10, 1, 0));
        when(userPort.create(any())).thenReturn("2");

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAuthController(provider(AuthInboundPort.class, authPort)),
                new SeahorseUserController(provider(UserInboundPort.class, userPort))).build();

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.token").value("token-1"));

        mvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));

        mvc.perform(get("/users").param("current", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "operator", "password", "pw", "role", "user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2"));

        mvc.perform(put("/users/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "operator", "password", "pw", "role", "user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mvc.perform(delete("/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "old", "newPassword", "new"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldKeepAgentRegistryAndRunStoreApiContracts() throws Exception {
        AgentDefinitionInboundPort definitionPort = mock(AgentDefinitionInboundPort.class);
        AgentDefinition definition = agentDefinition(AgentStatus.DRAFT);
        when(definitionPort.createDraft(any())).thenReturn("agent-1");
        when(definitionPort.page("tenant-a", 1L, 10L, "agent"))
                .thenReturn(new AgentDefinitionPage(List.of(definition), 1L, 10L, 1L, 1L));
        when(definitionPort.findById("agent-1")).thenReturn(Optional.of(definition));
        when(definitionPort.updateDraft(eq("agent-1"), any())).thenReturn(definition);
        when(definitionPort.publish(eq("agent-1"), any())).thenReturn(agentVersion());
        when(definitionPort.disable("agent-1")).thenReturn(agentDefinition(AgentStatus.DISABLED));

        AgentRunInboundPort runPort = mock(AgentRunInboundPort.class);
        when(runPort.startRun(any())).thenReturn(agentRun(AgentRunStatus.RUNNING));
        when(runPort.findRunById("run-1")).thenReturn(Optional.of(agentRun(AgentRunStatus.RUNNING)));
        when(runPort.listSteps("run-1")).thenReturn(List.of(agentStep()));
        when(runPort.cancel("run-1")).thenReturn(agentRun(AgentRunStatus.CANCELLED));
        when(runPort.retry("run-1")).thenReturn(agentRun(AgentRunStatus.RETRYING));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseAgentDefinitionController(provider(AgentDefinitionInboundPort.class, definitionPort)),
                new SeahorseAgentRunController(provider(AgentRunInboundPort.class, runPort))).build();

        mvc.perform(post("/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "agentId", "agent-1",
                                "tenantId", "tenant-a",
                                "name", "Agent One",
                                "description", "desc",
                                "ownerUserId", "owner-1",
                                "ownerTeam", "platform",
                                "agentType", "WORKFLOW",
                                "riskLevel", "HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("agent-1"));

        ArgumentCaptor<AgentDefinitionCreateCommand> createCaptor =
                ArgumentCaptor.forClass(AgentDefinitionCreateCommand.class);
        verify(definitionPort).createDraft(createCaptor.capture());
        assertThat(createCaptor.getValue().agentType()).isEqualTo(AgentType.WORKFLOW);
        assertThat(createCaptor.getValue().riskLevel()).isEqualTo(AgentRiskLevel.HIGH);

        mvc.perform(get("/agents")
                        .param("tenantId", "tenant-a")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].agentId").value("agent-1"));

        mvc.perform(get("/agents/agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value("agent-1"));

        mvc.perform(put("/agents/agent-1/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Agent One",
                                "description", "desc",
                                "ownerTeam", "platform",
                                "agentType", "DOMAIN",
                                "riskLevel", "MEDIUM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        verify(definitionPort).updateDraft(eq("agent-1"), any(AgentDefinitionUpdateDraftCommand.class));

        mvc.perform(post("/agents/agent-1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "instructions", "Do the work",
                                "toolSetJson", "{}",
                                "modelConfigJson", "{}",
                                "memoryConfigJson", "{}",
                                "guardrailConfigJson", "{}",
                                "changeSummary", "initial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value("agent-1-v1"));
        verify(definitionPort).publish(eq("agent-1"), any(AgentVersionPublishCommand.class));

        mvc.perform(post("/agents/agent-1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mvc.perform(post("/agents/agent-1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "versionId", "agent-1-v1",
                                "tenantId", "tenant-a",
                                "conversationId", "conversation-1",
                                "triggerType", "CHAT",
                                "inputSummary", "summary",
                                "traceId", "trace-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"));

        ArgumentCaptor<AgentRunStartCommand> runCaptor = ArgumentCaptor.forClass(AgentRunStartCommand.class);
        verify(runPort).startRun(runCaptor.capture());
        assertThat(runCaptor.getValue().agentId()).isEqualTo("agent-1");
        assertThat(runCaptor.getValue().versionId()).isEqualTo("agent-1-v1");
        assertThat(runCaptor.getValue().triggerType()).isEqualTo(AgentRunTriggerType.CHAT);

        mvc.perform(get("/agent-runs/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mvc.perform(get("/agent-runs/run-1/steps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stepId").value("step-1"));

        mvc.perform(post("/agent-runs/run-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mvc.perform(post("/agent-runs/run-1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETRYING"));

        mvc.perform(post("/api/agent-runs/run-1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETRYING"));
    }

    @Test
    void shouldKeepChatApiContracts() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        ChatStreamCallbackFactoryPort callbackFactory = (emitter, conversationId, taskId) -> noopCallback();
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseChatController(provider(ChatInboundPort.class, chatPort), callbackFactory,
                        streamTaskPort, 1_000L)).build();

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "hello")
                        .param("conversationId", "c1")
                        .param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "hello")
                        .param("conversationId", "c2")
                        .param("userId", "u1")
                        .param("chatMode", "agent"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "hello")
                        .param("conversationId", "c3")
                        .param("userId", "u1")
                        .param("chatMode", "invalid"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "hello")
                        .param("conversationId", "c4")
                        .param("userId", "u1")
                        .param("chatMode", "agent")
                        .param("agentId", " agent-1 ")
                        .param("versionId", " agent-1-v2 "))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<StreamChatCommand> commandCaptor = ArgumentCaptor.forClass(StreamChatCommand.class);
        verify(chatPort, times(4)).streamChat(commandCaptor.capture(), any());
        assertThat(commandCaptor.getAllValues())
                .extracting(StreamChatCommand::chatMode)
                .containsExactly(ChatMode.RAG, ChatMode.AGENT, ChatMode.RAG, ChatMode.AGENT);
        StreamChatCommand agentCommand = commandCaptor.getAllValues().get(3);
        assertThat(agentCommand.agentId()).isEqualTo("agent-1");
        assertThat(agentCommand.versionId()).isEqualTo("agent-1-v2");

        mvc.perform(post("/rag/v3/stop").param("taskId", "task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts() throws Exception {
        RagTraceInboundPort tracePort = mock(RagTraceInboundPort.class);
        when(tracePort.pageRuns(any())).thenReturn(new RagTracePage<>(1, 10, 0, List.of()));
        when(tracePort.detail("trace-1")).thenReturn(new RagTraceDetail(null, List.of()));
        when(tracePort.listNodes("trace-1")).thenReturn(List.of());

        KnowledgeBaseInboundPort knowledgeBasePort = mock(KnowledgeBaseInboundPort.class);
        when(knowledgeBasePort.create(any())).thenReturn("kb-1");
        when(knowledgeBasePort.queryById("kb-1")).thenReturn(knowledgeBase("kb-1"));
        when(knowledgeBasePort.page(any())).thenReturn(new KnowledgeBasePage(List.of(), 0, 10, 1, 0));
        when(knowledgeBasePort.listChunkStrategies()).thenReturn(List.of());

        MemoryManagementInboundPort memoryManagementPort = mock(MemoryManagementInboundPort.class);
        when(memoryManagementPort.listMemories(eq("u1"), any(), any(), anyInt()))
                .thenReturn(new MemoryPage("short_term", List.of()));
        when(memoryManagementPort.findMemory("short_term", "m1")).thenReturn(Optional.empty());
        when(memoryManagementPort.deleteMemory("short_term", "m1")).thenReturn(true);
        when(memoryManagementPort.listQualitySnapshots("u1", 20)).thenReturn(List.of());
        when(memoryManagementPort.listConflicts("u1", null, 20)).thenReturn(List.of());
        when(memoryManagementPort.resolveConflict("c1", "merge", "u1")).thenReturn(true);
        when(memoryManagementPort.listProfileFacts("u1", "default", 20))
                .thenReturn(List.of(new ProfileFact(
                        "pf-1", "u1", "default", "occupation", "学生", 0.95,
                        "explicit", "gen-1", "ACTIVE", Instant.EPOCH)));
        when(memoryManagementPort.listCorrectionRules("u1", "default", 20))
                .thenReturn(List.of(new CorrectionRule(
                        "cr-1", "u1", "default", "REPLACE", "PROFILE_SLOT",
                        "occupation", "学生", "老师", "职业应以老师为准",
                        "HARD_RULE", "gen-2", "ACTIVE", Instant.EPOCH)));
        when(memoryManagementPort.listOperations("u1", "default", null, 20))
                .thenReturn(List.of(new MemoryOperationRecord(
                        "op-1", "u1", "default", "PROFILE_UPSERT", "PROFILE_SLOT",
                        "occupation", Map.of("value", "学生"), Map.of("accepted", true),
                        "APPLIED", "policy-v1", "", Instant.EPOCH, Instant.EPOCH)));
        when(memoryManagementPort.listOutboxTasks(20))
                .thenReturn(List.of(new MemoryOutboxPort.MemoryOutboxTask(
                        "outbox-1", "VECTOR_UPSERT", "m1", "u1", "default",
                        Map.of("memoryId", "m1"), "", null, Instant.EPOCH)));
        when(memoryManagementPort.memoryHealth("u1", "default"))
                .thenReturn(new MemoryHealthReport(
                        "u1", "default", 1, 1, 1, 1, 0,
                        Map.of("SUCCEEDED", 1L), 1D, 0D, 0, 0, 0.25D, 0.1D,
                        Map.of("shortTermCount", 3), List.of("memory.outbox.backlog"), Instant.EPOCH));
        when(memoryManagementPort.memoryPolicyConfig()).thenReturn(MemoryPolicyConfig.defaults()
                .withCaptureAcceptThreshold(0.55D)
                .withTokenBudget(1800));
        when(memoryManagementPort.updatePolicyConfig(any())).thenReturn(MemoryPolicyConfig.defaults()
                .withCaptureAcceptThreshold(0.6D)
                .withTokenBudget(1600));

        MemoryGovernanceInboundPort governancePort = mock(MemoryGovernanceInboundPort.class);
        when(governancePort.runGovernance("u1", "manual", true)).thenReturn(governanceResult("u1"));
        when(governancePort.runDecay("manual-decay")).thenReturn(governanceResult(""));
        when(governancePort.assessQuality("u1")).thenReturn(MemoryQualityReport.builder().userId("u1").build());
        MemoryMaintenanceInboundPort maintenancePort = mock(MemoryMaintenanceInboundPort.class);
        when(maintenancePort.runMaintenance(any())).thenReturn(maintenanceResult("manual-maintenance"));
        when(maintenancePort.pageMaintenanceRuns(any())).thenReturn(new MemoryMaintenanceRunPage(
                List.of(maintenanceRunRecord("run-1")), 1, 10, 1, 1));
        when(maintenancePort.aggregateRecent(anyInt())).thenReturn(new MemoryMaintenanceRunAggregate(
                MemoryMaintenanceRunAggregate.DEFAULT_LIMIT, 3, 2, 1, 0,
                4L, 2L, 6L,
                5L, 3L, 1L, 2L,
                8L, 5L, 3L,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60)));

        IngestionTaskInboundPort ingestionTaskPort = mock(IngestionTaskInboundPort.class);
        when(ingestionTaskPort.execute(any()))
                .thenReturn(new IngestionTaskExecutionResult("task-1", "pipe-1", "SUCCESS", 0, "ok"));
        when(ingestionTaskPort.page(1, 10, null)).thenReturn(new IngestionTaskPage(List.of(), 0, 10, 1, 0));

        AgentExtensionStatusPort statusPort = mock(AgentExtensionStatusPort.class);
        when(statusPort.listStatuses()).thenReturn(List.of());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRagTraceController(provider(RagTraceInboundPort.class, tracePort)),
                new SeahorseKnowledgeBaseController(provider(KnowledgeBaseInboundPort.class, knowledgeBasePort)),
                new SeahorseMemoryController(provider(MemoryManagementInboundPort.class, memoryManagementPort),
                        provider(MemoryGovernanceInboundPort.class, governancePort)),
                new SeahorseMemoryMaintenanceController(provider(MemoryMaintenanceInboundPort.class, maintenancePort)),
                new SeahorseIngestionTaskController(provider(IngestionTaskInboundPort.class, ingestionTaskPort)),
                new SeahorsePluginController(
                        emptyProvider(FeatureHealthAggregator.class),
                        provider(AgentExtensionStatusPort.class, statusPort),
                        provider(ExtensionRegistry.class, ExtensionRegistry.empty()))).build();

        mvc.perform(get("/rag/traces/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
        mvc.perform(get("/rag/traces/runs/trace-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/rag/traces/runs/trace-1/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mvc.perform(post("/knowledge-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "kb", "embeddingModel", "embed", "collectionName", "col"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("kb-1"));
        mvc.perform(get("/knowledge-base/kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("kb-1"));
        mvc.perform(get("/knowledge-base"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        mvc.perform(get("/memories").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layer").value("short_term"));
        mvc.perform(get("/memories/profile-facts").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slotKey").value("occupation"))
                .andExpect(jsonPath("$.data[0].valueText").value("学生"));
        mvc.perform(get("/memories/corrections").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetKey").value("occupation"))
                .andExpect(jsonPath("$.data[0].correctValue").value("老师"));
        mvc.perform(get("/memories/operations").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].operationId").value("op-1"))
                .andExpect(jsonPath("$.data[0].targetKey").value("occupation"));
        mvc.perform(get("/memories/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("outbox-1"))
                .andExpect(jsonPath("$.data[0].taskType").value("VECTOR_UPSERT"));
        mvc.perform(get("/memories/health").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileFactCount").value(1))
                .andExpect(jsonPath("$.data.pendingReviewCount").value(0))
                .andExpect(jsonPath("$.data.alerts[0]").value("memory.outbox.backlog"));
        mvc.perform(get("/memories/policy-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captureAcceptThreshold").value(0.55))
                .andExpect(jsonPath("$.data.tokenBudget").value(1800));
        mvc.perform(post("/memories/policy-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("captureAcceptThreshold", 0.6D, "tokenBudget", 1600))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captureAcceptThreshold").value(0.6))
                .andExpect(jsonPath("$.data.tokenBudget").value(1600));
        mvc.perform(post("/memories/conflicts/c1/resolve")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "merge"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true));
        mvc.perform(post("/memories/governance/run").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("u1"));
        mvc.perform(post("/memories/maintenance/run")
                        .param("reason", "manual-maintenance")
                        .param("gc", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reason").value("manual-maintenance"))
                .andExpect(jsonPath("$.data.garbageCollectionResult.reason").value("manual-maintenance"))
                .andExpect(jsonPath("$.data.garbageCollectionResult.derivedIndexCandidateCount").value(1))
                .andExpect(jsonPath("$.data.garbageCollectionResult.archiveCandidateCount").value(1))
                .andExpect(jsonPath("$.data.garbageCollectionResult.physicalDeleteCandidateCount").value(1))
                .andExpect(jsonPath("$.data.taskOutcomes[0].task").value("garbageCollection"))
                .andExpect(jsonPath("$.data.taskOutcomes[0].status").value("SUCCEEDED"));
        ArgumentCaptor<MemoryMaintenanceRunCommand> maintenanceCaptor =
                ArgumentCaptor.forClass(MemoryMaintenanceRunCommand.class);
        verify(maintenancePort).runMaintenance(maintenanceCaptor.capture());
        assertThat(maintenanceCaptor.getValue().reason()).isEqualTo("manual-maintenance");
        assertThat(maintenanceCaptor.getValue().garbageCollectionEnabled()).isTrue();
        mvc.perform(get("/memories/maintenance-runs")
                        .param("status", "SUCCEEDED")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].runId").value("run-1"))
                .andExpect(jsonPath("$.data.records[0].gcScannedCount").value(1))
                .andExpect(jsonPath("$.data.records[0].gcEnqueuedCount").value(1))
                .andExpect(jsonPath("$.data.records[0].gcMarkedCount").value(1))
                .andExpect(jsonPath("$.data.records[0].gcDryRun").value(false));
        ArgumentCaptor<MemoryMaintenanceRunQuery> maintenanceQueryCaptor =
                ArgumentCaptor.forClass(MemoryMaintenanceRunQuery.class);
        verify(maintenancePort).pageMaintenanceRuns(maintenanceQueryCaptor.capture());
        assertThat(maintenanceQueryCaptor.getValue().status()).isEqualTo("SUCCEEDED");

        mvc.perform(get("/memories/maintenance-runs/aggregate")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.sampleCount").value(3))
                .andExpect(jsonPath("$.data.succeededCount").value(2))
                .andExpect(jsonPath("$.data.succeededWithWarningsCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.compactionScannedTotal").value(4))
                .andExpect(jsonPath("$.data.aliasScannedTotal").value(5))
                .andExpect(jsonPath("$.data.gcScannedTotal").value(8));
        verify(maintenancePort).aggregateRecent(30);

        mvc.perform(post("/ingestion/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pipelineId", "pipe-1",
                                "source", Map.of("type", "text", "location", "inline", "fileName", "a.txt"),
                                "metadata", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("task-1"));
        mvc.perform(get("/ingestion/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        mvc.perform(get("/agent/plugins/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/agent/plugins/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(post("/agent/plugins/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "wrapper", "portType", "demo", "enabled", true))))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.name").value("wrapper"));
    }

    @Test
    void shouldKeepDashboardConversationAndFeedbackFrontendContracts() throws Exception {
        DashboardInboundPort dashboardPort = mock(DashboardInboundPort.class);
        when(dashboardPort.overview(any())).thenReturn(dashboardOverview());
        when(dashboardPort.performance(any())).thenReturn(dashboardPerformance());
        when(dashboardPort.trends(any(), any(), any()))
                .thenReturn(new DashboardTrends("latency", "24h", "hour", List.of()));

        ConversationManagementInboundPort conversationPort = mock(ConversationManagementInboundPort.class);
        when(conversationPort.listConversations(any()))
                .thenReturn(List.of(new ConversationRecord("c1", "Conversation", Instant.EPOCH)));
        when(conversationPort.listMessages(eq("c1"), any())).thenReturn(List.of(conversationMessage("m1")));

        MessageFeedbackInboundPort feedbackPort = mock(MessageFeedbackInboundPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseDashboardController(provider(DashboardInboundPort.class, dashboardPort)),
                new SeahorseConversationController(provider(ConversationManagementInboundPort.class, conversationPort)),
                new SeahorseMessageFeedbackController(provider(MessageFeedbackInboundPort.class, feedbackPort))).build();

        mvc.perform(get("/admin/dashboard/overview").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window").value("24h"));
        mvc.perform(get("/admin/dashboard/performance").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRate").value(1.0));
        mvc.perform(get("/admin/dashboard/trends").param("metric", "latency").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metric").value("latency"));

        mvc.perform(get("/conversations").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conversationId").value("c1"));
        mvc.perform(put("/conversations/c1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/conversations/c1/messages").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("m1"));
        mvc.perform(post("/conversations/messages/m1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("vote", 1, "reason", "helpful", "comment", "ok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(delete("/conversations/c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldKeepIntentMappingSampleQuestionAndSettingsFrontendContracts() throws Exception {
        IntentTreeInboundPort intentPort = mock(IntentTreeInboundPort.class);
        when(intentPort.tree()).thenReturn(List.of(intentNode("intent-1")));
        when(intentPort.create(any())).thenReturn("intent-1");

        QueryTermMappingInboundPort mappingPort = mock(QueryTermMappingInboundPort.class);
        when(mappingPort.page(anyLong(), anyLong(), any()))
                .thenReturn(new QueryTermMappingPage(List.of(mapping("map-1")), 1, 10, 1, 1));
        when(mappingPort.create(any())).thenReturn("map-1");

        SampleQuestionInboundPort samplePort = mock(SampleQuestionInboundPort.class);
        SampleQuestionRecord sample = new SampleQuestionRecord("sq1", "Title", "Desc", "Question?", Instant.EPOCH,
                Instant.EPOCH);
        when(samplePort.listRandomQuestions()).thenReturn(List.of(sample));
        when(samplePort.page(any())).thenReturn(new SampleQuestionPage(List.of(sample), 1, 10, 1, 1));
        when(samplePort.create(any())).thenReturn("sq1");

        MockEnvironment environment = new MockEnvironment()
                .withProperty("seahorse-agent.adapters.vector.collection-name", "native_collection")
                .withProperty("seahorse-agent.plugins.memory.history-keep-turns", "8");

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIntentTreeController(provider(IntentTreeInboundPort.class, intentPort)),
                new SeahorseQueryTermMappingController(provider(QueryTermMappingInboundPort.class, mappingPort)),
                new SeahorseSampleQuestionController(provider(SampleQuestionInboundPort.class, samplePort)),
                new SeahorseRagSettingsController(environment)).build();

        mvc.perform(get("/intent-tree/trees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("intent-1"));
        mvc.perform(post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("intentCode", "intent", "name", "Intent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("intent-1"));
        mvc.perform(put("/intent-tree/intent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Intent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(post("/intent-tree/batch/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of("intent-1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mvc.perform(get("/mappings").param("current", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("map-1"));
        mvc.perform(post("/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceTerm", "crm", "targetTerm", "customer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("map-1"));
        mvc.perform(put("/mappings/map-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceTerm", "crm", "targetTerm", "customer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(delete("/mappings/map-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mvc.perform(get("/rag/sample-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("sq1"));
        mvc.perform(get("/sample-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("sq1"));
        mvc.perform(post("/sample-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Title", "description", "Desc", "question", "Question?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("sq1"));
        mvc.perform(put("/sample-questions/sq1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", "Question?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mvc.perform(get("/rag/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rag.defaultConfig.collectionName").value("native_collection"));
    }

    @Test
    void shouldKeepKnowledgeDocumentAndChunkFrontendContracts() throws Exception {
        KnowledgeDocumentInboundPort documentPort = mock(KnowledgeDocumentInboundPort.class);
        when(documentPort.upload(any())).thenReturn(knowledgeDocument("doc-1"));
        when(documentPort.queryById("doc-1")).thenReturn(knowledgeDocumentDetail("doc-1"));
        when(documentPort.page(eq("kb-1"), any()))
                .thenReturn(new KnowledgeDocumentPage(List.of(knowledgeDocumentDetail("doc-1")), 1, 10, 1, 1));
        when(documentPort.search(any(), anyInt()))
                .thenReturn(List.of(new KnowledgeDocumentSummary("doc-1", "kb-1", "Doc", "Knowledge")));
        when(documentPort.chunkLogs(eq("doc-1"), anyLong(), anyLong()))
                .thenReturn(new KnowledgeDocumentChunkLogPage(List.of(), 0, 10, 1, 0));

        KnowledgeChunkInboundPort chunkPort = mock(KnowledgeChunkInboundPort.class);
        when(chunkPort.page(eq("doc-1"), any()))
                .thenReturn(new KnowledgeChunkPage(List.of(knowledgeChunk("chunk-1")), 1, 10, 1, 1));
        when(chunkPort.create(eq("doc-1"), any())).thenReturn(knowledgeChunk("chunk-1"));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, documentPort)),
                new SeahorseKnowledgeChunkController(provider(KnowledgeChunkInboundPort.class, chunkPort))).build();

        mvc.perform(multipart("/knowledge-base/kb-1/docs/upload").file("file", "hello".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("doc-1"));
        mvc.perform(get("/knowledge-base/kb-1/docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("doc-1"));
        mvc.perform(get("/knowledge-base/docs/search").param("keyword", "Doc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("doc-1"));
        mvc.perform(get("/knowledge-base/docs/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("doc-1"));
        mvc.perform(put("/knowledge-base/docs/doc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("docName", "Doc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(post("/knowledge-base/docs/doc-1/chunk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(patch("/knowledge-base/docs/doc-1/enable").param("value", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/knowledge-base/docs/doc-1/chunk-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        mvc.perform(get("/knowledge-base/docs/doc-1/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("chunk-1"));
        mvc.perform(post("/knowledge-base/docs/doc-1/chunks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("chunkId", "chunk-1", "content", "hello", "index", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("chunk-1"));
        mvc.perform(put("/knowledge-base/docs/doc-1/chunks/chunk-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(patch("/knowledge-base/docs/doc-1/chunks/chunk-1/enable").param("value", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(patch("/knowledge-base/docs/doc-1/chunks/batch-enable")
                        .param("value", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("chunkIds", List.of("chunk-1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(delete("/knowledge-base/docs/doc-1/chunks/chunk-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(delete("/knowledge-base/docs/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldKeepKeywordIndexMaintenanceContracts() throws Exception {
        KeywordIndexMaintenanceInboundPort maintenancePort = mock(KeywordIndexMaintenanceInboundPort.class);
        when(maintenancePort.rebuildDocument("doc-1"))
                .thenReturn(new KeywordIndexRebuildResult("document", "doc-1", 1, 1, 2, 1, 0, 0, List.of()));
        when(maintenancePort.rebuildKnowledgeBase(eq("kb-1"), anyInt()))
                .thenReturn(new KeywordIndexRebuildResult("knowledge_base", "kb-1", 3, 2, 8, 3, 1, 0, List.of()));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKeywordIndexMaintenanceController(
                        provider(KeywordIndexMaintenanceInboundPort.class, maintenancePort))).build();

        mvc.perform(post("/knowledge-base/docs/doc-1/keyword-index/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.scope").value("document"))
                .andExpect(jsonPath("$.data.indexedChunks").value(2));

        mvc.perform(post("/knowledge-base/kb-1/keyword-index/rebuild").param("batchSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.scope").value("knowledge_base"))
                .andExpect(jsonPath("$.data.processedDocuments").value(3));
    }

    @Test
    void shouldKeepMetadataBackfillManagementContracts() throws Exception {
        MetadataBackfillInboundPort backfillPort = mock(MetadataBackfillInboundPort.class);
        when(backfillPort.createJob(any()))
                .thenReturn(metadataBackfillJob(MetadataBackfillJobStatus.PENDING));
        when(backfillPort.getJob("job-1"))
                .thenReturn(metadataBackfillJob(MetadataBackfillJobStatus.PENDING));
        when(backfillPort.pageJobs(any(MetadataBackfillJobQuery.class)))
                .thenReturn(new MetadataBackfillJobPage(
                        List.of(metadataBackfillJob(MetadataBackfillJobStatus.PENDING)), 1, 10, 1, 1));
        when(backfillPort.overview("tenant-1", "kb-1"))
                .thenReturn(metadataBackfillOverview());
        when(backfillPort.runNextBatch("job-1"))
                .thenReturn(new MetadataBackfillRunResult(
                        "job-1", MetadataBackfillJobStatus.COMPLETED, 1, 50,
                        3, 2, 1, 0, 1, 0, Map.of("currentPage", 1), List.of("doc-2: boom")));
        when(backfillPort.pause(eq("job-1"), any()))
                .thenReturn(metadataBackfillJob(MetadataBackfillJobStatus.PAUSED));
        when(backfillPort.resume(eq("job-1"), any()))
                .thenReturn(metadataBackfillJob(MetadataBackfillJobStatus.PENDING));
        when(backfillPort.cancel(eq("job-1"), any()))
                .thenReturn(metadataBackfillJob(MetadataBackfillJobStatus.CANCELLED));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataBackfillController(provider(MetadataBackfillInboundPort.class, backfillPort))).build();

        mvc.perform(post("/knowledge-base/kb-1/metadata-backfill/jobs")
                        .header("X-User-Id", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantId", "tenant-1", "pipelineId", "pipe-1", "batchSize", 50))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.jobId").value("job-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mvc.perform(get("/knowledge-base/kb-1/metadata-backfill/jobs")
                        .param("tenantId", "tenant-1")
                        .param("status", "PENDING")
                        .param("pipelineId", "pipe-1")
                        .param("operator", "admin")
                        .param("documentId", "doc-2")
                        .param("pauseReason", "SCHEMA_MISSING")
                        .param("failureKeyword", "boom")
                        .param("hasFailures", "true")
                        .param("reExtract", "true")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].jobId").value("job-1"));
        ArgumentCaptor<MetadataBackfillJobQuery> backfillQueryCaptor =
                ArgumentCaptor.forClass(MetadataBackfillJobQuery.class);
        verify(backfillPort).pageJobs(backfillQueryCaptor.capture());
        MetadataBackfillJobQuery capturedBackfillQuery = backfillQueryCaptor.getValue();
        assertThat(capturedBackfillQuery.pipelineId()).isEqualTo("pipe-1");
        assertThat(capturedBackfillQuery.operator()).isEqualTo("admin");
        assertThat(capturedBackfillQuery.documentId()).isEqualTo("doc-2");
        assertThat(capturedBackfillQuery.pauseReason()).isEqualTo("SCHEMA_MISSING");
        assertThat(capturedBackfillQuery.failureKeyword()).isEqualTo("boom");
        assertThat(capturedBackfillQuery.hasFailures()).isTrue();
        assertThat(capturedBackfillQuery.reExtract()).isTrue();

        mvc.perform(get("/knowledge-base/kb-1/metadata-backfill/overview")
                        .param("tenantId", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalJobs").value(2))
                .andExpect(jsonPath("$.data.pendingReviewItems").value(2))
                .andExpect(jsonPath("$.data.pendingSchemaCompensationJobs").value(1))
                .andExpect(jsonPath("$.data.statusCounts[0].key").value("PENDING"))
                .andExpect(jsonPath("$.data.latestReExtractJob.jobId").value("job-1"));
        verify(backfillPort).overview("tenant-1", "kb-1");

        mvc.perform(get("/metadata-backfill/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-1"));

        mvc.perform(post("/metadata-backfill/jobs/job-1/run-next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.failedDocuments").value(1));

        mvc.perform(post("/metadata-backfill/jobs/job-1/pause").header("X-User-Id", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
        mvc.perform(post("/metadata-backfill/jobs/job-1/resume").header("X-User-Id", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        mvc.perform(post("/metadata-backfill/jobs/job-1/cancel").header("X-User-Id", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void shouldKeepMetadataQualityReportContract() throws Exception {
        MetadataQualityInboundPort qualityPort = mock(MetadataQualityInboundPort.class);
        when(qualityPort.report("tenant-1", "kb-1", 3, 2, "extractor-v2", "prompt-v3"))
                .thenReturn(metadataQualityReport());
        when(qualityPort.compare(
                "tenant-1", "kb-1", 3,
                1, "extractor-v1", "prompt-v1",
                2, "extractor-v2", "prompt-v3"))
                .thenReturn(metadataQualityComparisonReport());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataQualityController(provider(MetadataQualityInboundPort.class, qualityPort))).build();

                mvc.perform(get("/knowledge-base/kb-1/metadata-quality/report")
                        .param("tenantId", "tenant-1")
                        .param("schemaVersion", "2")
                        .param("extractorVersion", "extractor-v2")
                        .param("llmPromptVersion", "prompt-v3")
                        .param("topN", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.schemaVersion").value(2))
                .andExpect(jsonPath("$.data.extractorVersion").value("extractor-v2"))
                .andExpect(jsonPath("$.data.llmPromptVersion").value("prompt-v3"))
                .andExpect(jsonPath("$.data.averageFieldCoverage").value(0.75))
                .andExpect(jsonPath("$.data.lowConfidenceRatio").value(0.25))
                .andExpect(jsonPath("$.data.reviewPassRate").value(0.8))
                .andExpect(jsonPath("$.data.reviewCorrectionRate").value(0.25))
                .andExpect(jsonPath("$.data.pendingReviewCount").value(2))
                .andExpect(jsonPath("$.data.indexSyncFailureCount").value(1))
                .andExpect(jsonPath("$.data.fieldCoverages[0].fieldKey").value("department"))
                .andExpect(jsonPath("$.data.fieldCoverages[0].lowConfidenceDocuments").value(1))
                .andExpect(jsonPath("$.data.fieldCoverages[0].lowConfidenceRate").value(1D / 3D))
                .andExpect(jsonPath("$.data.fieldCoverages[0].reviewedDocuments").value(2))
                .andExpect(jsonPath("$.data.fieldCoverages[0].correctedDocuments").value(1))
                .andExpect(jsonPath("$.data.fieldCoverages[0].correctionRate").value(0.5))
                .andExpect(jsonPath("$.data.reviewFeedbackSummaries[0].fieldKey").value("department"))
                .andExpect(jsonPath("$.data.reviewFeedbackSummaries[0].decisionAction").value("CORRECTED"))
                .andExpect(jsonPath("$.data.reviewFeedbackSummaries[0].sampleAuditIds[0]").value("audit-1"))
                .andExpect(jsonPath("$.data.quarantineReasons[0].reasonCode").value("SCHEMA_MISSING"));

        mvc.perform(get("/knowledge-base/kb-1/metadata-quality/compare")
                        .param("tenantId", "tenant-1")
                        .param("baselineSchemaVersion", "1")
                        .param("baselineExtractorVersion", "extractor-v1")
                        .param("baselineLlmPromptVersion", "prompt-v1")
                        .param("candidateSchemaVersion", "2")
                        .param("candidateExtractorVersion", "extractor-v2")
                        .param("candidateLlmPromptVersion", "prompt-v3")
                        .param("topN", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.delta.extractedDocumentsDelta").value(1))
                .andExpect(jsonPath("$.data.delta.reviewCorrectionRateDelta").value(0.1))
                .andExpect(jsonPath("$.data.fieldDeltas[0].fieldKey").value("department"))
                .andExpect(jsonPath("$.data.fieldDeltas[0].correctionRateDelta").value(0.2));
    }

    @Test
    void shouldKeepMetadataSchemaUsageReportContract() throws Exception {
        MetadataSchemaUsageInboundPort usagePort = mock(MetadataSchemaUsageInboundPort.class);
        when(usagePort.report("tenant-1", "kb-1", 2)).thenReturn(metadataSchemaUsageReport());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataSchemaUsageController(usagePort)).build();

        mvc.perform(get("/knowledge-base/kb-1/metadata-schema/usage-report")
                        .param("tenantId", "tenant-1")
                        .param("schemaVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.schemaVersion").value(2))
                .andExpect(jsonPath("$.data.totalCompiledRequests").value(4))
                .andExpect(jsonPath("$.data.totalRejectedRequests").value(1))
                .andExpect(jsonPath("$.data.guardOnlyRequestCount").value(1))
                .andExpect(jsonPath("$.data.fields[0].fieldKey").value("department"))
                .andExpect(jsonPath("$.data.fields[0].usageCount").value(3));
    }

    @Test
    void shouldKeepRetrievalEvaluationContract() throws Exception {
        RetrievalEvaluationInboundPort evaluationPort = mock(RetrievalEvaluationInboundPort.class);
        when(evaluationPort.evaluate(any())).thenReturn(new RetrievalEvaluationReport(
                "baseline", 2, 1, 1,
                1.0D, 0.5D, 0.63D, 0.0D,
                12.0D, 12.0D,
                List.of(new RetrievalEvaluationCaseResult(
                        "case-1", "question-a", List.of("chunk-1"), List.of("doc-1"),
                        1, 1, 1.0D, 0.5D, 0.63D, 12L, "SUCCESS", ""))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalEvaluationController(
                        provider(RetrievalEvaluationInboundPort.class, evaluationPort))).build();

        mvc.perform(post("/knowledge-base/kb-1/retrieval-quality/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-1",
                                "strategyName", "baseline",
                                "topK", 2,
                                "cases", List.of(Map.of(
                                        "caseId", "case-1",
                                        "question", "question-a",
                                        "expectedChunkIds", List.of("chunk-1"),
                                        "aclSubjectIds", List.of("dept-a")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.strategyName").value("baseline"))
                .andExpect(jsonPath("$.data.recallAtK").value(1.0D))
                .andExpect(jsonPath("$.data.mrr").value(0.5D))
                .andExpect(jsonPath("$.data.ndcgAtK").value(0.63D))
                .andExpect(jsonPath("$.data.cases[0].retrievedChunkIds[0]").value("chunk-1"));

        ArgumentCaptor<com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand> captor =
                ArgumentCaptor.forClass(
                        com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand.class);
        verify(evaluationPort).evaluate(captor.capture());
        assertThat(captor.getValue().cases().get(0).filter().system().tenantId()).isEqualTo("tenant-1");
        assertThat(captor.getValue().cases().get(0).filter().system().knowledgeBaseIds()).containsExactly("kb-1");
        assertThat(captor.getValue().cases().get(0).filter().system().aclSubjectIds()).containsExactly("dept-a");
    }

    @Test
    void shouldKeepRetrievalEvaluationComparisonContract() throws Exception {
        RetrievalEvaluationInboundPort evaluationPort = mock(RetrievalEvaluationInboundPort.class);
        when(evaluationPort.compare(any())).thenReturn(new RetrievalEvaluationComparisonReport(
                "baseline",
                "keyword",
                List.of(
                        new RetrievalEvaluationReport("baseline", 2, 1, 1,
                                0.0D, 0.0D, 0.0D, 1.0D,
                                20.0D, 20.0D, List.of()),
                        new RetrievalEvaluationReport("keyword", 2, 1, 1,
                                1.0D, 1.0D, 1.0D, 0.0D,
                                15.0D, 15.0D, List.of())),
                List.of(
                        new RetrievalEvaluationStrategyDelta("baseline", 0D, 0D, 0D, 0D, 0D, 0D),
                        new RetrievalEvaluationStrategyDelta("keyword", 1D, 1D, 1D, -1D, -5D, -5D))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalEvaluationController(
                        provider(RetrievalEvaluationInboundPort.class, evaluationPort))).build();

        mvc.perform(post("/knowledge-base/kb-1/retrieval-quality/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-1",
                                "baselineStrategyName", "baseline",
                                "topK", 2,
                                "strategies", List.of(
                                        Map.of(
                                                "strategyName", "baseline",
                                                "topK", 2,
                                                "options", Map.of("finalTopK", 2)),
                                        Map.of(
                                                "strategyName", "keyword",
                                                "topK", 2,
                                                "options", Map.of(
                                                        "finalTopK", 2,
                                                        "enableKeyword", true))),
                                "cases", List.of(Map.of(
                                        "caseId", "case-1",
                                        "question", "question-a",
                                        "expectedDocIds", List.of("doc-1"),
                                        "aclSubjectIds", List.of("dept-a")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.baselineStrategyName").value("baseline"))
                .andExpect(jsonPath("$.data.winnerStrategyName").value("keyword"))
                .andExpect(jsonPath("$.data.reports[1].strategyName").value("keyword"))
                .andExpect(jsonPath("$.data.deltas[1].recallAtKDelta").value(1.0D))
                .andExpect(jsonPath("$.data.deltas[1].p95LatencyMsDelta").value(-5.0D));

        ArgumentCaptor<RetrievalEvaluationComparisonCommand> captor =
                ArgumentCaptor.forClass(RetrievalEvaluationComparisonCommand.class);
        verify(evaluationPort).compare(captor.capture());
        assertThat(captor.getValue().baselineStrategyName()).isEqualTo("baseline");
        assertThat(captor.getValue().strategies().stream()
                .map(strategy -> strategy.strategyName())
                .toList())
                .containsExactly("baseline", "keyword");
        assertThat(captor.getValue().cases().get(0).filter().system().tenantId()).isEqualTo("tenant-1");
        assertThat(captor.getValue().cases().get(0).filter().system().knowledgeBaseIds()).containsExactly("kb-1");
        assertThat(captor.getValue().cases().get(0).filter().system().aclSubjectIds()).containsExactly("dept-a");
    }

    @Test
    void shouldKeepVersionQualityComparisonContract() throws Exception {
        VersionQualityComparisonInboundPort comparisonPort = mock(VersionQualityComparisonInboundPort.class);
        when(comparisonPort.compare(any())).thenReturn(versionQualityComparisonReport());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseVersionQualityComparisonController(comparisonPort)).build();

        mvc.perform(post("/knowledge-base/kb-1/version-quality/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-1",
                                "quarantineTopN", 3,
                                "baselineSchemaVersion", 1,
                                "baselineExtractorVersion", "extractor-v1",
                                "baselineLlmPromptVersion", "prompt-v1",
                                "candidateSchemaVersion", 2,
                                "candidateExtractorVersion", "extractor-v2",
                                "candidateLlmPromptVersion", "prompt-v3",
                                "retrievalComparison", Map.of(
                                        "baselineStrategyName", "baseline",
                                        "topK", 2,
                                        "strategies", List.of(
                                                Map.of("strategyName", "baseline", "topK", 2),
                                                Map.of("strategyName", "candidate", "topK", 2,
                                                        "options", Map.of("enableKeyword", true, "finalTopK", 2))),
                                        "cases", List.of(Map.of(
                                                "caseId", "case-1",
                                                "question", "question-a",
                                                "expectedDocIds", List.of("doc-1"),
                                                "aclSubjectIds", List.of("dept-a"))))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.metadataQuality.delta.averageFieldCoverageDelta").value(0.1D))
                .andExpect(jsonPath("$.data.retrievalQuality.winnerStrategyName").value("candidate"))
                .andExpect(jsonPath("$.data.retrievalQuality.deltas[1].recallAtKDelta").value(1.0D));

        ArgumentCaptor<VersionQualityComparisonCommand> captor =
                ArgumentCaptor.forClass(VersionQualityComparisonCommand.class);
        verify(comparisonPort).compare(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(captor.getValue().knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(captor.getValue().quarantineTopN()).isEqualTo(3);
        assertThat(captor.getValue().baselineSchemaVersion()).isEqualTo(1);
        assertThat(captor.getValue().candidateSchemaVersion()).isEqualTo(2);
        assertThat(captor.getValue().retrievalComparison().baselineStrategyName()).isEqualTo("baseline");
        assertThat(captor.getValue().retrievalComparison().strategies()).hasSize(2);
        assertThat(captor.getValue().retrievalComparison().cases().get(0).filter().system().tenantId())
                .isEqualTo("tenant-1");
        assertThat(captor.getValue().retrievalComparison().cases().get(0).filter().system().knowledgeBaseIds())
                .containsExactly("kb-1");
    }

    @Test
    void shouldKeepRetrievalStrategyTemplateContract() throws Exception {
        RetrievalStrategyTemplateInboundPort templatePort = mock(RetrievalStrategyTemplateInboundPort.class);
        when(templatePort.listTemplates("kb-1")).thenReturn(List.of(new RetrievalStrategyTemplate(
                "hybrid_rrf",
                "混合召回 RRF",
                "同时启用向量和关键词召回",
                com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions.builder()
                        .finalTopK(5)
                        .enableVector(true)
                        .enableKeyword(true)
                        .enableRrf(true)
                        .build())));
        when(templatePort.upsertTemplate(eq("kb-1"), any())).thenReturn(new RetrievalStrategyTemplate(
                "keyword_precise",
                "关键词精确优先",
                "优先使用关键词通道",
                com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions.builder()
                        .finalTopK(3)
                        .enableVector(false)
                        .enableKeyword(true)
                        .enableRrf(false)
                        .build()));
        when(templatePort.deleteTemplate("kb-1", "keyword_precise")).thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalStrategyTemplateController(
                        provider(RetrievalStrategyTemplateInboundPort.class, templatePort))).build();

        mvc.perform(get("/knowledge-base/kb-1/retrieval-strategy-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].templateKey").value("hybrid_rrf"))
                .andExpect(jsonPath("$.data[0].options.enableKeyword").value(true))
                .andExpect(jsonPath("$.data[0].options.enableRrf").value(true));
        mvc.perform(post("/knowledge-base/kb-1/retrieval-strategy-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "templateKey", "keyword_precise",
                                "displayName", "关键词精确优先",
                                "description", "优先使用关键词通道",
                                "sortOrder", 10,
                                "enabled", true,
                                "options", Map.of(
                                        "finalTopK", 3,
                                        "enableVector", false,
                                        "enableKeyword", true,
                                        "enableRrf", false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateKey").value("keyword_precise"))
                .andExpect(jsonPath("$.data.options.enableKeyword").value(true));
        mvc.perform(put("/knowledge-base/kb-1/retrieval-strategy-templates/keyword_precise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "关键词精确优先",
                                "description", "优先使用关键词通道",
                                "options", Map.of("finalTopK", 3, "enableKeyword", true)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateKey").value("keyword_precise"));
        mvc.perform(delete("/knowledge-base/kb-1/retrieval-strategy-templates/keyword_precise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(templatePort).listTemplates("kb-1");
        verify(templatePort, times(2)).upsertTemplate(eq("kb-1"), any());
        verify(templatePort).deleteTemplate("kb-1", "keyword_precise");
    }

    @Test
    void shouldKeepMetadataReviewAndQuarantineManagementContracts() throws Exception {
        MetadataReviewInboundPort reviewPort = mock(MetadataReviewInboundPort.class);
        when(reviewPort.page(
                "tenant-1", "kb-1", MetadataReviewStatus.PENDING, "LOW_CONFIDENCE", "doc-1", 1, 10))
                .thenReturn(new MetadataReviewPage(List.of(metadataReview("review-1", MetadataReviewStatus.PENDING)),
                        1, 10, 1, 1));
        when(reviewPort.queryById("review-1"))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.PENDING));
        when(reviewPort.listAudits("review-1"))
                .thenReturn(List.of(metadataReviewAudit("audit-1")));
        when(reviewPort.approve(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.APPROVED));
        when(reviewPort.correct(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.CORRECTED));
        when(reviewPort.ignoreField(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.CORRECTED));
        when(reviewPort.reExtract(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.RE_EXTRACTING));
        when(reviewPort.reject(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.REJECTED));
        when(reviewPort.quarantine(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.QUARANTINED));

        MetadataQuarantineInboundPort quarantinePort = mock(MetadataQuarantineInboundPort.class);
        when(quarantinePort.page(
                "tenant-1", "kb-1", Boolean.FALSE, "VALIDATE", "SCHEMA_MISSING", "doc-1", "job-1", 1, 10))
                .thenReturn(new MetadataQuarantinePage(List.of(metadataQuarantine("q-1", false, 1)), 1, 10, 1, 1));
        when(quarantinePort.queryById("q-1"))
                .thenReturn(metadataQuarantine("q-1", false, 1));
        when(quarantinePort.resolve(eq("q-1"), any()))
                .thenReturn(metadataQuarantine("q-1", true, 1));
        when(quarantinePort.retry(eq("q-1"), any()))
                .thenReturn(metadataQuarantine("q-1", false, 2));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataReviewController(provider(MetadataReviewInboundPort.class, reviewPort)),
                new SeahorseMetadataQuarantineController(
                        provider(MetadataQuarantineInboundPort.class, quarantinePort))).build();

        mvc.perform(get("/metadata-review/items")
                        .param("tenantId", "tenant-1")
                        .param("kbId", "kb-1")
                        .param("status", "PENDING")
                        .param("reasonCode", "LOW_CONFIDENCE")
                        .param("documentId", "doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].id").value("review-1"))
                .andExpect(jsonPath("$.data.records[0].reviewStatus").value("PENDING"));
        mvc.perform(get("/metadata-review/items/review-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value("doc-1"));
        mvc.perform(get("/metadata-review/items/review-1/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("audit-1"))
                .andExpect(jsonPath("$.data[0].toStatus").value("CORRECTED"))
                .andExpect(jsonPath("$.data[0].decisionMetadata.department").value("legal"));
        mvc.perform(post("/metadata-review/items/review-1/approve")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "通过"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));
        mvc.perform(post("/metadata-review/items/review-1/correct")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "comment", "修正部门",
                                "correctedMetadata", Map.of("department", "legal")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("CORRECTED"));
        mvc.perform(post("/metadata-review/items/review-1/ignore-field")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "comment", "忽略非关键字段",
                                "ignoredFields", List.of("owner")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("CORRECTED"));
        mvc.perform(post("/metadata-review/items/review-1/re-extract")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "comment", "重新抽取",
                                "extractorVersion", "extractor-v2",
                                "pipelineId", "pipe-1",
                                "llmExtractorVersion", "llm-v2",
                                "llmPromptVersion", "prompt-v2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("RE_EXTRACTING"));
        mvc.perform(post("/metadata-review/items/review-1/reject")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "拒绝"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"));
        mvc.perform(post("/metadata-review/items/review-1/quarantine")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "转隔离"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("QUARANTINED"));

        mvc.perform(get("/metadata-quarantine/items")
                        .param("tenantId", "tenant-1")
                        .param("kbId", "kb-1")
                        .param("resolved", "false")
                        .param("stage", "VALIDATE")
                        .param("reasonCode", "SCHEMA_MISSING")
                        .param("documentId", "doc-1")
                        .param("jobId", "job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("q-1"))
                .andExpect(jsonPath("$.data.records[0].resolved").value(false));
        mvc.perform(get("/metadata-quarantine/items/q-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reasonCode").value("SCHEMA_MISSING"));
        mvc.perform(post("/metadata-quarantine/items/q-1/resolve").header("X-User-Id", "auditor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true));
        mvc.perform(post("/metadata-quarantine/items/q-1/retry")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nextRetryTime", "2026-05-13T10:00:00Z"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retryCount").value(2));
    }

    @Test
    void shouldKeepMemoryReviewManagementContracts() throws Exception {
        MemoryReviewInboundPort reviewPort = mock(MemoryReviewInboundPort.class);
        when(reviewPort.page("default", "user-1", MemoryReviewStatus.PENDING,
                "PROJECT_FACT", "project.ambiguous", 1, 10))
                .thenReturn(new MemoryReviewPage(
                        List.of(memoryReview("review-1", MemoryReviewStatus.PENDING)), 1, 10, 1, 1));
        when(reviewPort.queryById("review-1"))
                .thenReturn(memoryReview("review-1", MemoryReviewStatus.PENDING));
        when(reviewPort.approve(eq("review-1"), any()))
                .thenReturn(memoryReview("review-1", MemoryReviewStatus.APPLIED));
        when(reviewPort.modify(eq("review-1"), any()))
                .thenReturn(memoryReview("review-1", MemoryReviewStatus.APPLIED));
        when(reviewPort.reject(eq("review-1"), any()))
                .thenReturn(memoryReview("review-1", MemoryReviewStatus.REJECTED));
        when(reviewPort.listFeedbackSamples("review-1", 5))
                .thenReturn(List.of(memoryReviewFeedback("sample-1", "review-1")));
        when(reviewPort.listFeedbackSamples("default", "user-1", MemoryReviewStatus.APPLIED,
                "PROJECT_FACT", "project.ambiguous", 25))
                .thenReturn(List.of(memoryReviewFeedback("sample-2", "review-2")));
        when(reviewPort.exportRefinerFeedbackSamples("default", "user-1", MemoryReviewStatus.APPLIED,
                "PROJECT_FACT", "project.ambiguous", 25))
                .thenReturn(List.of(memoryRefinerFeedbackExport("sample-2", "review-2")));
        when(reviewPort.pendingSummary("default", "user-1", "PROJECT_FACT", "project.ambiguous"))
                .thenReturn(new MemoryReviewPendingSummary(
                        1,
                        memoryReview("review-1", MemoryReviewStatus.PENDING)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryReviewController(provider(MemoryReviewInboundPort.class, reviewPort))).build();

        mvc.perform(get("/memory-review/items")
                        .param("tenantId", "default")
                        .param("userId", "user-1")
                        .param("status", "PENDING")
                        .param("targetKind", "PROJECT_FACT")
                        .param("targetKey", "project.ambiguous")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].candidateId").value("review-1"))
                .andExpect(jsonPath("$.data.records[0].reviewStatus").value("PENDING"));
        mvc.perform(get("/memory-review/pending-summary")
                        .param("tenantId", "default")
                        .param("userId", "user-1")
                        .param("targetKind", "PROJECT_FACT")
                        .param("targetKey", "project.ambiguous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.hasPending").value(true))
                .andExpect(jsonPath("$.data.latestPendingCandidate.candidateId").value("review-1"));
        mvc.perform(get("/memory-review/items/review-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetKind").value("PROJECT_FACT"));
        mvc.perform(post("/memory-review/items/review-1/approve")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPLIED"));
        mvc.perform(post("/memory-review/items/review-1/modify")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "comment", "modify",
                                "correctedContent", "corrected memory",
                                "correctedMetadata", Map.of("source", "human")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPLIED"));
        mvc.perform(post("/memory-review/items/review-1/reject")
                        .header("X-User-Id", "auditor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "reject"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"));
        mvc.perform(get("/memory-review/items/review-1/feedback-samples")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sampleId").value("sample-1"))
                .andExpect(jsonPath("$.data[0].candidateId").value("review-1"))
                .andExpect(jsonPath("$.data[0].chosenMetadata.reviewReason").value("human"));
        mvc.perform(get("/memory-review/feedback-samples")
                        .param("tenantId", "default")
                        .param("userId", "user-1")
                        .param("status", "APPLIED")
                        .param("targetKind", "PROJECT_FACT")
                        .param("targetKey", "project.ambiguous")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sampleId").value("sample-2"))
                .andExpect(jsonPath("$.data[0].candidateId").value("review-2"));
        mvc.perform(get("/memory-review/feedback-samples/export")
                        .param("tenantId", "default")
                        .param("userId", "user-1")
                        .param("status", "APPLIED")
                        .param("targetKind", "PROJECT_FACT")
                        .param("targetKey", "project.ambiguous")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sampleId").value("sample-2"))
                .andExpect(jsonPath("$.data[0].feedbackType").value("MODIFY"))
                .andExpect(jsonPath("$.data[0].promptInput.targetKey").value("project.ambiguous"))
                .andExpect(jsonPath("$.data[0].chosenOutput.action").value("ADD"));
    }

    @Test
    void shouldKeepMemoryTraceQueryContract() throws Exception {
        MemoryTraceInboundPort tracePort = mock(MemoryTraceInboundPort.class);
        when(tracePort.listRecent(any())).thenReturn(List.of(new MemoryTraceEvent(
                "trace-1",
                "tenant-1",
                "user-1",
                "conv-1",
                "session-1",
                "memory-review",
                "approve",
                MemoryTraceEvent.STATUS_SUCCESS,
                "review-1",
                "memory-review",
                Map.of("source", "test"),
                Instant.EPOCH)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryTraceController(provider(MemoryTraceInboundPort.class, tracePort))).build();

        mvc.perform(get("/memories/traces")
                        .param("limit", "25")
                        .param("tenantId", "tenant-1")
                        .param("userId", "user-1")
                        .param("component", "memory-review")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].traceId").value("trace-1"))
                .andExpect(jsonPath("$.data[0].component").value("memory-review"));

        ArgumentCaptor<MemoryTraceQuery> queryCaptor = ArgumentCaptor.forClass(MemoryTraceQuery.class);
        verify(tracePort).listRecent(queryCaptor.capture());
        assertThat(queryCaptor.getValue().limit()).isEqualTo(25);
        assertThat(queryCaptor.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(queryCaptor.getValue().userId()).isEqualTo("user-1");
        assertThat(queryCaptor.getValue().component()).isEqualTo("memory-review");
        assertThat(queryCaptor.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldKeepMemoryRecallEvaluationContract() throws Exception {
        MemoryRecallEvaluationInboundPort evaluationPort = mock(MemoryRecallEvaluationInboundPort.class);
        when(evaluationPort.evaluate(any())).thenReturn(new MemoryRecallEvaluationReport(
                1,
                1,
                1,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                0.0D,
                List.of(new MemoryRecallEvaluationResult(
                        "case-1",
                        "Pulsar PIP-459",
                        List.of("mem-pip"),
                        List.of("mem-pip"),
                        List.of(),
                        true,
                        true,
                        1.0D,
                        1.0D,
                        1.0D,
                        0.0D))));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryRecallEvaluationController(
                        provider(MemoryRecallEvaluationInboundPort.class, evaluationPort))).build();

        mvc.perform(post("/memories/recall-quality/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "topK", 3,
                                "cases", List.of(Map.of(
                                        "caseId", "case-1",
                                        "userId", "user-1",
                                        "conversationId", "conv-1",
                                        "query", "Pulsar PIP-459",
                                        "expectedMemoryIds", List.of("mem-pip")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.hitRate").value(1.0D))
                .andExpect(jsonPath("$.data.meanReciprocalRank").value(1.0D))
                .andExpect(jsonPath("$.data.averagePrecision").value(1.0D))
                .andExpect(jsonPath("$.data.averageNoiseRate").value(0.0D))
                .andExpect(jsonPath("$.data.results[0].retrievedMemoryIds[0]").value("mem-pip"))
                .andExpect(jsonPath("$.data.results[0].precision").value(1.0D))
                .andExpect(jsonPath("$.data.results[0].noiseRate").value(0.0D));
    }

    @Test
    void shouldKeepMemoryRecallGoldenHarnessContract() throws Exception {
        MemoryRecallGoldenHarnessInboundPort harnessPort = mock(MemoryRecallGoldenHarnessInboundPort.class);
        when(harnessPort.listProfiles()).thenReturn(List.of("smoke", "regression"));
        when(harnessPort.runProfile("smoke")).thenReturn(new MemoryRecallEvaluationReport(
                1,
                1,
                1,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                0.0D,
                List.of(new MemoryRecallEvaluationResult(
                        "smoke-1",
                        "Pulsar PIP-459",
                        List.of("mem-pip"),
                        List.of("mem-pip"),
                        List.of(),
                        true,
                        true,
                        1.0D,
                        1.0D,
                        1.0D,
                        0.0D))));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryRecallGoldenHarnessController(
                        provider(MemoryRecallGoldenHarnessInboundPort.class, harnessPort))).build();

        mvc.perform(get("/memories/recall-quality/golden/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0]").value("smoke"))
                .andExpect(jsonPath("$.data[1]").value("regression"));

        mvc.perform(post("/memories/recall-quality/golden/profiles/smoke/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.caseCount").value(1))
                .andExpect(jsonPath("$.data.hitRate").value(1.0D))
                .andExpect(jsonPath("$.data.results[0].caseId").value("smoke-1"));
    }

    @Test
    void shouldKeepMetadataSchemaManagementContracts() throws Exception {
        MetadataSchemaInboundPort schemaPort = mock(MetadataSchemaInboundPort.class);
        when(schemaPort.listFields("tenant-1", "kb-1"))
                .thenReturn(List.of(metadataSchemaField("field-1")));
        when(schemaPort.listFieldCapabilities("tenant-1", "kb-1"))
                .thenReturn(List.of(metadataSchemaFieldCapability("field-1")));
        when(schemaPort.createField(eq("kb-1"), any()))
                .thenReturn(metadataSchemaField("field-1"));
        when(schemaPort.updateField(eq("field-1"), any()))
                .thenReturn(metadataSchemaField("field-1"));
        when(schemaPort.deleteField("field-1")).thenReturn(true);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataSchemaController(provider(MetadataSchemaInboundPort.class, schemaPort))).build();

        mvc.perform(get("/knowledge-base/kb-1/metadata-schema/fields")
                        .param("tenantId", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].fieldKey").value("department"));
        mvc.perform(get("/knowledge-base/kb-1/metadata-schema/field-capabilities")
                        .param("tenantId", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].fieldKey").value("department"))
                .andExpect(jsonPath("$.data[0].pushdownToKeyword").value(true))
                .andExpect(jsonPath("$.data[0].lastSyncOutcome").value("FAILED"));
        mvc.perform(post("/knowledge-base/kb-1/metadata-schema/fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-1",
                                "fieldKey", "department",
                                "displayName", "部门",
                                "valueType", "STRING",
                                "allowedOperators", List.of("EQ", "IN"),
                                "filterable", true,
                                "indexed", true,
                                "indexPolicy", "SEARCH_KEYWORD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("field-1"));
        mvc.perform(put("/metadata-schema/fields/field-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-1",
                                "fieldKey", "department",
                                "displayName", "部门",
                                "valueType", "STRING",
                                "allowedOperators", List.of("EQ", "IN"),
                                "filterable", true,
                                "indexed", true,
                                "indexPolicy", "SEARCH_KEYWORD",
                                "minConfidence", 0.7D))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fieldKey").value("department"));
        mvc.perform(delete("/metadata-schema/fields/field-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void shouldKeepMetadataDictionaryManagementContracts() throws Exception {
        MetadataDictionaryInboundPort dictionaryPort = mock(MetadataDictionaryInboundPort.class);
        when(dictionaryPort.listItems("tenant-1", "department", false))
                .thenReturn(List.of(metadataDictionaryItem("dict-1", true)));
        when(dictionaryPort.createItem(any()))
                .thenReturn(metadataDictionaryItem("dict-1", true));
        when(dictionaryPort.updateItem(eq("dict-1"), any()))
                .thenReturn(metadataDictionaryItem("dict-1", true));
        when(dictionaryPort.deleteItem("dict-1")).thenReturn(true);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataDictionaryController(
                        provider(MetadataDictionaryInboundPort.class, dictionaryPort))).build();

        mvc.perform(get("/metadata-dictionaries/items")
                        .param("tenantId", "tenant-1")
                        .param("dictCode", "department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].rawValue").value("hr"));
        mvc.perform(post("/metadata-dictionaries/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-1",
                                "dictionaryCode", "department",
                                "rawValue", "hr",
                                "canonicalValue", "HR",
                                "displayName", "Human Resource"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("dict-1"));
        mvc.perform(put("/metadata-dictionaries/items/dict-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tenantId", "tenant-1",
                                "dictionaryCode", "department",
                                "rawValue", "hr",
                                "canonicalValue", "HR",
                                "displayName", "Human Resource",
                                "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canonicalValue").value("HR"));
        mvc.perform(delete("/metadata-dictionaries/items/dict-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void shouldKeepMetadataExtractionResultManagementContracts() throws Exception {
        MetadataExtractionResultInboundPort resultPort = mock(MetadataExtractionResultInboundPort.class);
        when(resultPort.page("tenant-1", "kb-1", "doc-1", "job-1", "ACCEPTED",
                Integer.valueOf(2), "extractor-v2", 1L, 10L))
                .thenReturn(new MetadataExtractionResultPage(
                        List.of(metadataExtractionResult("result-1")), 1, 10, 1, 1));
        when(resultPort.queryById("result-1")).thenReturn(metadataExtractionResult("result-1"));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataExtractionResultController(
                        provider(MetadataExtractionResultInboundPort.class, resultPort))).build();

        mvc.perform(get("/metadata-extraction/results")
                        .param("tenantId", "tenant-1")
                        .param("kbId", "kb-1")
                        .param("docId", "doc-1")
                        .param("jobId", "job-1")
                        .param("status", "ACCEPTED")
                        .param("schemaVersion", "2")
                        .param("extractorVersion", "extractor-v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].id").value("result-1"))
                .andExpect(jsonPath("$.data.records[0].normalizedMetadata.department").value("HR"));
        mvc.perform(get("/metadata-extraction/results/result-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("result-1"))
                .andExpect(jsonPath("$.data.rawCandidates[0].fieldKey").value("department"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static AgentDefinition agentDefinition(AgentStatus status) {
        return new AgentDefinition("agent-1", "tenant-a", "Agent One", "desc", "owner-1", "platform",
                AgentType.WORKFLOW, null, status, AgentRiskLevel.HIGH, "agent-1-v1", Instant.EPOCH, Instant.EPOCH);
    }

    private static AgentVersion agentVersion() {
        return new AgentVersion("agent-1-v1", "agent-1", 1L, "Do the work", "{}", "{}", "{}",
                "{}", "admin-1", Instant.EPOCH, "initial");
    }

    private static AgentRun agentRun(AgentRunStatus status) {
        return new AgentRun("run-1", "agent-1", "agent-1-v1", "tenant-a", "user-1",
                "conversation-1", AgentRunTriggerType.CHAT, "summary", status, "trace-1",
                0L, 0L, BigDecimal.ZERO, null, null, Instant.EPOCH,
                status.isFinished() ? Instant.EPOCH : null);
    }

    private static AgentStep agentStep() {
        return new AgentStep("step-1", "run-1", 1, AgentStepType.MODEL_TURN, AgentStepStatus.SUCCEEDED,
                "{\"prompt\":\"hi\"}", "{\"answer\":\"hello\"}", null, null, Instant.EPOCH, Instant.EPOCH);
    }

    private static StreamCallback noopCallback() {
        return new StreamCallback() {
            @Override
            public void onContent(String content) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable error) {
            }
        };
    }

    private static KnowledgeBaseRecord knowledgeBase(String id) {
        KnowledgeBaseRecord record = new KnowledgeBaseRecord();
        record.setId(id);
        record.setName("kb");
        record.setEmbeddingModel("embed");
        record.setCollectionName("col");
        return record;
    }

    private static DashboardOverview dashboardOverview() {
        DashboardKpi zero = new DashboardKpi(0L, 0L, 0.0);
        DashboardKpiGroup kpis = new DashboardKpiGroup(zero, zero, zero, zero, zero, zero);
        return new DashboardOverview("24h", "previous", 0L, kpis);
    }

    private static DashboardPerformance dashboardPerformance() {
        DashboardPerformance performance = new DashboardPerformance();
        performance.setWindow("24h");
        performance.setAvgLatencyMs(10L);
        performance.setP95LatencyMs(20L);
        performance.setSuccessRate(1.0);
        performance.setErrorRate(0.0);
        performance.setNoDocRate(0.0);
        performance.setSlowRate(0.0);
        return performance;
    }

    private static ConversationMessageRecord conversationMessage(String id) {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(id);
        record.setConversationId("c1");
        record.setRole("assistant");
        record.setContent("hello");
        record.setCreateTime(Instant.EPOCH);
        return record;
    }

    private static IntentNodeTree intentNode(String id) {
        IntentNodeTree node = new IntentNodeTree();
        node.setId(id);
        node.setIntentCode("intent");
        node.setName("Intent");
        return node;
    }

    private static QueryTermMappingRecord mapping(String id) {
        QueryTermMappingRecord record = new QueryTermMappingRecord();
        record.setId(id);
        record.setSourceTerm("crm");
        record.setTargetTerm("customer");
        record.setEnabled(true);
        return record;
    }

    private static KnowledgeDocumentRecord knowledgeDocument(String id) {
        return new KnowledgeDocumentRecord(id, "kb-1", "Doc",
                new KnowledgeDocumentFileRef("s3://bucket/doc.txt", "txt", 5L),
                new KnowledgeDocumentProcessRef("PENDING", "pipeline", "pipe-1"));
    }

    private static KnowledgeDocumentDetail knowledgeDocumentDetail(String id) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(id);
        detail.setKbId("kb-1");
        detail.setDocName("Doc");
        detail.setEnabled(true);
        detail.setStatus("PENDING");
        return detail;
    }

    private static KnowledgeChunkRecord knowledgeChunk(String id) {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(id);
        record.setDocId("doc-1");
        record.setContent("hello");
        record.setEnabled(1);
        return record;
    }

    private static MetadataBackfillJobRecord metadataBackfillJob(MetadataBackfillJobStatus status) {
        return new MetadataBackfillJobRecord(
                "job-1",
                "tenant-1",
                "kb-1",
                "pipe-1",
                status,
                1,
                50,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of("currentPage", 1),
                List.of(),
                "admin",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MetadataBackfillOperationsOverview metadataBackfillOverview() {
        return new MetadataBackfillOperationsOverview(
                "tenant-1",
                "kb-1",
                2,
                8,
                6,
                1,
                1,
                2,
                1,
                2,
                1,
                1,
                1,
                1,
                2,
                List.of(
                        new MetadataBackfillCountItem("PENDING", 1),
                        new MetadataBackfillCountItem("PAUSED", 1)),
                List.of(new MetadataBackfillCountItem("SCHEMA_MISSING", 1)),
                List.of(new MetadataBackfillCountItem("SCHEMA_MISSING", 1)),
                metadataBackfillJob(MetadataBackfillJobStatus.PENDING),
                metadataBackfillJob(MetadataBackfillJobStatus.PAUSED),
                Instant.EPOCH);
    }

    private static MetadataQualityReport metadataQualityReport() {
        return new MetadataQualityReport(
                "tenant-1",
                "kb-1",
                2,
                "extractor-v2",
                "prompt-v3",
                4,
                3,
                0.75D,
                0.25D,
                0.8D,
                0.25D,
                2,
                1,
                1,
                List.of(new MetadataFieldCoverage("department", "部门", true,
                        3, 4, 0.75D, 1, 1D / 3D, 2, 1, 0.5D)),
                List.of(new MetadataReviewFeedbackSummary(
                        "department", "LOW_CONFIDENCE", "CORRECTED", 2, 2,
                        List.of("review-1"), List.of("result-1"), List.of("audit-1"), List.of("job-1"))),
                List.of(new MetadataQuarantineReasonCount("SCHEMA_MISSING", "缺少 Schema", 1)),
                Instant.EPOCH);
    }

    private static MetadataQualityComparisonReport metadataQualityComparisonReport() {
        MetadataQualityReport baseline = new MetadataQualityReport(
                "tenant-1", "kb-1", 1, "extractor-v1", "prompt-v1",
                4, 2, 0.65D, 0.3D, 0.7D, 0.1D, 2, 1, 1,
                List.of(new MetadataFieldCoverage("department", "部门", true,
                        2, 4, 0.5D, 1, 0.5D, 1, 0, 0D)),
                List.of(),
                List.of(new MetadataQuarantineReasonCount("SCHEMA_MISSING", "缺少 Schema", 1)),
                Instant.EPOCH);
        MetadataQualityReport candidate = metadataQualityReport();
        return new MetadataQualityComparisonReport(
                "tenant-1",
                "kb-1",
                baseline,
                candidate,
                new MetadataQualityComparisonDelta(0, 1, 0.1D, -0.05D, 0.1D, 0.1D, 0, 0, 0),
                List.of(new MetadataFieldCoverageDelta("department", "部门",
                        1, 0, 1, 1, 0.25D, -0.16666666666666669D, 0.2D)));
    }

    private static MetadataSchemaUsageReport metadataSchemaUsageReport() {
        return new MetadataSchemaUsageReport(
                "tenant-1",
                "kb-1",
                2,
                4L,
                1L,
                1L,
                0.25D,
                0.2D,
                List.of(
                        new MetadataSchemaUsageFieldRecord("department", "部门", 3L, 0L, 0L, 0D, 0D),
                        new MetadataSchemaUsageFieldRecord("owner", "负责人", 1L, 1L, 1L, 1D, 0.5D)),
                Instant.parse("2026-05-16T10:00:00Z"));
    }

    private static VersionQualityComparisonReport versionQualityComparisonReport() {
        return new VersionQualityComparisonReport(
                "tenant-1",
                "kb-1",
                metadataQualityComparisonReport(),
                new RetrievalEvaluationComparisonReport(
                        "baseline",
                        "candidate",
                        List.of(
                                new RetrievalEvaluationReport("baseline", 2, 1, 1,
                                        0.0D, 0.0D, 0.0D, 1.0D,
                                        20.0D, 20.0D, List.of()),
                                new RetrievalEvaluationReport("candidate", 2, 1, 1,
                                        1.0D, 1.0D, 1.0D, 0.0D,
                                        15.0D, 15.0D, List.of())),
                        List.of(
                                new RetrievalEvaluationStrategyDelta("baseline", 0D, 0D, 0D, 0D, 0D, 0D),
                                new RetrievalEvaluationStrategyDelta("candidate", 1D, 1D, 1D, -1D, -5D, -5D))),
                Instant.EPOCH);
    }

    private static MetadataReviewRecord metadataReview(String id, MetadataReviewStatus status) {
        return new MetadataReviewRecord(
                id,
                "tenant-1",
                "kb-1",
                "doc-1",
                "result-1",
                status,
                10,
                "LOW_CONFIDENCE",
                "字段置信度低",
                Map.of("department", "hr"),
                MetadataReviewStatus.CORRECTED.equals(status) ? Map.of("department", "legal") : Map.of(),
                "auditor",
                "ok",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MetadataReviewAuditRecord metadataReviewAudit(String id) {
        return new MetadataReviewAuditRecord(
                id,
                "review-1",
                "tenant-1",
                "kb-1",
                "doc-1",
                "result-1",
                "PENDING",
                "CORRECTED",
                "auditor",
                "ok",
                Map.of("department", "hr"),
                Map.of("department", "legal"),
                Map.of("department", "legal"),
                Instant.EPOCH);
    }

    private static MetadataQuarantineRecord metadataQuarantine(String id, boolean resolved, int retryCount) {
        return new MetadataQuarantineRecord(
                id,
                "tenant-1",
                "kb-1",
                "doc-1",
                "job-1",
                "VALIDATE",
                "SCHEMA_MISSING",
                "缺少 Schema",
                Map.of("source", "test"),
                retryCount,
                Instant.EPOCH,
                resolved,
                resolved ? "auditor" : "",
                resolved ? Instant.EPOCH : null,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MemoryReviewRecord memoryReview(String id, MemoryReviewStatus status) {
        return new MemoryReviewRecord(
                id,
                "op-1",
                "default",
                "user-1",
                "conv-1",
                "msg-1",
                "REVIEW",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.ambiguous",
                "candidate content",
                0.7D,
                0.8D,
                0.8D,
                0.2D,
                "needs_review",
                List.of("msg-1"),
                Map.of("reviewReason", "low_confidence"),
                status,
                "auditor",
                "ok",
                MemoryReviewStatus.APPLIED.equals(status) ? "chosen content" : "",
                Map.of(),
                MemoryReviewStatus.APPLIED.equals(status) ? "memory-review-apply-review-1" : "",
                MemoryReviewStatus.APPLIED.equals(status) ? "SHORT_TERM" : "",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MemoryReviewFeedbackSample memoryReviewFeedback(String id, String candidateId) {
        return new MemoryReviewFeedbackSample(
                id,
                candidateId,
                "op-1",
                "default",
                "user-1",
                "REVIEW",
                MemoryReviewStatus.APPLIED,
                "auditor",
                "approved",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.ambiguous",
                "candidate content",
                "chosen content",
                Map.of("reviewReason", "low_confidence"),
                Map.of("reviewReason", "human"),
                List.of("msg-1"),
                "memory-review-apply-review-1",
                "SHORT_TERM",
                Instant.EPOCH);
    }

    private static MemoryRefinerFeedbackExportRecord memoryRefinerFeedbackExport(String id, String candidateId) {
        return new MemoryRefinerFeedbackExportRecord(
                id,
                candidateId,
                "MODIFY",
                Map.of("tenantId", "default", "userId", "user-1", "targetKey", "project.ambiguous"),
                Map.of("action", "REVIEW", "content", "candidate content"),
                Map.of("action", "ADD", "content", "chosen content"),
                Map.of("reviewerId", "auditor"),
                Instant.EPOCH);
    }

    private static MetadataSchemaFieldRecord metadataSchemaField(String id) {
        return new MetadataSchemaFieldRecord(
                id,
                "tenant-1",
                "kb-1",
                "department",
                "部门",
                MetadataValueType.STRING,
                java.util.Set.of(MetadataOperator.EQ, MetadataOperator.IN),
                false,
                true,
                false,
                true,
                true,
                MetadataIndexPolicy.SEARCH_KEYWORD,
                0.8D,
                java.util.Set.of("source"),
                Map.of("sourceKeys", List.of("department")),
                BackendFieldMapping.defaults("department"),
                1,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MetadataSchemaFieldCapabilityRecord metadataSchemaFieldCapability(String id) {
        return new MetadataSchemaFieldCapabilityRecord(
                id,
                "tenant-1",
                "kb-1",
                "department",
                "閮ㄩ棬",
                MetadataValueType.STRING,
                true,
                false,
                true,
                true,
                MetadataIndexPolicy.SEARCH_KEYWORD,
                true,
                false,
                false,
                2,
                "elasticsearch",
                "UPDATE",
                "FAILED",
                "IllegalStateException",
                "mapping failed",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MetadataDictionaryItemRecord metadataDictionaryItem(String id, boolean enabled) {
        return new MetadataDictionaryItemRecord(
                id,
                "tenant-1",
                "department",
                "hr",
                "HR",
                "Human Resource",
                enabled,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MetadataExtractionResultRecord metadataExtractionResult(String id) {
        return new MetadataExtractionResultRecord(
                id,
                "tenant-1",
                "kb-1",
                "doc-1",
                "job-1",
                2,
                "extractor-v2",
                "ACCEPTED",
                Map.of("department", "HR"),
                List.of(Map.of("fieldKey", "department", "value", "hr")),
                List.of(Map.of("fieldKey", "department", "confidence", 0.93D)),
                List.of(),
                Map.of("department", "HR"),
                "auditor",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MemoryGovernanceRunResult governanceResult(String userId) {
        return new MemoryGovernanceRunResult(userId, "manual", 0, 0, 0, false, false, List.of(), Instant.EPOCH);
    }

    private static MemoryMaintenanceRunResult maintenanceResult(String reason) {
        return new MemoryMaintenanceRunResult(
                reason,
                false,
                false,
                true,
                null,
                null,
                new MemoryGarbageCollectionResult(reason, 1, 1, 1, 1, 1, 1, 1, 1, false, List.of(), Instant.EPOCH),
                List.of(),
                List.of(),
                List.of(MemoryMaintenanceTaskOutcome.succeeded(
                        MemoryMaintenanceTaskOutcome.TASK_GARBAGE_COLLECTION)),
                Instant.EPOCH);
    }

    private static MemoryMaintenanceRunRecord maintenanceRunRecord(String runId) {
        return new MemoryMaintenanceRunRecord(
                runId,
                "manual-maintenance",
                MemoryMaintenanceRunRecord.STATUS_SUCCEEDED,
                false,
                false,
                true,
                1,
                1,
                1,
                false,
                List.of(),
                List.of(),
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return provider(type, null);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }
}
