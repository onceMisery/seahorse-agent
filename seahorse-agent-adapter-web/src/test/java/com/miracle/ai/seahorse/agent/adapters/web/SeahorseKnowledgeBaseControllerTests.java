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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.CreateKnowledgeBaseCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeBaseCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseKnowledgeBaseControllerTests {

    @Test
    void shouldCreateKnowledgeBase() throws Exception {
        KnowledgeBaseInboundPort port = mock(KnowledgeBaseInboundPort.class);
        when(port.create(any())).thenReturn("kb-1");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeBaseController(provider(KnowledgeBaseInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test KB",
                                  "embeddingModel": "text-embedding-3-small",
                                  "collectionName": "test_collection"
                                }
                                """)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("kb-1"));

        ArgumentCaptor<CreateKnowledgeBaseCommand> captor =
                ArgumentCaptor.forClass(CreateKnowledgeBaseCommand.class);
        verify(port).create(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Test KB");
    }

    @Test
    void shouldUpdateKnowledgeBase() throws Exception {
        KnowledgeBaseInboundPort port = mock(KnowledgeBaseInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeBaseController(provider(KnowledgeBaseInboundPort.class, port))).build();

        mvc.perform(put("/knowledge-base/kb-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated KB",
                                  "embeddingModel": "text-embedding-3-large"
                                }
                                """)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).update(eq("kb-1"), any(UpdateKnowledgeBaseCommand.class));
    }

    @Test
    void shouldDeleteKnowledgeBase() throws Exception {
        KnowledgeBaseInboundPort port = mock(KnowledgeBaseInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeBaseController(provider(KnowledgeBaseInboundPort.class, port))).build();

        mvc.perform(delete("/knowledge-base/kb-1")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete("kb-1", "admin-1");
    }

    @Test
    void shouldQueryKnowledgeBaseById() throws Exception {
        KnowledgeBaseInboundPort port = mock(KnowledgeBaseInboundPort.class);
        when(port.queryById("kb-1")).thenReturn(
                mock(com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeBaseController(provider(KnowledgeBaseInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).queryById("kb-1");
    }

    @Test
    void shouldPageKnowledgeBases() throws Exception {
        KnowledgeBaseInboundPort port = mock(KnowledgeBaseInboundPort.class);
        when(port.page(any())).thenReturn(
                new com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePage(
                        java.util.List.of(), 0L, 10L, 1L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeBaseController(provider(KnowledgeBaseInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base")
                        .param("current", "1")
                        .param("size", "10")
                        .param("name", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).page(any());
    }

    @Test
    void shouldListChunkStrategies() throws Exception {
        KnowledgeBaseInboundPort port = mock(KnowledgeBaseInboundPort.class);
        when(port.listChunkStrategies()).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeBaseController(provider(KnowledgeBaseInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/chunk-strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listChunkStrategies();
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
