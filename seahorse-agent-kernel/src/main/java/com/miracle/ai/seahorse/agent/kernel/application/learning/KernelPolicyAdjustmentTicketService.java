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

package com.miracle.ai.seahorse.agent.kernel.application.learning;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketBatchDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketNotFoundException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReviewRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PreviousProposalOutcome;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketReviewRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 把闭环的建议转成可追责、可审批、可回访的落地工单。
 *
 * <p>proposal → ticket 的转换放在应用服务而不是领域对象里，是为了让
 * {@link PolicyAdjustmentTicket} 保持纯粹的数据载体：它不需要知道工单 id 怎么生成、
 * 也不需要知道"这条建议是否已经开过工单"。仓储只在这里被依赖，domain 不碰 IO。
 *
 * <p><b>本服务不读也不写 {@code MemoryPolicyConfig}。</b>APPLIED 表示"人已在配置侧改完
 * 并确认"，不是"系统自动改了"。让一个靠几天延迟的反馈信号驱动的闭环直接改生产配置，
 * 结果只会是阈值震荡。
 */
public class KernelPolicyAdjustmentTicketService implements PolicyAdjustmentTicketManagementInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelPolicyAdjustmentTicketService.class);
    private static final String TICKET_ID_PREFIX = "pat-";
    private static final String REVIEW_ID_PREFIX = "par-";
    private static final String BATCH_ID_PREFIX = "lr-";
    private static final int REVIEW_SCAN_LIMIT = 200;
    private static final int MAX_BATCH_SIZE = 100;

    private final PolicyAdjustmentTicketRepositoryPort repository;
    private final PolicyAdjustmentTicketQueryPort query;
    private final KernelLearningLoopRunner loopRunner;
    private final PolicyAdjustmentTicketReviewRepositoryPort reviewRepository;
    private final LearningLoopPolicy policy;
    private final Clock clock;

    /**
     * 只管工单、不跑闭环的构造。闭环取样端口未接入时使用——
     * 此时 {@link #runAndGenerateTickets} 返回空报告，其余能力不受影响。
     */
    public KernelPolicyAdjustmentTicketService(PolicyAdjustmentTicketRepositoryPort repository,
                                               PolicyAdjustmentTicketQueryPort query,
                                               Clock clock) {
        this(repository, query, clock, null, null, null);
    }

    public KernelPolicyAdjustmentTicketService(PolicyAdjustmentTicketRepositoryPort repository,
                                               PolicyAdjustmentTicketQueryPort query,
                                               Clock clock,
                                               KernelLearningLoopRunner loopRunner) {
        this(repository, query, clock, loopRunner, null, null);
    }

    public KernelPolicyAdjustmentTicketService(PolicyAdjustmentTicketRepositoryPort repository,
                                               PolicyAdjustmentTicketQueryPort query,
                                               Clock clock,
                                               KernelLearningLoopRunner loopRunner,
                                               PolicyAdjustmentTicketReviewRepositoryPort reviewRepository) {
        this(repository, query, clock, loopRunner, reviewRepository, null);
    }

    public KernelPolicyAdjustmentTicketService(PolicyAdjustmentTicketRepositoryPort repository,
                                               PolicyAdjustmentTicketQueryPort query,
                                               Clock clock,
                                               KernelLearningLoopRunner loopRunner,
                                               PolicyAdjustmentTicketReviewRepositoryPort reviewRepository,
                                               LearningLoopPolicy policy) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.loopRunner = loopRunner;
        this.reviewRepository = reviewRepository;
        this.policy = Objects.requireNonNullElseGet(policy, LearningLoopPolicy::defaults);
    }

    @Override
    public LearningLoopRunResult runAndGenerateTickets(String tenantId, String createdBy, int sampleLimit) {
        String safeTenant = Objects.requireNonNullElse(tenantId, "").trim();
        if (loopRunner == null || !loopRunner.configured()) {
            return new LearningLoopRunResult(
                    new LearningLoopReport(null, List.of(), List.of(), null,
                            "闭环数据源未配置：未接入反馈或复核查询端口"),
                    List.of());
        }
        LearningLoopReport report = loopRunner.analyze(safeTenant, sampleLimit);
        return new LearningLoopRunResult(report, submitProposals(report, safeTenant, createdBy));
    }

    /**
     * 跑闭环并顺带回访已落地工单——这是<b>事件驱动</b>的那条触发路径，也是默认生效的那条。
     *
     * <p>为什么默认走这条而不是定时任务：回访的意义是"在产生新建议的同一时刻，
     * 回头看上一次改动的效果"。它天然与"新跑了一次闭环"同频；定时任务是给
     * "很久没人点运行闭环"的兜底，不是主路径。
     */
    @Override
    public LearningLoopRunResult runAndGenerateTickets(String tenantId,
                                                       String createdBy,
                                                       int sampleLimit,
                                                       ReviewMeasurement measurement) {
        LearningLoopRunResult result = runAndGenerateTickets(tenantId, createdBy, sampleLimit);
        PolicyAdjustmentTicketReviewRunResult review =
                reviewAppliedTickets(tenantId, nextBatchId(), measurement);
        if (!review.reviews().isEmpty()) {
            LOG.info("Learning loop reviewed {} applied ticket(s), rollbackRecommended={}",
                    review.reviews().size(), review.hasRollbackRecommendation());
        }
        return new LearningLoopRunResult(result.report(), result.tickets(), review);
    }

    @Override
    public PolicyAdjustmentTicketReviewRunResult reviewAppliedTickets(String tenantId,
                                                                      String batchId,
                                                                      ReviewMeasurement measurement) {
        String safeTenant = Objects.requireNonNullElse(tenantId, "").trim();
        String safeBatch = isBlank(batchId) ? nextBatchId() : batchId.trim();
        if (reviewRepository == null) {
            return PolicyAdjustmentTicketReviewRunResult.empty("回访记录仓储未装配，本次未写回访记录");
        }
        if (measurement == null || !measurement.measurable()) {
            // 不可测量就不落库：否则每一轮都会多出一条内容完全相同的 AWAITING_MEASUREMENT，
            // 既没有信息量，又会吃掉冷却期让真正可判定的那次回访被跳过。
            return PolicyAdjustmentTicketReviewRunResult.empty(
                    "缺少可比的评测通过率（基线或当前值为空），本次未写回访记录");
        }
        PolicyAdjustmentTicketPage applied = query.page(new PolicyAdjustmentTicketQuery(
                safeTenant, PolicyAdjustmentTicketStatus.APPLIED, 1L, REVIEW_SCAN_LIMIT));
        List<PolicyAdjustmentTicket> tickets = applied == null || applied.records() == null
                ? List.of()
                : applied.records();
        if (tickets.isEmpty()) {
            return PolicyAdjustmentTicketReviewRunResult.empty("当前没有处于已落地状态的工单");
        }
        List<String> ticketIds = tickets.stream().map(PolicyAdjustmentTicket::ticketId).toList();
        Map<String, PolicyAdjustmentTicketReview> latest = safeLatest(ticketIds);

        Instant now = clock.instant();
        List<PolicyAdjustmentTicketReview> written = new ArrayList<>();
        int skipped = 0;
        for (PolicyAdjustmentTicket ticket : tickets) {
            PolicyAdjustmentTicketReview previous = latest.get(ticket.ticketId());
            if (previous != null && previous.reviewedAt().plus(policy.reviewCooldown()).isAfter(now)) {
                skipped++;
                continue;
            }
            PreviousProposalOutcome outcome = PreviousProposalOutcome.of(
                    measurement.delta(), policy.evalImprovementThreshold());
            PolicyAdjustmentTicketReview review = PolicyAdjustmentTicketReview.of(
                    nextReviewId(), ticket, safeBatch, measurement, outcome, now);
            reviewRepository.save(review);
            written.add(review);
        }
        String note = String.format(Locale.ROOT,
                "扫描 %d 张已落地工单，写入 %d 条回访记录，跳过 %d 张（冷却期 %s 内）",
                tickets.size(), written.size(), skipped, policy.reviewCooldown());
        return new PolicyAdjustmentTicketReviewRunResult(written, tickets.size(), skipped, note);
    }

    @Override
    public List<PolicyAdjustmentTicketReview> listReviews(String ticketId, int limit) {
        if (reviewRepository == null || isBlank(ticketId)) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<PolicyAdjustmentTicketReview> reviews =
                reviewRepository.listByTicket(ticketId.trim(), safeLimit);
        return reviews == null ? List.of() : reviews;
    }

    @Override
    public Map<String, PolicyAdjustmentTicketReview> latestReviews(List<String> ticketIds) {
        if (reviewRepository == null || ticketIds == null || ticketIds.isEmpty()) {
            return Map.of();
        }
        return safeLatest(ticketIds);
    }

    /**
     * 批量审批：逐条执行，成功与失败分别收集。
     *
     * <p>刻意不做整批事务。工单在"前端勾选"与"后端执行"之间可能已被别人审批掉，
     * 整批回滚会让前面成功的白做；而整批失败（抛异常）则会让一次误操作毁掉整批，
     * 并且前端拿不到"到底是哪几张失败了"。
     */
    @Override
    public PolicyAdjustmentTicketBatchDecisionResult decideBatch(List<String> ticketIds,
                                                                 PolicyAdjustmentTicketStatus target,
                                                                 String operator,
                                                                 String comment) {
        Objects.requireNonNull(target, "target status must not be null");
        if (target != PolicyAdjustmentTicketStatus.APPROVED
                && target != PolicyAdjustmentTicketStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "批量审批只支持通过或驳回；标记落地与回滚需要逐张确认实际配置变更：" + target);
        }
        if (ticketIds == null || ticketIds.isEmpty()) {
            throw new IllegalArgumentException("ticketIds 不能为空");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String id : ticketIds) {
            if (!isBlank(id)) {
                unique.add(id.trim());
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("ticketIds 不能为空");
        }
        if (unique.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次批量审批不得超过 " + MAX_BATCH_SIZE + " 张，实际 " + unique.size());
        }
        List<String> succeeded = new ArrayList<>();
        List<PolicyAdjustmentTicketBatchDecisionResult.Failure> failed = new ArrayList<>();
        for (String ticketId : unique) {
            try {
                decide(new PolicyAdjustmentTicketDecision(ticketId, target, operator, comment));
                succeeded.add(ticketId);
            } catch (RuntimeException ex) {
                failed.add(new PolicyAdjustmentTicketBatchDecisionResult.Failure(
                        ticketId, friendlyReason(ex)));
            }
        }
        LOG.info("Batch decision {} finished: succeeded={} failed={}",
                target, succeeded.size(), failed.size());
        return new PolicyAdjustmentTicketBatchDecisionResult(target, succeeded, failed);
    }

    private Map<String, PolicyAdjustmentTicketReview> safeLatest(List<String> ticketIds) {
        try {
            Map<String, PolicyAdjustmentTicketReview> latest = reviewRepository.latestByTickets(ticketIds);
            return latest == null ? Map.of() : latest;
        } catch (RuntimeException ex) {
            // 取不到历史就当作"从未回访"，最多是多写一条；反过来若在这里抛异常，
            // 会让整批回访因为一个只读查询失败而全部作废。
            LOG.warn("Failed to load latest ticket reviews; treating all as never reviewed", ex);
            return Map.of();
        }
    }

    /** 异常信息直接展示给运营，只保留可安全外传的语义。 */
    private static String friendlyReason(RuntimeException ex) {
        if (ex instanceof PolicyAdjustmentTicketNotFoundException) {
            return "工单不存在";
        }
        if (ex instanceof IllegalStateException) {
            return "工单状态已被其他审批人变更，请刷新后重试";
        }
        if (ex instanceof IllegalArgumentException) {
            String message = ex.getMessage();
            return isBlank(message) ? "参数不合法" : message;
        }
        return "审批失败，请稍后重试";
    }

    @Override
    public List<PolicyAdjustmentTicket> submitProposals(LearningLoopReport report,
                                                        String tenantId,
                                                        String createdBy) {
        if (report == null || !report.hasProposals()) {
            return List.of();
        }
        String safeTenant = Objects.requireNonNullElse(tenantId, "").trim();
        // 同一批报告里也可能出现指纹相同的建议（同一目标、同一配置键、同一方向）
        List<PolicyAdjustmentProposal> unique = new ArrayList<>();
        Set<String> batchSeen = new LinkedHashSet<>();
        for (PolicyAdjustmentProposal proposal : report.proposals()) {
            if (proposal != null && batchSeen.add(PolicyAdjustmentTicket.fingerprintOf(proposal))) {
                unique.add(proposal);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        List<String> fingerprints = unique.stream()
                .map(PolicyAdjustmentTicket::fingerprintOf)
                .toList();
        Set<String> existing = query.existingFingerprints(safeTenant, fingerprints);

        Instant now = clock.instant();
        List<PolicyAdjustmentTicket> created = new ArrayList<>();
        for (PolicyAdjustmentProposal proposal : unique) {
            String fingerprint = PolicyAdjustmentTicket.fingerprintOf(proposal);
            if (existing.contains(fingerprint)) {
                continue;
            }
            PolicyAdjustmentTicket ticket = PolicyAdjustmentTicket.pending(
                    nextTicketId(), safeTenant, proposal, fingerprint, createdBy, now);
            repository.save(ticket);
            created.add(ticket);
        }
        if (!created.isEmpty()) {
            LOG.info("Created {} policy adjustment ticket(s) for tenant={} from a learning loop report",
                    created.size(), safeTenant);
        }
        return List.copyOf(created);
    }

    @Override
    public PolicyAdjustmentTicketPage page(String tenantId,
                                           PolicyAdjustmentTicketStatus status,
                                           long current,
                                           long size) {
        return query.page(new PolicyAdjustmentTicketQuery(
                Objects.requireNonNullElse(tenantId, "").trim(), status, safeCurrent(current), safeSize(size)));
    }

    @Override
    public Optional<PolicyAdjustmentTicket> findById(String ticketId) {
        return query.findById(Objects.requireNonNullElse(ticketId, "").trim());
    }

    @Override
    public PolicyAdjustmentTicket decide(PolicyAdjustmentTicketDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        PolicyAdjustmentTicket current = query.findById(decision.ticketId())
                .orElseThrow(() -> new PolicyAdjustmentTicketNotFoundException(decision.ticketId()));
        PolicyAdjustmentTicketStatus target = decision.targetStatus();
        if (!current.status().canTransitionTo(target)) {
            throw new IllegalArgumentException("illegal ticket transition: " + current.status()
                    + " -> " + target + " (ticketId=" + decision.ticketId() + ")");
        }
        if (target != PolicyAdjustmentTicketStatus.ROLLED_BACK && decision.comment().isBlank()
                && (target == PolicyAdjustmentTicketStatus.REJECTED
                    || target == PolicyAdjustmentTicketStatus.APPLIED)) {
            throw new IllegalArgumentException(
                    "decision comment is required for " + target + " (ticketId=" + decision.ticketId() + ")");
        }
        PolicyAdjustmentTicket updated = current.withStatus(
                target, decision.operator(), decision.comment(), clock.instant());
        if (!repository.updateIfStatus(decision.ticketId(), current.status(), updated)) {
            throw new IllegalStateException(
                    "ticket status changed by another reviewer, please reload: " + decision.ticketId());
        }
        LOG.info("Policy adjustment ticket {} moved {} -> {} by {}",
                decision.ticketId(), current.status(), target, decision.operator());
        return updated;
    }

    private static long safeCurrent(long current) {
        return current < 1L ? PolicyAdjustmentTicketQuery.DEFAULT_CURRENT : current;
    }

    private static long safeSize(long size) {
        return size < 1L ? PolicyAdjustmentTicketQuery.DEFAULT_PAGE_SIZE : Math.min(size, 200L);
    }

    private static String nextTicketId() {
        return TICKET_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private static String nextReviewId() {
        return REVIEW_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    /**
     * 批次 id 缺省时现生成一个。这意味着不传 batchId 的调用<b>没有批次幂等</b>，
     * 只能靠冷却期兜住——定时任务必须自己传一个按时间分桶的稳定 batchId。
     */
    private static String nextBatchId() {
        return BATCH_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
