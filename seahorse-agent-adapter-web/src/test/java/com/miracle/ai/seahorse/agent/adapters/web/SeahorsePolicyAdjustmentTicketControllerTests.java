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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketBatchDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketNotFoundException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReviewRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PreviousProposalOutcome;
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
import java.util.Map;

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
        when(port.latestReviews(any())).thenReturn(Map.of());

        mvc().perform(get("/api/policy-adjustment-tickets")
                        .param("tenantId", "default")
                        .param("status", "PENDING")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.page.total").value(1))
                .andExpect(jsonPath("$.data.page.records[0].ticketId").value("pat-1"))
                .andExpect(jsonPath("$.data.page.records[0].targetKey").value("user:preference:language"))
                .andExpect(jsonPath("$.data.page.records[0].policyKey").value("memory.captureAcceptThreshold"))
                .andExpect(jsonPath("$.data.page.records[0].direction").value("TIGHTEN"))
                .andExpect(jsonPath("$.data.page.records[0].disapprovalRate").value(0.72))
                .andExpect(jsonPath("$.data.page.records[0].sampleCount").value(18));
    }

    @Test
    void shouldCarryLatestReviewIntoThePageResponse() throws Exception {
        when(port.page(eq("default"), isNull(), anyLong(), anyLong()))
                .thenReturn(new PolicyAdjustmentTicketPage(List.of(pendingTicket()), 1L, 10L, 1L, 1L));
        when(port.latestReviews(any())).thenReturn(Map.of("pat-1", review("pat-1")));

        mvc().perform(get("/api/policy-adjustment-tickets").param("tenantId", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestReviews.pat-1.outcome").value("REGRESSED"))
                // rollbackRecommended 是派生判定（outcome == REGRESSED），不进 JSON——
                // 前端按 outcome 自己算，避免"记录里存了布尔、枚举却又不一致"的两处真相
                .andExpect(jsonPath("$.data.latestReviews.pat-1.rollbackRecommended").doesNotExist());
    }

    @Test
    void shouldRunReviewAndReportSkipCounts() throws Exception {
        when(port.reviewAppliedTickets(eq("default"), eq("batch-1"), any()))
                .thenReturn(new PolicyAdjustmentTicketReviewRunResult(
                        List.of(review("pat-1")), 3, 1, "扫描 3 张，写入 1 条，跳过 1 张"));

        mvc().perform(post("/api/policy-adjustment-tickets/reviews/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\": \"default\", \"batchId\": \"batch-1\","
                                + " \"baselinePassRate\": 0.6, \"currentPassRate\": 0.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reviews[0].ticketId").value("pat-1"))
                .andExpect(jsonPath("$.data.scanned").value(3))
                .andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    void shouldListReviewHistoryForOneTicket() throws Exception {
        when(port.listReviews("pat-1", 20)).thenReturn(List.of(review("pat-1")));

        mvc().perform(get("/api/policy-adjustment-tickets/pat-1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outcome").value("REGRESSED"))
                .andExpect(jsonPath("$.data[0].reviewedAt").exists());
    }

    @Test
    void shouldApproveBatchAndReturnPerTicketOutcome() throws Exception {
        when(port.decideBatch(any(), eq(PolicyAdjustmentTicketStatus.APPROVED), eq("alice"), eq("同意")))
                .thenReturn(new PolicyAdjustmentTicketBatchDecisionResult(
                        PolicyAdjustmentTicketStatus.APPROVED, List.of("pat-1"), List.of()));

        mvc().perform(post("/api/policy-adjustment-tickets/batch-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\": [\"pat-1\"], \"operator\": \"alice\", \"comment\": \"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.succeededTicketIds[0]").value("pat-1"))
                .andExpect(jsonPath("$.data.failed").isEmpty())
                .andExpect(jsonPath("$.data.succeededTicketIds.length()").value(1));
    }

    @Test
    void shouldReturnOkWithFailedDetailsOnPartialFailure() throws Exception {
        // 部分失败不能是 500：前端需要逐条展示"哪张失败了、为什么"
        when(port.decideBatch(any(), eq(PolicyAdjustmentTicketStatus.REJECTED), eq("alice"), eq("不采纳")))
                .thenReturn(new PolicyAdjustmentTicketBatchDecisionResult(
                        PolicyAdjustmentTicketStatus.REJECTED,
                        List.of("pat-1"),
                        List.of(new PolicyAdjustmentTicketBatchDecisionResult.Failure(
                                "pat-2", "工单状态已被其他审批人变更，请刷新后重试"))));

        mvc().perform(post("/api/policy-adjustment-tickets/batch-reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\": [\"pat-1\", \"pat-2\"],"
                                + " \"operator\": \"alice\", \"comment\": \"不采纳\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.succeededTicketIds[0]").value("pat-1"))
                .andExpect(jsonPath("$.data.failed[0].ticketId").value("pat-2"))
                .andExpect(jsonPath("$.data.failed[0].reason").value("工单状态已被其他审批人变更，请刷新后重试"));
    }

    @Test
    void shouldReturnBadRequestWhenBatchTargetUnsupported() throws Exception {
        when(port.decideBatch(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("批量审批只支持通过或驳回"));

        mvc().perform(post("/api/policy-adjustment-tickets/batch-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\": [\"pat-1\"], \"targetStatus\": \"APPLIED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
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
        when(port.runAndGenerateTickets(isNull(), isNull(), anyInt(), any()))
                .thenReturn(new LearningLoopRunResult(new LearningLoopReport(null, List.of(), List.of(), null, ""),
                        List.of(pendingTicket())));

        mvc().perform(post("/api/policy-adjustment-tickets/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleLimit\": 99999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tickets[0].ticketId").value("pat-1"));

        verify(port).runAndGenerateTickets(isNull(), isNull(), eq(1000), any());
    }

    @Test
    void shouldRejectNonPositiveSampleLimit() throws Exception {
        when(port.runAndGenerateTickets(isNull(), isNull(), anyInt(), any()))
                .thenReturn(new LearningLoopRunResult(null, List.of()));

        mvc().perform(post("/api/policy-adjustment-tickets/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleLimit\": -5}"))
                .andExpect(status().isOk());

        // 下限夹到 1，不会因为负数把整张表拖进来
        verify(port).runAndGenerateTickets(isNull(), isNull(), eq(1), any());
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

    private static PolicyAdjustmentTicketReview review(String ticketId) {
        return new PolicyAdjustmentTicketReview(
                "par-1", ticketId, "default", "batch-1",
                PreviousProposalOutcome.Status.REGRESSED, "通过率下降 10.0%，建议回滚",
                0.70D, 0.60D, "eval", NOW);
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
