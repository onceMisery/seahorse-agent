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

import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.KernelAgentMarketplaceService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentPublishReview;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseMarketplaceControllerTests {

    @Test
    void shouldListPendingReviews() throws Exception {
        KernelAgentMarketplaceService service = mock(KernelAgentMarketplaceService.class);
        when(service.listPendingReviews(1, 20)).thenReturn(List.of(review(1L, "agent-1")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseMarketplaceController(
                provider(KernelAgentMarketplaceService.class, service),
                provider(AgentCatalogQueryPort.class, null),
                currentUserPort())).build();

        mvc.perform(get("/api/marketplace/reviews/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].agentId").value("agent-1"));

        verify(service).listPendingReviews(1, 20);
    }

    @Test
    void shouldSubmitForReview() throws Exception {
        KernelAgentMarketplaceService service = mock(KernelAgentMarketplaceService.class);
        when(service.submitForReview("agent-1", "admin")).thenReturn(99L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseMarketplaceController(
                provider(KernelAgentMarketplaceService.class, service),
                provider(AgentCatalogQueryPort.class, null),
                currentUserPort())).build();

        mvc.perform(post("/api/marketplace/agents/agent-1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(99L));

        verify(service).submitForReview("agent-1", "admin");
    }

    @Test
    void shouldApproveReview() throws Exception {
        KernelAgentMarketplaceService service = mock(KernelAgentMarketplaceService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseMarketplaceController(
                provider(KernelAgentMarketplaceService.class, service),
                provider(AgentCatalogQueryPort.class, null),
                currentUserPort())).build();

        mvc.perform(put("/api/marketplace/reviews/7/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(service).approve(7L, "admin", null);
    }

    private static AgentPublishReview review(Long id, String agentId) {
        return new AgentPublishReview(
                id, agentId, "tenant-a", "admin", AgentPublishReview.PENDING, null, null,
                Instant.parse("2026-06-01T00:00:00Z"), null);
    }

    private static CurrentUserPort currentUserPort() {
        return new CurrentUserPort() {
            @Override
            public Optional<CurrentUser> currentUser() {
                return Optional.of(new CurrentUser(1L, "admin", "admin", null, "tenant-a"));
            }
        };
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }
}
