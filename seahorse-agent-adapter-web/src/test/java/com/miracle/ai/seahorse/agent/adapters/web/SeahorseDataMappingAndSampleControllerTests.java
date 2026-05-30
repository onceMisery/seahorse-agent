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

import com.miracle.ai.seahorse.agent.ports.inbound.mapping.QueryTermMappingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPage;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRecord;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SeahorseDataMappingAndSampleControllerTests {

    // --- QueryTermMapping ---

    @Test
    void shouldPageQueryTermMappings() throws Exception {
        QueryTermMappingInboundPort port = mock(QueryTermMappingInboundPort.class);
        when(port.page(1L, 10L, "test")).thenReturn(mock(QueryTermMappingPage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseQueryTermMappingController(provider(QueryTermMappingInboundPort.class, port))).build();

        mvc.perform(get("/mappings")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).page(1L, 10L, "test");
    }

    @Test
    void shouldQueryTermMappingById() throws Exception {
        QueryTermMappingInboundPort port = mock(QueryTermMappingInboundPort.class);
        when(port.queryById("mapping-1")).thenReturn(mock(QueryTermMappingRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseQueryTermMappingController(provider(QueryTermMappingInboundPort.class, port))).build();

        mvc.perform(get("/mappings/mapping-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).queryById("mapping-1");
    }

    @Test
    void shouldCreateQueryTermMapping() throws Exception {
        QueryTermMappingInboundPort port = mock(QueryTermMappingInboundPort.class);
        when(port.create(any())).thenReturn("mapping-1");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseQueryTermMappingController(provider(QueryTermMappingInboundPort.class, port))).build();

        mvc.perform(post("/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceTerm": "AI",
                                  "targetTerm": "Artificial Intelligence",
                                  "tenantId": "tenant-a"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("mapping-1"));

        verify(port).create(any());
    }

    @Test
    void shouldUpdateQueryTermMapping() throws Exception {
        QueryTermMappingInboundPort port = mock(QueryTermMappingInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseQueryTermMappingController(provider(QueryTermMappingInboundPort.class, port))).build();

        mvc.perform(put("/mappings/mapping-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceTerm": "AI",
                                  "targetTerm": "Machine Intelligence",
                                  "tenantId": "tenant-a"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).update(eq("mapping-1"), any());
    }

    @Test
    void shouldDeleteQueryTermMapping() throws Exception {
        QueryTermMappingInboundPort port = mock(QueryTermMappingInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseQueryTermMappingController(provider(QueryTermMappingInboundPort.class, port))).build();

        mvc.perform(delete("/mappings/mapping-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete("mapping-1");
    }

    // --- SampleQuestion ---

    @Test
    void shouldListRandomQuestions() throws Exception {
        SampleQuestionInboundPort port = mock(SampleQuestionInboundPort.class);
        when(port.listRandomQuestions()).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSampleQuestionController(provider(SampleQuestionInboundPort.class, port))).build();

        mvc.perform(get("/rag/sample-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listRandomQuestions();
    }

    @Test
    void shouldPageSampleQuestions() throws Exception {
        SampleQuestionInboundPort port = mock(SampleQuestionInboundPort.class);
        when(port.page(any())).thenReturn(mock(SampleQuestionPage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSampleQuestionController(provider(SampleQuestionInboundPort.class, port))).build();

        mvc.perform(get("/sample-questions")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldCreateSampleQuestion() throws Exception {
        SampleQuestionInboundPort port = mock(SampleQuestionInboundPort.class);
        when(port.create(any())).thenReturn("sq-1");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSampleQuestionController(provider(SampleQuestionInboundPort.class, port))).build();

        mvc.perform(post("/sample-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Test Title",
                                  "description": "Test Desc",
                                  "question": "What is RAG?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("sq-1"));

        verify(port).create(any());
    }

    @Test
    void shouldDeleteSampleQuestion() throws Exception {
        SampleQuestionInboundPort port = mock(SampleQuestionInboundPort.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSampleQuestionController(provider(SampleQuestionInboundPort.class, port))).build();

        mvc.perform(delete("/sample-questions/sq-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).delete("sq-1");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
