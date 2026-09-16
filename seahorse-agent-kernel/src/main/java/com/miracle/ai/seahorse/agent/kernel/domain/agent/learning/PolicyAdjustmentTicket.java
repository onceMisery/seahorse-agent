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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.learning;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 一条策略调整建议的"落地工单"。
 *
 * <p>{@link PolicyAdjustmentProposal} 是闭环每轮算出来的内存对象，没有身份、没有持久化、
 * 也没有审批能力——它能进看板，但不能被追责、不能被回访。工单补上这三件事：
 * 一个稳定 id、一份可查的审批记录、以及一个能被下一轮闭环回访的终态。
 *
 * <p>工单刻意<b>不携带执行器</b>。它记录"谁在什么时候决定把哪个键往哪调"，
 * 由人在配置侧改完后再点 APPLIED；服务不读也不写 {@code MemoryPolicyConfig}。
 *
 * @param fingerprint 建议指纹，用于去重——同一个建议不应在每轮闭环里重复开工单
 */
public record PolicyAdjustmentTicket(String ticketId,
                                     String tenantId,
                                     String targetKey,
                                     String policyKey,
                                     PolicyAdjustmentProposal.Direction direction,
                                     double suggestedDelta,
                                     double disapprovalRate,
                                     int sampleCount,
                                     String rationale,
                                     List<String> evidenceIds,
                                     String fingerprint,
                                     PolicyAdjustmentTicketStatus status,
                                     String createdBy,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     String decidedBy,
                                     Instant decidedAt,
                                     String decisionComment,
                                     String appliedBy,
                                     Instant appliedAt,
                                     String rollbackComment) {

    public PolicyAdjustmentTicket {
        ticketId = Objects.requireNonNullElse(ticketId, "").trim();
        tenantId = Objects.requireNonNullElse(tenantId, "").trim();
        targetKey = Objects.requireNonNullElse(targetKey, "").trim();
        policyKey = Objects.requireNonNullElse(policyKey, "").trim();
        direction = Objects.requireNonNull(direction, "direction must not be null");
        rationale = Objects.requireNonNullElse(rationale, "");
        evidenceIds = List.copyOf(Objects.requireNonNullElse(evidenceIds, List.of()));
        fingerprint = Objects.requireNonNullElse(fingerprint, "").trim();
        status = Objects.requireNonNullElse(status, PolicyAdjustmentTicketStatus.PENDING);
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        updatedAt = updatedAt != null ? updatedAt : createdAt;
    }

    /**
     * 把一条建议转成待审批工单。
     *
     * @param fingerprint 由调用方（应用服务）按"目标 + 配置键 + 方向"算出，保证跨轮次稳定
     */
    public static PolicyAdjustmentTicket pending(String ticketId,
                                                 String tenantId,
                                                 PolicyAdjustmentProposal proposal,
                                                 String fingerprint,
                                                 String createdBy,
                                                 Instant now) {
        Objects.requireNonNull(proposal, "proposal must not be null");
        return new PolicyAdjustmentTicket(
                ticketId,
                tenantId,
                proposal.targetKey(),
                proposal.policyKey(),
                proposal.direction(),
                proposal.suggestedDelta(),
                proposal.disapprovalRate(),
                proposal.sampleCount(),
                proposal.rationale(),
                proposal.evidenceIds(),
                fingerprint,
                PolicyAdjustmentTicketStatus.PENDING,
                createdBy,
                now,
                now,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 建议指纹。只取"改哪个键、往哪改"这两个决定身份的维度——
     * 不认可率与样本量每轮都在变，进了指纹就永远去重不掉。
     */
    public static String fingerprintOf(PolicyAdjustmentProposal proposal) {
        return proposal.direction().name() + "|" + proposal.policyKey() + "|" + proposal.targetKey();
    }

    public PolicyAdjustmentTicket withStatus(PolicyAdjustmentTicketStatus target,
                                             String operator,
                                             String comment,
                                             Instant now) {
        Objects.requireNonNull(target, "target status must not be null");
        return switch (target) {
            case APPROVED, REJECTED -> new PolicyAdjustmentTicket(
                    ticketId, tenantId, targetKey, policyKey, direction, suggestedDelta, disapprovalRate,
                    sampleCount, rationale, evidenceIds, fingerprint, target,
                    createdBy, createdAt, now, operator, now, comment, null, null, null);
            case APPLIED -> new PolicyAdjustmentTicket(
                    ticketId, tenantId, targetKey, policyKey, direction, suggestedDelta, disapprovalRate,
                    sampleCount, rationale, evidenceIds, fingerprint, target,
                    createdBy, createdAt, now, decidedBy, decidedAt, decisionComment, operator, now, null);
            case ROLLED_BACK -> new PolicyAdjustmentTicket(
                    ticketId, tenantId, targetKey, policyKey, direction, suggestedDelta, disapprovalRate,
                    sampleCount, rationale, evidenceIds, fingerprint, target,
                    createdBy, createdAt, now, decidedBy, decidedAt, decisionComment,
                    appliedBy, appliedAt, comment);
            case PENDING -> new PolicyAdjustmentTicket(
                    ticketId, tenantId, targetKey, policyKey, direction, suggestedDelta, disapprovalRate,
                    sampleCount, rationale, evidenceIds, fingerprint, target,
                    createdBy, createdAt, now, null, null, null, null, null, null);
        };
    }

    /** 运营看板用的一行摘要。 */
    public String headline() {
        return String.format(Locale.ROOT, "[%s] %s %s %+.1f%%（%d 条样本）",
                status.name(), policyKey, direction.name(), suggestedDelta * 100, sampleCount);
    }
}
