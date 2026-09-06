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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UNKNOWN 工具调用对账服务（设计 §9 / ADR-003）。
 *
 * <p>UNKNOWN 只能通过对账收敛到 SUCCEEDED/FAILED。扫描超过宽限期仍处于
 * UNKNOWN 的审计记录：同租户同幂等键存在终态记录时引用其真实结局；否则按
 * "未执行"收敛为 FAILED 并保留对账证据。分布式锁保证双实例下单实例执行。
 */
public class ToolInvocationReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolInvocationReconciliationService.class);
    private static final String LOCK_KEY = "tool-invocation:reconciliation:lock";
    private static final int BATCH_SIZE = 50;
    private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMinutes(30);

    private final ToolInvocationAuditQueryPort auditQueryPort;
    private final ToolInvocationAuditPort auditPort;
    private final DistributedLockPort lockPort;
    private final Clock clock;
    private final Duration gracePeriod;

    public ToolInvocationReconciliationService(ToolInvocationAuditQueryPort auditQueryPort,
                                               ToolInvocationAuditPort auditPort,
                                               DistributedLockPort lockPort,
                                               Clock clock) {
        this(auditQueryPort, auditPort, lockPort, clock, DEFAULT_GRACE_PERIOD);
    }

    public ToolInvocationReconciliationService(ToolInvocationAuditQueryPort auditQueryPort,
                                               ToolInvocationAuditPort auditPort,
                                               DistributedLockPort lockPort,
                                               Clock clock,
                                               Duration gracePeriod) {
        this.auditQueryPort = Objects.requireNonNull(auditQueryPort, "auditQueryPort must not be null");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort must not be null");
        this.lockPort = Objects.requireNonNull(lockPort, "lockPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.gracePeriod = Objects.requireNonNullElse(gracePeriod, DEFAULT_GRACE_PERIOD);
    }

    /**
     * 执行一轮对账（由定时任务调用），返回本轮收敛的记录数。
     */
    public ToolInvocationReconciliationResult reconcile() {
        boolean locked = lockPort.tryLock(LOCK_KEY, Duration.ofSeconds(5), Duration.ofMinutes(5));
        if (!locked) {
            LOGGER.debug("Tool invocation reconciliation lock not acquired, skipping this round");
            return ToolInvocationReconciliationResult.empty();
        }
        try {
            Instant cutoff = clock.instant().minus(gracePeriod);
            List<ToolInvocationAuditEntry> unresolved = auditQueryPort.findUnresolvedUnknown(cutoff, BATCH_SIZE);
            if (unresolved.isEmpty()) {
                return ToolInvocationReconciliationResult.empty();
            }
            LOGGER.info("Reconciling {} UNKNOWN tool invocations finished before {}", unresolved.size(), cutoff);
            int fromSibling = 0;
            int resolvedAsFailed = 0;
            for (ToolInvocationAuditEntry entry : unresolved) {
                if (entry == null) {
                    continue;
                }
                if (reconcileOne(entry)) {
                    fromSibling++;
                } else {
                    resolvedAsFailed++;
                }
            }
            return new ToolInvocationReconciliationResult(fromSibling, resolvedAsFailed);
        } finally {
            lockPort.unlock(LOCK_KEY);
        }
    }

    /**
     * @return true 表示引用了同幂等键的终态结局；false 表示按未执行收敛为 FAILED。
     */
    private boolean reconcileOne(ToolInvocationAuditEntry entry) {
        Instant reconciledAt = clock.instant();
        if (entry.idempotencyKey() == null || entry.idempotencyKey().isBlank()) {
            fail(entry, "reconciliation: no idempotency evidence to correlate, resolved as not executed",
                    reconciledAt);
            return false;
        }
        Optional<ToolInvocationAuditEntry> sibling =
                auditQueryPort.findLatestTerminalByIdempotencyKey(entry.tenantId(), entry.idempotencyKey());
        if (sibling.isEmpty()) {
            fail(entry, "reconciliation: no terminal sibling observed within the grace period",
                    reconciledAt);
            return false;
        }
        ToolInvocationAuditEntry terminal = sibling.orElseThrow();
        String summary = "reconciled from terminal sibling invocation " + terminal.invocationId()
                + " (" + terminal.status() + ")";
        auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                entry.invocationId(),
                terminal.status(),
                summary,
                terminal.errorMessage(),
                reconciledAt));
        LOGGER.info("Tool invocation {} reconciled to {} via sibling {}",
                entry.invocationId(), terminal.status(), terminal.invocationId());
        return true;
    }

    private void fail(ToolInvocationAuditEntry entry, String reason, Instant reconciledAt) {
        auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                entry.invocationId(),
                ToolInvocationStatus.FAILED,
                null,
                reason,
                reconciledAt));
        LOGGER.info("Tool invocation {} reconciled to FAILED: {}", entry.invocationId(), reason);
    }

    /**
     * 单轮对账结果：引用同幂等键终态与按未执行收敛的记录数。
     */
    public record ToolInvocationReconciliationResult(int reconciledFromSibling, int resolvedAsFailed) {

        public static ToolInvocationReconciliationResult empty() {
            return new ToolInvocationReconciliationResult(0, 0);
        }

        public int total() {
            return reconciledFromSibling + resolvedAsFailed;
        }
    }
}
