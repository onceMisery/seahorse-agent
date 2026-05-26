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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateOutputType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.EstimatedDurationTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaCostTier;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseTaskTemplateControllerTests {

    @Test
    void shouldExposeConsumerWebTaskTemplatesWithoutAdvancedGate() throws Exception {
        TaskTemplateQueryInboundPort port = mock(TaskTemplateQueryInboundPort.class);
        when(port.listEnabled()).thenReturn(List.of(template(TaskTemplateId.DEEP_RESEARCH)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseTaskTemplateController(provider(TaskTemplateQueryInboundPort.class, port))).build();

        mvc.perform(get("/api/task-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].templateId").value("deep-research"))
                .andExpect(jsonPath("$.data[0].category").value("RESEARCH"))
                .andExpect(jsonPath("$.data[0].maxCostTier").value("HIGH"));

        verify(port).listEnabled();
    }

    @Test
    void shouldFindTemplateByStableId() throws Exception {
        TaskTemplateQueryInboundPort port = mock(TaskTemplateQueryInboundPort.class);
        when(port.findById(TaskTemplateId.WEB_SUMMARY)).thenReturn(Optional.of(template(TaskTemplateId.WEB_SUMMARY)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseTaskTemplateController(provider(TaskTemplateQueryInboundPort.class, port))).build();

        mvc.perform(get("/api/task-templates/web-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateId").value("web-summary"));

        verify(port).findById(TaskTemplateId.WEB_SUMMARY);
    }

    private static TaskTemplate template(TaskTemplateId id) {
        return new TaskTemplate(
                id,
                "Deep research",
                "Search public web sources and produce a cited report.",
                TaskTemplateCategory.RESEARCH,
                null,
                "consumer-web-default",
                TaskTemplateOutputType.MARKDOWN_REPORT,
                QuotaCostTier.HIGH,
                EstimatedDurationTier.LONG,
                true);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
