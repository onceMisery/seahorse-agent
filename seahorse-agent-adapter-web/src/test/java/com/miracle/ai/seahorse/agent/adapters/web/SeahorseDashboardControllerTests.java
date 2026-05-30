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

import com.miracle.ai.seahorse.agent.ports.inbound.dashboard.DashboardInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardPerformance;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrends;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseDashboardControllerTests {

    @Test
    void shouldExposeDashboardOverview() throws Exception {
        DashboardInboundPort port = mock(DashboardInboundPort.class);
        when(port.overview("7d")).thenReturn(mock(DashboardOverview.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseDashboardController(provider(DashboardInboundPort.class, port))).build();

        mvc.perform(get("/admin/dashboard/overview")
                        .param("window", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).overview("7d");
    }

    @Test
    void shouldExposeDashboardPerformance() throws Exception {
        DashboardInboundPort port = mock(DashboardInboundPort.class);
        when(port.performance("30d")).thenReturn(mock(DashboardPerformance.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseDashboardController(provider(DashboardInboundPort.class, port))).build();

        mvc.perform(get("/admin/dashboard/performance")
                        .param("window", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).performance("30d");
    }

    @Test
    void shouldExposeDashboardTrends() throws Exception {
        DashboardInboundPort port = mock(DashboardInboundPort.class);
        when(port.trends("latency", "7d", "day")).thenReturn(mock(DashboardTrends.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseDashboardController(provider(DashboardInboundPort.class, port))).build();

        mvc.perform(get("/admin/dashboard/trends")
                        .param("metric", "latency")
                        .param("window", "7d")
                        .param("granularity", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).trends("latency", "7d", "day");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
