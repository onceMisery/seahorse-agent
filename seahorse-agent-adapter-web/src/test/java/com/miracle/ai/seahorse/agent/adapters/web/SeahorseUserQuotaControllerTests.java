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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.EstimatedDurationTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaCostTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaSummaryStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.UserQuotaSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.UserQuotaSummaryQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseUserQuotaControllerTests {

    @Test
    void shouldExposeCurrentUserQuotaSummaryWithoutQuotaManagementGate() throws Exception {
        QuotaSummaryInboundPort port = mock(QuotaSummaryInboundPort.class);
        when(port.summary(org.mockito.ArgumentMatchers.any())).thenReturn(new UserQuotaSummary(
                "user-1",
                "tenant-a",
                QuotaSummaryStatus.AVAILABLE,
                100L,
                20L,
                80L,
                12.5d,
                2.5d,
                10.0d,
                QuotaCostTier.MEDIUM,
                EstimatedDurationTier.MEDIUM,
                "Quota is available."));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseUserQuotaController(provider(QuotaSummaryInboundPort.class, port))).build();

        mvc.perform(get("/api/me/quota-summary")
                        .param("tenantId", "tenant-a")
                        .param("taskTemplateId", "deep-research")
                        .header(WebUserIdResolver.HEADER_USER_ID, "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.remainingCalls").value(80))
                .andExpect(jsonPath("$.data.defaultCostTier").value("MEDIUM"));

        ArgumentCaptor<UserQuotaSummaryQuery> captor = ArgumentCaptor.forClass(UserQuotaSummaryQuery.class);
        verify(port).summary(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().taskTemplateId()).isEqualTo("deep-research");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }
}
