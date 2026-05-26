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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SreHealthInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseSreHealthControllerTests {

    @Test
    void shouldExposeHealthReportWithoutStackTrace() throws Exception {
        SreHealthInboundPort port = mock(SreHealthInboundPort.class);
        when(port.current()).thenReturn(new SreHealthReport(
                "sre-1",
                SreHealthStatus.WARN,
                List.of(new SreHealthItem(
                        "contributor-1",
                        SreHealthStatus.WARN,
                        "Contributor health check failed",
                        "health:contributor-1")),
                Instant.parse("2026-05-26T00:00:00Z")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseSreHealthController(provider(SreHealthInboundPort.class, port))).build();

        mvc.perform(get("/api/sre/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reportId").value("sre-1"))
                .andExpect(jsonPath("$.data.status").value("WARN"))
                .andExpect(jsonPath("$.data.items[0].contributorName").value("contributor-1"))
                .andExpect(content().string(not(containsString("java.lang.RuntimeException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
