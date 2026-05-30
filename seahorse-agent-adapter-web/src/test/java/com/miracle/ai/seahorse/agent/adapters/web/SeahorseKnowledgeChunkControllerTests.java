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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkInboundPort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseKnowledgeChunkControllerTests {

    @Test
    void shouldPageChunks() throws Exception {
        KnowledgeChunkInboundPort port = mock(KnowledgeChunkInboundPort.class);
        when(port.page(eq("doc-1"), any())).thenReturn(
                new com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkPage(
                        java.util.List.of(), 0L, 10L, 1L, 1L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeChunkController(provider(KnowledgeChunkInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/docs/doc-1/chunks")
                        .param("current", "1")
                        .param("size", "10")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).page(eq("doc-1"), any());
    }

    @Test
    void shouldCreateChunk() throws Exception {
        KnowledgeChunkInboundPort port = mock(KnowledgeChunkInboundPort.class);
        when(port.create(eq("doc-1"), any())).thenReturn(
                mock(com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeChunkController(provider(KnowledgeChunkInboundPort.class, port))).build();

        mvc.perform(post("/knowledge-base/docs/doc-1/chunks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chunkId": "chunk-1",
                                  "content": "test content",
                                  "index": 0
                                }
                                """)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).create(eq("doc-1"), any());
    }

    @Test
    void shouldUpdateChunk() throws Exception {
        KnowledgeChunkInboundPort port = mock(KnowledgeChunkInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeChunkController(provider(KnowledgeChunkInboundPort.class, port))).build();

        mvc.perform(put("/knowledge-base/docs/doc-1/chunks/chunk-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "updated content"
                                }
                                """)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).update(eq("doc-1"), eq("chunk-1"), any());
    }

    @Test
    void shouldDeleteChunk() throws Exception {
        KnowledgeChunkInboundPort port = mock(KnowledgeChunkInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeChunkController(provider(KnowledgeChunkInboundPort.class, port))).build();

        mvc.perform(delete("/knowledge-base/docs/doc-1/chunks/chunk-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete("doc-1", "chunk-1");
    }

    @Test
    void shouldEnableChunk() throws Exception {
        KnowledgeChunkInboundPort port = mock(KnowledgeChunkInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeChunkController(provider(KnowledgeChunkInboundPort.class, port))).build();

        mvc.perform(patch("/knowledge-base/docs/doc-1/chunks/chunk-1/enable")
                        .param("value", "true")
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).enable("doc-1", "chunk-1", true, "admin-1");
    }

    @Test
    void shouldBatchEnableChunks() throws Exception {
        KnowledgeChunkInboundPort port = mock(KnowledgeChunkInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseKnowledgeChunkController(provider(KnowledgeChunkInboundPort.class, port))).build();

        mvc.perform(patch("/knowledge-base/docs/doc-1/chunks/batch-enable")
                        .param("value", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chunkIds": ["chunk-1", "chunk-2"]
                                }
                                """)
                        .header("X-User-Id", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).batchEnable(eq("doc-1"), captor.capture(), eq(false), eq("admin-1"));
        assertThat(captor.getValue()).containsExactly("chunk-1", "chunk-2");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
