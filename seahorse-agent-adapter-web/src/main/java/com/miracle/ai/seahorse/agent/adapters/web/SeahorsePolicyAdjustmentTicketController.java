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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketBatchDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketNotFoundException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReviewRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 策略调整落地工单的查询与审批 API。
 *
 * <p>刻意没有"一键应用"：{@code /apply} 表示"人已经在配置侧改完了，工单登记一下"，
 * 服务端不会去改任何阈值。把这一步自动化，等于让一个靠几天延迟的反馈信号驱动的闭环
 * 直接调生产流量。
 *
 * <h2>接口契约</h2>
 * <p>所有响应统一包在 {@code {code, message, data}} 里，成功码为 {@code "0"}。
 * 错误码由 {@code SeahorseWebExceptionHandler} 统一映射：
 * <ul>
 *   <li>{@code 400 INVALID_ARGUMENT} —— 参数不合法，含"状态迁移不被允许"
 *       （如 PENDING 直接跳 APPLIED）、"拒绝/落地必须填写意见"。</li>
 *   <li>{@code 404 RESOURCE_NOT_FOUND} —— 工单不存在。</li>
 *   <li>{@code 409 CONFLICT} —— 工单已被其他审批人改动，需重新加载后再决策。</li>
 * </ul>
 *
 * <p><b>批量审批的失败语义是刻意不同的</b>：{@code /batch-approve} 与 {@code /batch-reject}
 * 在部分失败时仍返回 {@code 200}，失败明细放在响应的 {@code failed} 数组里。
 * 整批抛异常会让一次误操作毁掉整批，前端也拿不到"到底哪几张失败了"。
 *
 * <p>字段命名：全部 camelCase，与 {@code PolicyAdjustmentTicket} record 一致，
 * 未启用任何 Jackson 命名策略，前端可直接使用。
 */
@RestController
public class SeahorsePolicyAdjustmentTicketController {

    private static final int MAX_SAMPLE_LIMIT = 1000;
    private static final int DEFAULT_SAMPLE_LIMIT = 200;

    private final ObjectProvider<PolicyAdjustmentTicketManagementInboundPort> ticketPortProvider;

    @Autowired
    public SeahorsePolicyAdjustmentTicketController(
            ObjectProvider<PolicyAdjustmentTicketManagementInboundPort> ticketPortProvider) {
        this.ticketPortProvider = ticketPortProvider;
    }

