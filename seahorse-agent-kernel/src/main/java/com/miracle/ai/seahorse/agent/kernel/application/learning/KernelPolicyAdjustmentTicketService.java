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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketNotFoundException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    private final PolicyAdjustmentTicketRepositoryPort repository;
    private final PolicyAdjustmentTicketQueryPort query;
    private final KernelLearningLoopRunner loopRunner;
    private final Clock clock;

    /**
     * 只管工单、不跑闭环的构造。闭环取样端口未接入时使用——
     * 此时 {@link #runAndGenerateTickets} 返回空报告，其余能力不受影响。
     */
    public KernelPolicyAdjustmentTicketService(PolicyAdjustmentTicketRepositoryPort repository,
                                               PolicyAdjustmentTicketQueryPort query,
                                               Clock clock) {
        this(repository, query, clock, null);
    }

    public KernelPolicyAdjustmentTicketService(PolicyAdjustmentTicketRepositoryPort repository,
                                               PolicyAdjustmentTicketQueryPort query,
                                               Clock clock,
                                               KernelLearningLoopRunner loopRunner) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.loopRunner = loopRunner;
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
}
