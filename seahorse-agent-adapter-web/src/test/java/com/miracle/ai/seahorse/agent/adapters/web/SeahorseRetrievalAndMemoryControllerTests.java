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

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCaseDiagnostics;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCaseResult;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationChunkDiagnostic;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategyDelta;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyPromotionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseRetrievalAndMemoryControllerTests {

    // --- RetrievalEvaluation ---

    @Test
    void shouldEvaluateRetrieval() throws Exception {
        RetrievalEvaluationInboundPort port = mock(RetrievalEvaluationInboundPort.class);
        when(port.evaluate(any())).thenReturn(
                mock(com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalEvaluationController(provider(RetrievalEvaluationInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/kb-1/retrieval-quality/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "test question",
                                  "expectedDocId": "doc-1",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).evaluate(any());
    }

    @Test
    void shouldExposeRetrievalComparisonCaseDiagnostics() throws Exception {
        RetrievalEvaluationInboundPort port = mock(RetrievalEvaluationInboundPort.class);
        when(port.compare(any())).thenReturn(new RetrievalEvaluationComparisonReport(
                "baseline",
                "candidate",
                List.of(new RetrievalEvaluationReport(
                        "baseline", 2, 1, 1, 0.5D, 0.5D, 0.5D, 0D, 8D, 8D,
                        List.of(new RetrievalEvaluationCaseResult(
                                "case-1", "question", List.of("chunk-1"), List.of("doc-1"), 1, 1,
                                0.5D, 1D, 1D, 8L, "MISS", "", 0.5D, 1, List.of("negative-1"),
                                new RetrievalEvaluationCaseDiagnostics(
                                        List.of("chunk-1", "missing-1"),
                                        List.of("doc-1"),
                                        List.of("kb-1"),
                                        List.of("missing-1"),
                                        List.of(),
                                        List.of(),
                                        List.of(new RetrievalEvaluationChunkDiagnostic(
                                                1, "chunk-1", "doc-1", "kb-1", 0.9D,
                                                List.of("chunk:chunk-1"), false))))))),
                List.of(new RetrievalEvaluationStrategyDelta("baseline", 0D, 0D, 0D, 0D, 0D, 0D, 0D))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalEvaluationController(provider(RetrievalEvaluationInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/kb-1/retrieval-quality/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baselineStrategyName": "baseline",
                                  "topK": 2,
                                  "strategies": [],
                                  "cases": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reports[0].cases[0].diagnostics.missingExpectedChunkIds[0]")
                        .value("missing-1"))
                .andExpect(jsonPath("$.data.reports[0].cases[0].diagnostics.retrievedChunks[0].chunkId")
                        .value("chunk-1"));

        verify(port).compare(any());
    }

    // --- RetrievalEvaluationDataset ---

    @Test
    void shouldListRetrievalEvaluationDatasets() throws Exception {
        RetrievalEvaluationDatasetInboundPort port = mock(RetrievalEvaluationDatasetInboundPort.class);
        when(port.listDatasets("kb-1", false)).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalEvaluationDatasetController(
                        provider(RetrievalEvaluationDatasetInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/kb-1/retrieval-evaluation-datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listDatasets("kb-1", false);
    }

    @Test
    void shouldImportRetrievalEvaluationSamplesFromFrontendExportShape() throws Exception {
        RetrievalEvaluationDatasetInboundPort port = mock(RetrievalEvaluationDatasetInboundPort.class);
        when(port.getDataset("kb-1", "dataset-1")).thenReturn(
                new RetrievalEvaluationDataset("dataset-1", "kb-1", "daily", "daily gate",
                        true, List.of(), null, null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalEvaluationDatasetController(
                        provider(RetrievalEvaluationDatasetInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1/samples/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "sampleId": "sample-1",
                                    "query": "How do I deploy Seahorse?",
                                    "expectedDocumentIds": ["doc-1"],
                                    "expectedChunkIds": ["chunk-1"],
                                    "remark": "smoke"
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(1));

        var captor = forClass(RetrievalEvaluationDatasetPayload.class);
        verify(port).upsertDataset(eq("kb-1"), captor.capture());
        assertThat(captor.getValue().datasetId()).isEqualTo("dataset-1");
        assertThat(captor.getValue().name()).isEqualTo("daily");
        assertThat(captor.getValue().description()).isEqualTo("daily gate");
        assertThat(captor.getValue().enabled()).isTrue();
        assertThat(captor.getValue().cases()).hasSize(1);
        assertThat(captor.getValue().cases().get(0).caseId()).isEqualTo("sample-1");
        assertThat(captor.getValue().cases().get(0).question()).isEqualTo("How do I deploy Seahorse?");
        assertThat(captor.getValue().cases().get(0).expectedDocIds()).containsExactly("doc-1");
        assertThat(captor.getValue().cases().get(0).expectedChunkIds()).containsExactly("chunk-1");
        assertThat(captor.getValue().cases().get(0).tags()).containsExactly("smoke");
    }

    // --- RetrievalStrategyTemplate ---

    @Test
    void shouldListRetrievalStrategyTemplates() throws Exception {
        RetrievalStrategyTemplateInboundPort port = mock(RetrievalStrategyTemplateInboundPort.class);
        when(port.listTemplates("kb-1")).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalStrategyTemplateController(provider(RetrievalStrategyTemplateInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/kb-1/retrieval-strategy-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listTemplates("kb-1");
    }

    @Test
    void shouldPromoteRetrievalStrategyFromComparison() throws Exception {
        RetrievalStrategyTemplateInboundPort port = mock(RetrievalStrategyTemplateInboundPort.class);
        when(port.promoteTemplateFromComparison(eq("kb-1"), any())).thenReturn(
                new RetrievalStrategyTemplate(
                        "candidate", "Candidate", "Passed comparison", RetrievalOptions.defaults(8), true));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalStrategyTemplateController(provider(RetrievalStrategyTemplateInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1/comparisons/comparison-1/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "operatorId": "admin-a",
                                  "templateKey": "candidate",
                                  "displayName": "Candidate",
                                  "description": "Passed comparison",
                                  "comment": "daily gate",
                                  "options": {
                                    "finalTopK": 8,
                                    "enableKeyword": true,
                                    "enableRrf": true
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        var captor = forClass(RetrievalStrategyPromotionCommand.class);
        verify(port).promoteTemplateFromComparison(eq("kb-1"), captor.capture());
        assertThat(captor.getValue().datasetId()).isEqualTo("dataset-1");
        assertThat(captor.getValue().comparisonId()).isEqualTo("comparison-1");
        assertThat(captor.getValue().template().templateKey()).isEqualTo("candidate");
        assertThat(captor.getValue().template().options().finalTopK()).isEqualTo(8);
    }

    // --- VersionQualityComparison ---

    @Test
    void shouldCompareVersionQuality() throws Exception {
        VersionQualityComparisonInboundPort port = mock(VersionQualityComparisonInboundPort.class);
        when(port.compare(any())).thenReturn(
                mock(com.miracle.ai.seahorse.agent.ports.outbound.metadata.VersionQualityComparisonReport.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseVersionQualityComparisonController(
                        provider(VersionQualityComparisonInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/kb-1/version-quality/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baselineVersion": "v1",
                                  "candidateVersion": "v2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // --- MemoryMaintenance ---

    @Test
    void shouldRunMemoryMaintenance() throws Exception {
        MemoryMaintenanceInboundPort port = mock(MemoryMaintenanceInboundPort.class);
        when(port.runMaintenance(any())).thenReturn(null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryMaintenanceController(provider(MemoryMaintenanceInboundPort.class, port))).build();

        mvc.perform(post("/memories/maintenance/run")
                        .param("reason", "scheduled"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldPageMaintenanceRuns() throws Exception {
        MemoryMaintenanceInboundPort port = mock(MemoryMaintenanceInboundPort.class);
        when(port.pageMaintenanceRuns(any())).thenReturn(mock(MemoryMaintenanceRunPage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryMaintenanceController(provider(MemoryMaintenanceInboundPort.class, port))).build();

        mvc.perform(get("/memories/maintenance-runs")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    // --- MemoryRecallEvaluation ---

    @Test
    void shouldEvaluateMemoryRecall() throws Exception {
        MemoryRecallEvaluationInboundPort port = mock(MemoryRecallEvaluationInboundPort.class);
        when(port.evaluate(any())).thenReturn(null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryRecallEvaluationController(provider(MemoryRecallEvaluationInboundPort.class, port))).build();

        mvc.perform(post("/memories/recall-quality/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-1"
                                }
                                """))
                .andExpect(status().isOk());
    }

    // --- MemoryRecallGoldenHarness ---

    @Test
    void shouldListGoldenProfiles() throws Exception {
        MemoryRecallGoldenHarnessInboundPort port = mock(MemoryRecallGoldenHarnessInboundPort.class);
        when(port.listProfiles()).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryRecallGoldenHarnessController(provider(MemoryRecallGoldenHarnessInboundPort.class, port))).build();

        mvc.perform(get("/memories/recall-quality/golden/profiles"))
                .andExpect(status().isOk());
    }

    // --- MemoryReview ---

    @Test
    void shouldPageMemoryReviewItems() throws Exception {
        MemoryReviewInboundPort port = mock(MemoryReviewInboundPort.class);
        when(port.page(eq("tenant-a"), eq(null), any(), eq(null), eq(null), eq(1L), eq(10L)))
                .thenReturn(mock(MemoryReviewPage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryReviewController(provider(MemoryReviewInboundPort.class, port))).build();

        mvc.perform(get("/memory-review/items")
                        .param("tenantId", "tenant-a")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    // --- MemoryTrace ---

    @Test
    void shouldQueryMemoryTraces() throws Exception {
        MemoryTraceInboundPort port = mock(MemoryTraceInboundPort.class);
        when(port.listRecent(any(MemoryTraceQuery.class))).thenReturn(List.of(mock(MemoryTraceEvent.class)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryTraceController(provider(MemoryTraceInboundPort.class, port))).build();

        mvc.perform(get("/memories/traces")
                        .param("limit", "50"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDisableProfileFact() throws Exception {
        MemoryManagementInboundPort port = mock(MemoryManagementInboundPort.class);
        when(port.disableProfileFact("user-1", "default", "preferences.response_style", "admin-1"))
                .thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryController(
                        provider(MemoryManagementInboundPort.class, port),
                        provider(MemoryGovernanceInboundPort.class, mock(MemoryGovernanceInboundPort.class))))
                .build();

        mvc.perform(post("/memories/profile-facts/preferences.response_style/disable")
                        .param("userId", "user-1")
                        .param("tenantId", "default")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.disabled").value(true));

        verify(port).disableProfileFact("user-1", "default", "preferences.response_style", "admin-1");
    }

    @Test
    void shouldResolveMemoryConflictFromChatInteraction() throws Exception {
        String userId = "2001523723396308993";
        MemoryManagementInboundPort port = mock(MemoryManagementInboundPort.class);
        when(port.resolveConflict("mem-conflict-1", "keep_a", "interactive:" + userId))
                .thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMemoryController(
                        provider(MemoryManagementInboundPort.class, port),
                        provider(MemoryGovernanceInboundPort.class, mock(MemoryGovernanceInboundPort.class))))
                .build();

        mvc.perform(post("/memories/conflicts/interactive-resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", userId)
                        .content("""
                                {
                                  "conflictId": "mem-conflict-1",
                                  "action": "keep_a",
                                  "source": "chat-ui"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.resolved").value(true));

        verify(port).resolveConflict("mem-conflict-1", "keep_a", "interactive:" + userId);
    }

    // --- RagTrace ---

    @Test
    void shouldPageRagTraceRuns() throws Exception {
        RagTraceInboundPort port = mock(RagTraceInboundPort.class);
        when(port.pageRuns(any())).thenReturn(new RagTracePage<>(1L, 10L, 0L, List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRagTraceController(provider(RagTraceInboundPort.class, port))).build();

        mvc.perform(get("/rag/traces/runs")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldGetRagTraceDetail() throws Exception {
        RagTraceInboundPort port = mock(RagTraceInboundPort.class);
        when(port.detail("trace-1")).thenReturn(mock(RagTraceDetail.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRagTraceController(provider(RagTraceInboundPort.class, port))).build();

        mvc.perform(get("/rag/traces/runs/trace-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).detail("trace-1");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
