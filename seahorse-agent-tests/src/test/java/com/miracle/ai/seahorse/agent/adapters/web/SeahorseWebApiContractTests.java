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
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryPage;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillRunResult;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineReasonCount;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
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
                new SeahorseAuthController(authPort),
                new SeahorseUserController(userPort)).build();

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
    void shouldKeepChatApiContracts() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        ChatStreamCallbackFactoryPort callbackFactory = (emitter, conversationId, taskId) -> noopCallback();
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseChatController(chatPort, callbackFactory, streamTaskPort, 1_000L)).build();

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "hello")
                        .param("conversationId", "c1")
                        .param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

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

        MemoryGovernanceInboundPort governancePort = mock(MemoryGovernanceInboundPort.class);
        when(governancePort.runGovernance("u1", "manual", true)).thenReturn(governanceResult("u1"));
        when(governancePort.runDecay("manual-decay")).thenReturn(governanceResult(""));
        when(governancePort.assessQuality("u1")).thenReturn(MemoryQualityReport.builder().userId("u1").build());

        IngestionTaskInboundPort ingestionTaskPort = mock(IngestionTaskInboundPort.class);
        when(ingestionTaskPort.execute(any()))
                .thenReturn(new IngestionTaskExecutionResult("task-1", "pipe-1", "SUCCESS", 0, "ok"));
        when(ingestionTaskPort.page(1, 10, null)).thenReturn(new IngestionTaskPage(List.of(), 0, 10, 1, 0));

        AgentExtensionStatusPort statusPort = mock(AgentExtensionStatusPort.class);
        when(statusPort.listStatuses()).thenReturn(List.of());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRagTraceController(tracePort),
                new SeahorseKnowledgeBaseController(knowledgeBasePort),
                new SeahorseMemoryController(memoryManagementPort, governancePort),
                new SeahorseIngestionTaskController(ingestionTaskPort),
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
        mvc.perform(post("/memories/conflicts/c1/resolve")
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "merge"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true));
        mvc.perform(post("/memories/governance/run").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("u1"));

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
                new SeahorseDashboardController(dashboardPort),
                new SeahorseConversationController(conversationPort),
                new SeahorseMessageFeedbackController(feedbackPort)).build();

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
                new SeahorseIntentTreeController(intentPort),
                new SeahorseQueryTermMappingController(mappingPort),
                new SeahorseSampleQuestionController(samplePort),
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
                new SeahorseKnowledgeDocumentController(documentPort),
                new SeahorseKnowledgeChunkController(chunkPort)).build();

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
                new SeahorseKeywordIndexMaintenanceController(maintenancePort)).build();

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
                new SeahorseMetadataBackfillController(backfillPort)).build();

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
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].jobId").value("job-1"));

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
        when(qualityPort.report("tenant-1", "kb-1", 3)).thenReturn(metadataQualityReport());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataQualityController(qualityPort)).build();

        mvc.perform(get("/knowledge-base/kb-1/metadata-quality/report")
                        .param("tenantId", "tenant-1")
                        .param("topN", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.averageFieldCoverage").value(0.75))
                .andExpect(jsonPath("$.data.lowConfidenceRatio").value(0.25))
                .andExpect(jsonPath("$.data.reviewPassRate").value(0.8))
                .andExpect(jsonPath("$.data.pendingReviewCount").value(2))
                .andExpect(jsonPath("$.data.indexSyncFailureCount").value(1))
                .andExpect(jsonPath("$.data.fieldCoverages[0].fieldKey").value("department"))
                .andExpect(jsonPath("$.data.quarantineReasons[0].reasonCode").value("SCHEMA_MISSING"));
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
                new SeahorseRetrievalEvaluationController(evaluationPort)).build();

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
                new SeahorseRetrievalEvaluationController(evaluationPort)).build();

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
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalStrategyTemplateController(templatePort)).build();

        mvc.perform(get("/knowledge-base/kb-1/retrieval-strategy-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].templateKey").value("hybrid_rrf"))
                .andExpect(jsonPath("$.data[0].options.enableKeyword").value(true))
                .andExpect(jsonPath("$.data[0].options.enableRrf").value(true));

        verify(templatePort).listTemplates("kb-1");
    }

    @Test
    void shouldKeepMetadataReviewAndQuarantineManagementContracts() throws Exception {
        MetadataReviewInboundPort reviewPort = mock(MetadataReviewInboundPort.class);
        when(reviewPort.page("tenant-1", "kb-1", MetadataReviewStatus.PENDING, 1, 10))
                .thenReturn(new MetadataReviewPage(List.of(metadataReview("review-1", MetadataReviewStatus.PENDING)),
                        1, 10, 1, 1));
        when(reviewPort.queryById("review-1"))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.PENDING));
        when(reviewPort.approve(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.APPROVED));
        when(reviewPort.correct(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.CORRECTED));
        when(reviewPort.ignoreField(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.CORRECTED));
        when(reviewPort.reject(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.REJECTED));
        when(reviewPort.quarantine(eq("review-1"), any()))
                .thenReturn(metadataReview("review-1", MetadataReviewStatus.QUARANTINED));

        MetadataQuarantineInboundPort quarantinePort = mock(MetadataQuarantineInboundPort.class);
        when(quarantinePort.page("tenant-1", "kb-1", Boolean.FALSE, 1, 10))
                .thenReturn(new MetadataQuarantinePage(List.of(metadataQuarantine("q-1", false, 1)), 1, 10, 1, 1));
        when(quarantinePort.queryById("q-1"))
                .thenReturn(metadataQuarantine("q-1", false, 1));
        when(quarantinePort.resolve(eq("q-1"), any()))
                .thenReturn(metadataQuarantine("q-1", true, 1));
        when(quarantinePort.retry(eq("q-1"), any()))
                .thenReturn(metadataQuarantine("q-1", false, 2));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataReviewController(reviewPort),
                new SeahorseMetadataQuarantineController(quarantinePort)).build();

        mvc.perform(get("/metadata-review/items")
                        .param("tenantId", "tenant-1")
                        .param("kbId", "kb-1")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].id").value("review-1"))
                .andExpect(jsonPath("$.data.records[0].reviewStatus").value("PENDING"));
        mvc.perform(get("/metadata-review/items/review-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value("doc-1"));
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
                        .param("resolved", "false"))
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
    void shouldKeepMetadataSchemaManagementContracts() throws Exception {
        MetadataSchemaInboundPort schemaPort = mock(MetadataSchemaInboundPort.class);
        when(schemaPort.listFields("tenant-1", "kb-1"))
                .thenReturn(List.of(metadataSchemaField("field-1")));
        when(schemaPort.createField(eq("kb-1"), any()))
                .thenReturn(metadataSchemaField("field-1"));
        when(schemaPort.updateField(eq("field-1"), any()))
                .thenReturn(metadataSchemaField("field-1"));
        when(schemaPort.deleteField("field-1")).thenReturn(true);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataSchemaController(schemaPort)).build();

        mvc.perform(get("/knowledge-base/kb-1/metadata-schema/fields")
                        .param("tenantId", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].fieldKey").value("department"));
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
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

    private static MetadataQualityReport metadataQualityReport() {
        return new MetadataQualityReport(
                "tenant-1",
                "kb-1",
                4,
                3,
                0.75D,
                0.25D,
                0.8D,
                2,
                1,
                1,
                List.of(new MetadataFieldCoverage("department", "部门", true, 3, 4, 0.75D)),
                List.of(new MetadataQuarantineReasonCount("SCHEMA_MISSING", "缺少 Schema", 1)),
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

    private static MemoryGovernanceRunResult governanceResult(String userId) {
        return new MemoryGovernanceRunResult(userId, "manual", 0, 0, false, false, List.of(), Instant.EPOCH);
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