    /**
     * 工单分页。响应额外带上本页每张工单<b>最近一次回访</b>——回访结论若只在详情里可见，
     * 被判定为"建议回滚"的工单就永远没人发现。
     */
    @GetMapping("/api/policy-adjustment-tickets")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) PolicyAdjustmentTicketStatus status,
                                    @RequestParam(required = false) Long current,
                                    @RequestParam(required = false) Long size) {
        long safeCurrent = current == null ? PolicyAdjustmentTicketQuery.DEFAULT_CURRENT : current;
        long safeSize = size == null ? PolicyAdjustmentTicketQuery.DEFAULT_PAGE_SIZE : size;
        return ApiResponses.requireService(ticketPortProvider, port -> {
            PolicyAdjustmentTicketPage page = port.page(tenantId, status, safeCurrent, safeSize);
            List<String> ticketIds = page.records().stream()
                    .map(PolicyAdjustmentTicket::ticketId)
                    .toList();
            return new TicketPageResponse(page, port.latestReviews(ticketIds));
        });
    }

    @GetMapping("/api/policy-adjustment-tickets/{ticketId}")
    public ApiResponse<Object> findById(@PathVariable String ticketId) {
        return ApiResponses.requireService(ticketPortProvider, port -> port.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy adjustment ticket not found")));
    }

    /**
     * 把一次闭环报告的建议转成待审批工单。指纹相同的建议不会重复开工单。
     */
    @PostMapping("/api/policy-adjustment-tickets/from-report")
    public ApiResponse<Object> submitProposals(@RequestBody(required = false) SubmitProposalsRequest request) {
        SubmitProposalsRequest safe = request == null ? new SubmitProposalsRequest(null, null, null) : request;
        return ApiResponses.requireService(ticketPortProvider, port -> port.submitProposals(
                safe.report(), safe.tenantId(), safe.createdBy()));
    }

    /**
     * 跑一次自学习闭环，把产出的建议转成待审批工单。
     *
     * <p>这是页面"生成工单"按钮背后的接口：闭环此前只能被代码调用，没有入口，
     * 前端只能看到空列表。取样上限默认 200，最大 1000。
     */
    @PostMapping("/api/policy-adjustment-tickets/generate")
    public ApiResponse<Object> generate(@RequestBody(required = false) GenerateRequest request) {
        GenerateRequest safe = request == null ? new GenerateRequest(null, null, null, null, null) : request;
        int limit = safe.sampleLimit() == null ? DEFAULT_SAMPLE_LIMIT
                : Math.min(Math.max(safe.sampleLimit(), 1), MAX_SAMPLE_LIMIT);
        // 传了这对通过率就顺带回访已落地工单；没传则只生成工单，回访交由定时任务或手动触发。
        ReviewMeasurement measurement = new ReviewMeasurement(
                safe.baselinePassRate(), safe.currentPassRate(), "loop-run");
        return ApiResponses.requireService(ticketPortProvider,
                port -> port.runAndGenerateTickets(safe.tenantId(), safe.createdBy(), limit, measurement));
    }

    /**
     * 手动触发一次回访：对所有处于 {@code APPLIED} 的工单记录"上一次改动到底有没有用"。
     *
     * <p>幂等：同一 {@code batchId} 重复提交不会产生第二条记录（数据库唯一约束兜底）；
     * 距上次回访不足冷却期的工单会被跳过，跳过量在 {@code skipped} 里返回。
     *
     * @param request 基线/当前评测通过率与可选的批次 id；缺任一通过率则不写记录
     */
    @PostMapping("/api/policy-adjustment-tickets/reviews/run")
    public ApiResponse<Object> runReviews(@RequestBody(required = false) ReviewRunRequest request) {
        ReviewRunRequest safe = request == null ? new ReviewRunRequest(null, null, null, null) : request;
        ReviewMeasurement measurement = new ReviewMeasurement(
                safe.baselinePassRate(), safe.currentPassRate(),
                safe.source() == null ? "manual" : safe.source());
        return ApiResponses.requireService(ticketPortProvider,
                port -> port.reviewAppliedTickets(safe.tenantId(), safe.batchId(), measurement));
    }

    /** 某张工单的回访历史，供详情抽屉展示。 */
    @GetMapping("/api/policy-adjustment-tickets/{ticketId}/reviews")
    public ApiResponse<Object> listReviews(@PathVariable String ticketId,
                                           @RequestParam(required = false) Integer limit) {
        return ApiResponses.requireService(ticketPortProvider,
                port -> port.listReviews(ticketId, limit == null ? 20 : limit));
    }

    /**
     * 批量通过。逐条执行，部分失败时仍返回 200，失败明细在 {@code failed} 里。
     */
    @PostMapping("/api/policy-adjustment-tickets/batch-approve")
    public ApiResponse<Object> batchApprove(@RequestBody(required = false) BatchDecisionRequest request) {
        return decideBatch(PolicyAdjustmentTicketStatus.APPROVED, request);
    }

    /**
     * 批量驳回。驳回必须填写意见，缺意见时整批拒绝并返回 400——
     * 否则会生成一批"被驳回但没有理由"的工单，等于把问题推回给提建议的人。
     */
    @PostMapping("/api/policy-adjustment-tickets/batch-reject")
    public ApiResponse<Object> batchReject(@RequestBody(required = false) BatchDecisionRequest request) {
        return decideBatch(PolicyAdjustmentTicketStatus.REJECTED, request);
    }

    private ApiResponse<Object> decideBatch(PolicyAdjustmentTicketStatus target,
                                            BatchDecisionRequest request) {
        BatchDecisionRequest safe = request == null
                ? new BatchDecisionRequest(null, null, null) : request;
        return ApiResponses.requireService(ticketPortProvider, port -> port.decideBatch(
                safe.ticketIds(), target, safe.operator(), safe.comment()));
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/approve")
    public ApiResponse<Object> approve(@PathVariable String ticketId,
                                       @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.APPROVED, request);
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/reject")
    public ApiResponse<Object> reject(@PathVariable String ticketId,
                                      @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.REJECTED, request);
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/apply")
    public ApiResponse<Object> apply(@PathVariable String ticketId,
                                     @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.APPLIED, request);
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/roll-back")
    public ApiResponse<Object> rollBack(@PathVariable String ticketId,
                                        @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.ROLLED_BACK, request);
    }

    private ApiResponse<Object> decide(String ticketId,
                                       PolicyAdjustmentTicketStatus target,
                                       DecisionRequest request) {
        DecisionRequest safe = request == null ? new DecisionRequest(null, null) : request;
        PolicyAdjustmentTicketDecision decision =
                new PolicyAdjustmentTicketDecision(ticketId, target, safe.operator(), safe.comment());
        try {
            return ApiResponses.requireService(ticketPortProvider, port -> port.decide(decision));
        } catch (PolicyAdjustmentTicketNotFoundException ex) {
            // 内核不知道 HTTP，"找不到"要在这里翻译成 404，否则会变成 400 让前端误以为参数错了
            throw new ResourceNotFoundException(ex.getMessage());
        }
    }

    public record DecisionRequest(String operator, String comment) {
    }

    public record SubmitProposalsRequest(LearningLoopReport report, String tenantId, String createdBy) {
    }

    /**
     * @param baselinePassRate 可选；与 currentPassRate 同时给出时，跑闭环之外还会顺带回访已落地工单
     * @param currentPassRate  可选
     */
    public record GenerateRequest(String tenantId,
                                  String createdBy,
                                  Integer sampleLimit,
                                  Double baselinePassRate,
                                  Double currentPassRate) {

        public GenerateRequest(String tenantId, String createdBy, Integer sampleLimit) {
            this(tenantId, createdBy, sampleLimit, null, null);
        }
    }

    /**
     * @param batchId 回访批次；不传则服务端现生成一个（此时同一批次重复提交无法去重，
     *                只能靠冷却期兜底）。定时任务务必传按时间分桶的稳定值。
     */
    public record ReviewRunRequest(String tenantId,
                                   String batchId,
                                   Double baselinePassRate,
                                   Double currentPassRate,
                                   String source) {

        public ReviewRunRequest(String tenantId, String batchId, Double baselinePassRate, Double currentPassRate) {
            this(tenantId, batchId, baselinePassRate, currentPassRate, null);
        }
    }

    public record BatchDecisionRequest(List<String> ticketIds, String operator, String comment) {
    }

    /**
     * 列表页响应：分页数据 + 本页每张工单最近一次回访。
     *
     * @param latestReviews ticketId → 最近一次回访；从未回访过的工单不在 map 里
     */
    public record TicketPageResponse(PolicyAdjustmentTicketPage page,
                                     Map<String, PolicyAdjustmentTicketReview> latestReviews) {
    }
}
