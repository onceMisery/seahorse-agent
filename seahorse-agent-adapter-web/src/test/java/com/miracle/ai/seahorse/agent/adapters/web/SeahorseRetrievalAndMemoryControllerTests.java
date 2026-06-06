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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
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

    // --- VersionQualityComparison ---

    @Test
    void shouldCompareVersionQuality() throws Exception {
        VersionQualityComparisonInboundPort port = mock(VersionQualityComparisonInboundPort.class);
        when(port.compare(any())).thenReturn(
                mock(com.miracle.ai.seahorse.agent.ports.outbound.metadata.VersionQualityComparisonReport.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseVersionQualityComparisonController(port)).build();

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
