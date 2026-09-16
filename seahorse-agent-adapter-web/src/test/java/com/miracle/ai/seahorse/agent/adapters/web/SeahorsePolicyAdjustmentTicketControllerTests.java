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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketNotFoundException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工单接口的契约测试：响应形状、camelCase 字段、以及错误码映射。
 *
 * <p>错误码这一段是核心——内核抛的是领域异常，不认识 HTTP。如果"工单不存在"漏到
 * {@code IllegalArgumentException} 分支就会变成 400，前端会误判成参数错误。
 */
class SeahorsePolicyAdjustmentTicketControllerTests {

    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    private final PolicyAdjustmentTicketManagementInboundPort port =
            mock(PolicyAdjustmentTicketManagementInboundPort.class);

    @Test
    void shouldPageTicketsWithCamelCaseFields() throws Exception {
        when(port.page(eq("default"), eq(PolicyAdjustmentTicketStatus.PENDING), anyLong(), anyLong()))
                .thenReturn(new PolicyAdjustmentTicketPage(List.of(pendingTicket()), 1L, 10L, 1L, 1L));

        mvc().perform(get("/api/policy-adjustment-tickets")
                        .param("tenantId", "default")
                        .param("status", "PENDING")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ticketId").value("pat-1"))
                .andExpect(jsonPath("$.data.records[0].targetKey").value("user:preference:language"))
                .andExpect(jsonPath("$.data.records[0].policyKey").value("memory.captureAcceptThreshold"))
                .andExpect(jsonPath("$.data.records[0].direction").value("TIGHTEN"))
                .andExpect(jsonPath("$.data.records[0].disapprovalRate").value(0.72))
                .andExpect(jsonPath("$.data.records[0].sampleCount").value(18));
    }

    @Test
    void shouldReturnNotFoundWhenTicketMissing() throws Exception {
        when(port.findById("pat-missing")).thenReturn(java.util.Optional.empty());

        mvc().perform(get("/api/policy-adjustment-tickets/pat-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldTranslateKernelNotFoundToHttp404OnDecision() throws Exception {
        when(port.decide(any())).thenThrow(new PolicyAdjustmentTicketNotFoundException("pat-missing"));

        mvc().perform(post("/api/policy-adjustment-tickets/pat-missing/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\": \"alice\", \"comment\": \"同意\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnBadRequestOnIllegalTransition() throws Exception {
        when(port.decide(any())).thenThrow(new IllegalArgumentException("illegal ticket transition"));

        mvc().perform(post("/api/policy-adjustment-tickets/pat-1/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\": \"alice\", \"comment\": \"已改完\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldReturnConflictWhenAnotherReviewerWon() throws Exception {
        when(port.decide(any())).thenThrow(new IllegalStateException("ticket status changed by another reviewer"));

        mvc().perform(post("/api/policy-adjustment-tickets/pat-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\": \"alice\", \"comment\": \"不采纳\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void shouldClampSampleLimitBeforeRunningTheLoop() throws Exception {
        when(port.runAndGenerateTickets(isNull(), isNull(), anyInt()))
                .thenReturn(new LearningLoopRunResult(new LearningLoopReport(null, List.of(), List.of(), null, ""),
                        List.of(pendingTicket())));

        mvc().perform(post("/api/policy-adjustment-tickets/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleLimit\": 99999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tickets[0].ticketId").value("pat-1"));

        verify(port).runAndGenerateTickets(null, null, 1000);
    }

    @Test
    void shouldRejectNonPositiveSampleLimit() throws Exception {
        when(port.runAndGenerateTickets(isNull(), isNull(), anyInt()))
                .thenReturn(new LearningLoopRunResult(null, List.of()));

        mvc().perform(post("/api/policy-adjustment-tickets/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleLimit\": -5}"))
                .andExpect(status().isOk());

        // 下限夹到 1，不会因为负数把整张表拖进来
        verify(port).runAndGenerateTickets(null, null, 1);
    }

    private static PolicyAdjustmentTicket pendingTicket() {
        PolicyAdjustmentProposal proposal = new PolicyAdjustmentProposal(
                "user:preference:language",
                PolicyAdjustmentProposal.POLICY_CAPTURE_ACCEPT_THRESHOLD,
                PolicyAdjustmentProposal.Direction.TIGHTEN,
                0.05D,
                0.72D,
                18,
                "连续三轮被点踩且复核被拒",
                List.of("msg-1", "msg-2"));
        return PolicyAdjustmentTicket.pending("pat-1", "default", proposal, "fp-1", "learning-loop", NOW);
    }

    private MockMvc mvc() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("ticketPort", port);
        ObjectProvider<PolicyAdjustmentTicketManagementInboundPort> provider =
                beanFactory.getBeanProvider(PolicyAdjustmentTicketManagementInboundPort.class);
        return MockMvcBuilders.standaloneSetup(new SeahorsePolicyAdjustmentTicketController(provider))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }
}
