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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseKnowledgeDocumentControllerTests {

    @Test
    void shouldStartChunk() throws Exception {
        KnowledgeDocumentInboundPort port = mock(KnowledgeDocumentInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/docs/1/chunk")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).startChunk(1L, "admin-1");
    }

    @Test
    void shouldDeleteDocument() throws Exception {
        KnowledgeDocumentInboundPort port = mock(KnowledgeDocumentInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, port))).build();

        mvc.perform(delete("/knowledge-base/docs/1")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete(1L, "admin-1");
    }

    @Test
    void shouldQueryDocumentById() throws Exception {
        KnowledgeDocumentInboundPort port = mock(KnowledgeDocumentInboundPort.class);
        when(port.queryById(1L)).thenReturn(mock(KnowledgeDocumentDetail.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/docs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).queryById(1L);
    }

    @Test
    void shouldPageDocuments() throws Exception {
        KnowledgeDocumentInboundPort port = mock(KnowledgeDocumentInboundPort.class);
        when(port.page(eq(1L), any())).thenReturn(
                new KnowledgeDocumentPage(java.util.List.of(), 0L, 10L, 0L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/1/docs")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).page(eq(1L), any());
    }

    @Test
    void shouldSearchDocuments() throws Exception {
        KnowledgeDocumentInboundPort port = mock(KnowledgeDocumentInboundPort.class);
        when(port.search("test", 8)).thenReturn(java.util.List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/docs/search")
                        .param("keyword", "test")
                        .param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).search("test", 8);
    }

    @Test
    void shouldEnableDocument() throws Exception {
        KnowledgeDocumentInboundPort port = mock(KnowledgeDocumentInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, port))).build();

        mvc.perform(patch("/knowledge-base/docs/1/enable")
                        .param("value", "true")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).enable(1L, true, "admin-1");
    }

    @Test
    void shouldQueryChunkLogs() throws Exception {
        KnowledgeDocumentInboundPort port = mock(KnowledgeDocumentInboundPort.class);
        when(port.chunkLogs(1L, 1L, 10L)).thenReturn(
                new KnowledgeDocumentChunkLogPage(java.util.List.of(), 0L, 10L, 0L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeDocumentController(provider(KnowledgeDocumentInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/docs/1/chunk-logs")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).chunkLogs(1L, 1L, 10L);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
