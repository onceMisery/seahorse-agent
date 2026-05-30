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

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataDictionaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataExtractionResultInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SeahorseMetadataControllerTests {

    @Test
    void shouldListDictionaryItems() throws Exception {
        MetadataDictionaryInboundPort port = mock(MetadataDictionaryInboundPort.class);
        when(port.listItems("tenant-a", "status", false)).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataDictionaryController(provider(MetadataDictionaryInboundPort.class, port))).build();

        mvc.perform(get("/metadata-dictionaries/items")
                        .param("tenantId", "tenant-a")
                        .param("dictCode", "status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listItems("tenant-a", "status", false);
    }

    @Test
    void shouldCreateDictionaryItem() throws Exception {
        MetadataDictionaryInboundPort port = mock(MetadataDictionaryInboundPort.class);
        when(port.createItem(any())).thenReturn(mock(MetadataDictionaryItemRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataDictionaryController(provider(MetadataDictionaryInboundPort.class, port))).build();

        mvc.perform(post("/metadata-dictionaries/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "dictionaryCode": "status",
                                  "rawValue": "active",
                                  "canonicalValue": "ACTIVE",
                                  "displayName": "Active",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).createItem(any());
    }

    @Test
    void shouldUpdateDictionaryItem() throws Exception {
        MetadataDictionaryInboundPort port = mock(MetadataDictionaryInboundPort.class);
        when(port.updateItem(eq("item-1"), any())).thenReturn(mock(MetadataDictionaryItemRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataDictionaryController(provider(MetadataDictionaryInboundPort.class, port))).build();

        mvc.perform(put("/metadata-dictionaries/items/item-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "dictionaryCode": "status",
                                  "rawValue": "active",
                                  "canonicalValue": "ACTIVE",
                                  "displayName": "Active Updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).updateItem(eq("item-1"), any());
    }

    @Test
    void shouldDeleteDictionaryItem() throws Exception {
        MetadataDictionaryInboundPort port = mock(MetadataDictionaryInboundPort.class);
        when(port.deleteItem("item-1")).thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataDictionaryController(provider(MetadataDictionaryInboundPort.class, port))).build();

        mvc.perform(delete("/metadata-dictionaries/items/item-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(port).deleteItem("item-1");
    }

    @Test
    void shouldListSchemaFields() throws Exception {
        MetadataSchemaInboundPort port = mock(MetadataSchemaInboundPort.class);
        when(port.listFields("tenant-a", "kb-1")).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataSchemaController(provider(MetadataSchemaInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/kb-1/metadata-schema/fields")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listFields("tenant-a", "kb-1");
    }

    @Test
    void shouldListFieldCapabilities() throws Exception {
        MetadataSchemaInboundPort port = mock(MetadataSchemaInboundPort.class);
        when(port.listFieldCapabilities("tenant-a", "kb-1")).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataSchemaController(provider(MetadataSchemaInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/kb-1/metadata-schema/field-capabilities")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).listFieldCapabilities("tenant-a", "kb-1");
    }

    @Test
    void shouldPageQuarantineItems() throws Exception {
        MetadataQuarantineInboundPort port = mock(MetadataQuarantineInboundPort.class);
        when(port.page(eq("tenant-a"), eq("kb-1"), eq(false), any(), any(), any(), any(), eq(1L), eq(10L)))
                .thenReturn(mock(MetadataQuarantinePage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataQuarantineController(provider(MetadataQuarantineInboundPort.class, port))).build();

        mvc.perform(get("/metadata-quarantine/items")
                        .param("tenantId", "tenant-a")
                        .param("kbId", "kb-1")
                        .param("resolved", "false")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldPageReviewItems() throws Exception {
        MetadataReviewInboundPort port = mock(MetadataReviewInboundPort.class);
        when(port.page(eq("tenant-a"), eq("kb-1"), any(), any(), any(), eq(1L), eq(10L)))
                .thenReturn(mock(MetadataReviewPage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataReviewController(provider(MetadataReviewInboundPort.class, port))).build();

        mvc.perform(get("/metadata-review/items")
                        .param("tenantId", "tenant-a")
                        .param("kbId", "kb-1")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldPageExtractionResults() throws Exception {
        MetadataExtractionResultInboundPort port = mock(MetadataExtractionResultInboundPort.class);
        when(port.page(eq("tenant-a"), eq("kb-1"), eq("doc-1"), eq("job-1"), eq("SUCCEEDED"),
                        any(), any(), eq(1L), eq(10L)))
                .thenReturn(mock(MetadataExtractionResultPage.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataExtractionResultController(provider(MetadataExtractionResultInboundPort.class, port))).build();

        mvc.perform(get("/metadata-extraction/results")
                        .param("tenantId", "tenant-a")
                        .param("kbId", "kb-1")
                        .param("docId", "doc-1")
                        .param("jobId", "job-1")
                        .param("status", "SUCCEEDED")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void shouldQueryExtractionResultById() throws Exception {
        MetadataExtractionResultInboundPort port = mock(MetadataExtractionResultInboundPort.class);
        when(port.queryById("result-1")).thenReturn(
                mock(com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataExtractionResultController(provider(MetadataExtractionResultInboundPort.class, port))).build();

        mvc.perform(get("/metadata-extraction/results/result-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).queryById("result-1");
    }

    @Test
    void shouldExposeMetadataQualityReport() throws Exception {
        MetadataQualityInboundPort port = mock(MetadataQualityInboundPort.class);
        when(port.report(eq("tenant-a"), eq("kb-1"), eq(5), any(), any(), any()))
                .thenReturn(mock(MetadataQualityReport.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseMetadataQualityController(provider(MetadataQualityInboundPort.class, port))).build();

        mvc.perform(get("/knowledge-base/kb-1/metadata-quality/report")
                        .param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
