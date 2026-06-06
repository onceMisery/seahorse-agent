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

import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshResult;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodeTree;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseIngestionAndIntentControllerTests {

    // --- IngestionPipeline ---

    @Test
    void shouldCreateIngestionPipeline() throws Exception {
        IngestionPipelineInboundPort port = mock(IngestionPipelineInboundPort.class);
        when(port.create(any())).thenReturn(mock(IngestionPipelineRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIngestionPipelineController(provider(IngestionPipelineInboundPort.class, port))).build();

        mvc.perform(post("/ingestion/pipelines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "pipeline-1",
                                  "steps": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).create(any());
    }

    @Test
    void shouldPageIngestionPipelines() throws Exception {
        IngestionPipelineInboundPort port = mock(IngestionPipelineInboundPort.class);
        when(port.page(1L, 10L, "test")).thenReturn(mock(IngestionPipelinePage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIngestionPipelineController(provider(IngestionPipelineInboundPort.class, port))).build();

        mvc.perform(get("/ingestion/pipelines")
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).page(1L, 10L, "test");
    }

    @Test
    void shouldDeleteIngestionPipeline() throws Exception {
        IngestionPipelineInboundPort port = mock(IngestionPipelineInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIngestionPipelineController(provider(IngestionPipelineInboundPort.class, port))).build();

        mvc.perform(delete("/ingestion/pipelines/pipeline-1")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete("pipeline-1", "admin-1");
    }

    // --- IngestionTask ---

    @Test
    void shouldGetIngestionTask() throws Exception {
        IngestionTaskInboundPort port = mock(IngestionTaskInboundPort.class);
        when(port.get("task-1")).thenReturn(mock(IngestionTaskRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIngestionTaskController(provider(IngestionTaskInboundPort.class, port))).build();

        mvc.perform(get("/ingestion/tasks/task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).get("task-1");
    }

    @Test
    void shouldListIngestionTaskNodes() throws Exception {
        IngestionTaskInboundPort port = mock(IngestionTaskInboundPort.class);
        when(port.listNodes("task-1")).thenReturn(List.of(mock(IngestionTaskNodeRecord.class)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIngestionTaskController(provider(IngestionTaskInboundPort.class, port))).build();

        mvc.perform(get("/ingestion/tasks/task-1/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listNodes("task-1");
    }

    @Test
    void shouldPageIngestionTasks() throws Exception {
        IngestionTaskInboundPort port = mock(IngestionTaskInboundPort.class);
        when(port.page(1L, 10L, "COMPLETED")).thenReturn(mock(IngestionTaskPage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIngestionTaskController(provider(IngestionTaskInboundPort.class, port))).build();

        mvc.perform(get("/ingestion/tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).page(1L, 10L, "COMPLETED");
    }

    @Test
    void shouldReturnControlledErrorWhenIngestionTaskServiceIsMissing() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIngestionTaskController(emptyProvider(IngestionTaskInboundPort.class))).build();

        mvc.perform(get("/ingestion/tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("Service not available"));
    }

    // --- IntentTree ---

    @Test
    void shouldListIntentTrees() throws Exception {
        IntentTreeInboundPort port = mock(IntentTreeInboundPort.class);
        when(port.tree()).thenReturn(List.of(mock(IntentNodeTree.class)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIntentTreeController(provider(IntentTreeInboundPort.class, port))).build();

        mvc.perform(get("/intent-tree/trees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).tree();
    }

    @Test
    void shouldCreateIntentNode() throws Exception {
        IntentTreeInboundPort port = mock(IntentTreeInboundPort.class);
        when(port.create(any())).thenReturn("node-1");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIntentTreeController(provider(IntentTreeInboundPort.class, port))).build();

        mvc.perform(post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "greeting",
                                  "parentId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("node-1"));

        verify(port).create(any());
    }

    @Test
    void shouldDeleteIntentNode() throws Exception {
        IntentTreeInboundPort port = mock(IntentTreeInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseIntentTreeController(provider(IntentTreeInboundPort.class, port))).build();

        mvc.perform(delete("/intent-tree/node-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete("node-1");
    }

    // --- DocumentRefresh ---

    @Test
    void shouldRefreshDocument() throws Exception {
        DocumentRefreshInboundPort port = mock(DocumentRefreshInboundPort.class);
        when(port.refreshDocument("doc-1", "admin-1")).thenReturn(mock(DocumentRefreshResult.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseDocumentRefreshController(provider(DocumentRefreshInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/docs/doc-1/refresh")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).refreshDocument("doc-1", "admin-1");
    }

    // --- KeywordIndexMaintenance ---

    @Test
    void shouldRebuildDocumentKeywordIndex() throws Exception {
        KeywordIndexMaintenanceInboundPort port = mock(KeywordIndexMaintenanceInboundPort.class);
        when(port.rebuildDocument(1L)).thenReturn(
                mock(com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexRebuildResult.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKeywordIndexMaintenanceController(provider(KeywordIndexMaintenanceInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/docs/1/keyword-index/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).rebuildDocument(1L);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }
}
