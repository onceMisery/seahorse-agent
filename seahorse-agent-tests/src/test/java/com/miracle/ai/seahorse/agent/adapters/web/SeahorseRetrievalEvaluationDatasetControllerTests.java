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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseRetrievalEvaluationDatasetControllerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepRetrievalEvaluationDatasetManagementContracts() throws Exception {
        RetrievalEvaluationDatasetInboundPort datasetPort = mock(RetrievalEvaluationDatasetInboundPort.class);
        when(datasetPort.listDatasets("kb-1", false)).thenReturn(List.of(summary()));
        when(datasetPort.getDataset("kb-1", "dataset-1")).thenReturn(dataset());
        when(datasetPort.upsertDataset(eq("kb-1"), any())).thenReturn(dataset());
        when(datasetPort.deleteDataset("kb-1", "dataset-1")).thenReturn(true);
        when(datasetPort.evaluateDataset(eq("kb-1"), any())).thenReturn(report());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseRetrievalEvaluationDatasetController(datasetPort)).build();

        mvc.perform(get("/knowledge-base/kb-1/retrieval-evaluation-datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].datasetId").value("dataset-1"))
                .andExpect(jsonPath("$.data[0].caseCount").value(1));

        mvc.perform(get("/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].caseId").value("case-1"));

        mvc.perform(post("/knowledge-base/kb-1/retrieval-evaluation-datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "回归集",
                                "enabled", true,
                                "cases", List.of(Map.of(
                                        "caseId", "case-1",
                                        "question", "问题",
                                        "expectedDocIds", List.of("doc-1")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasetId").value("dataset-1"));

        mvc.perform(put("/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "回归集更新", "cases", List.of()))))
                .andExpect(status().isOk());

        mvc.perform(post("/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("strategyName", "hybrid", "topK", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategyName").value("hybrid"));

        mvc.perform(delete("/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        ArgumentCaptor<RetrievalEvaluationDatasetPayload> payloadCaptor =
                ArgumentCaptor.forClass(RetrievalEvaluationDatasetPayload.class);
        verify(datasetPort, times(2)).upsertDataset(eq("kb-1"), payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues().get(0).name()).isEqualTo("回归集");
        ArgumentCaptor<RetrievalEvaluationDatasetRunCommand> runCaptor =
                ArgumentCaptor.forClass(RetrievalEvaluationDatasetRunCommand.class);
        verify(datasetPort).evaluateDataset(eq("kb-1"), runCaptor.capture());
        assertThat(runCaptor.getValue().datasetId()).isEqualTo("dataset-1");
        assertThat(runCaptor.getValue().topK()).isEqualTo(3);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private RetrievalEvaluationDatasetSummary summary() {
        return dataset().summary();
    }

    private RetrievalEvaluationDataset dataset() {
        return new RetrievalEvaluationDataset(
                "dataset-1",
                "kb-1",
                "回归集",
                "上线前回归",
                true,
                List.of(caseRecord()),
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private RetrievalEvaluationCase caseRecord() {
        return new RetrievalEvaluationCase("case-1", "问题", List.of("kb-1"), List.of("doc-1"),
                List.of("chunk-1"), null, null);
    }

    private RetrievalEvaluationReport report() {
        return new RetrievalEvaluationReport("hybrid", 3, 1, 1, 1D, 1D, 1D, 0D, 10D, 10D, List.of());
    }
}
