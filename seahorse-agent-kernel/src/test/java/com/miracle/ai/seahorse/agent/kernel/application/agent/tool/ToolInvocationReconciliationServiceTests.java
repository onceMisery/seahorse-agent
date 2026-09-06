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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolInvocationReconciliationServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration GRACE = Duration.ofMinutes(30);

    @Test
    void shouldPropagateTerminalSiblingOutcomeToUnknownInvocation() {
        ToolInvocationAuditEntry unknown = entry("inv-unknown", "key-1", ToolInvocationStatus.UNKNOWN, NOW.minusSeconds(3600));
        ToolInvocationAuditEntry sibling = entry("inv-original", "key-1", ToolInvocationStatus.SUCCEEDED, NOW.minusSeconds(3700));
        RecordingAuditQueryPort queryPort = new RecordingAuditQueryPort(List.of(unknown));
        queryPort.terminalSiblings.put("key-1", sibling);
        RecordingAuditPort auditPort = new RecordingAuditPort();
        ToolInvocationReconciliationService service = service(queryPort, auditPort);

        ToolInvocationReconciliationService.ToolInvocationReconciliationResult result = service.reconcile();

        assertEquals(1, result.reconciledFromSibling());
        assertEquals(0, result.resolvedAsFailed());
        assertEquals(1, auditPort.completions.size());
        ToolInvocationAuditCompletion completion = auditPort.completions.get(0);
        assertEquals("inv-unknown", completion.invocationId());
        assertEquals(ToolInvocationStatus.SUCCEEDED, completion.status());
        assertTrue(completion.resultSummary().contains("inv-original"), completion.resultSummary());
    }

    @Test
    void shouldResolveUnknownAsFailedWhenNoSiblingExists() {
        ToolInvocationAuditEntry unknown = entry("inv-orphan", "key-2", ToolInvocationStatus.UNKNOWN, NOW.minusSeconds(3600));
        RecordingAuditQueryPort queryPort = new RecordingAuditQueryPort(List.of(unknown));
        RecordingAuditPort auditPort = new RecordingAuditPort();
        ToolInvocationReconciliationService service = service(queryPort, auditPort);

        ToolInvocationReconciliationService.ToolInvocationReconciliationResult result = service.reconcile();

        assertEquals(0, result.reconciledFromSibling());
        assertEquals(1, result.resolvedAsFailed());
        ToolInvocationAuditCompletion completion = auditPort.completions.get(0);
        assertEquals(ToolInvocationStatus.FAILED, completion.status());
        assertTrue(completion.errorMessage().contains("no terminal sibling"), completion.errorMessage());
    }

    @Test
    void shouldResolveUnknownAsFailedWithoutIdempotencyEvidence() {
        ToolInvocationAuditEntry unknown = entry("inv-no-key", null, ToolInvocationStatus.UNKNOWN, NOW.minusSeconds(3600));
        RecordingAuditQueryPort queryPort = new RecordingAuditQueryPort(List.of(unknown));
        RecordingAuditPort auditPort = new RecordingAuditPort();
        ToolInvocationReconciliationService service = service(queryPort, auditPort);

        ToolInvocationReconciliationService.ToolInvocationReconciliationResult result = service.reconcile();

        assertEquals(1, result.resolvedAsFailed());
        assertEquals(ToolInvocationStatus.FAILED, auditPort.completions.get(0).status());
        assertTrue(auditPort.completions.get(0).errorMessage().contains("no idempotency evidence"));
    }

    @Test
    void shouldOnlyScanUnknownsFinishedBeforeTheGraceCutoff() {
        RecordingAuditQueryPort queryPort = new RecordingAuditQueryPort(List.of());
        RecordingAuditPort auditPort = new RecordingAuditPort();
        ToolInvocationReconciliationService service = service(queryPort, auditPort);

        service.reconcile();

        assertEquals(NOW.minus(GRACE), queryPort.lastCutoff);
        assertEquals(50, queryPort.lastLimit);
    }

    @Test
    void shouldSkipScanWhenLockIsHeldByAnotherInstance() {
        RecordingAuditQueryPort queryPort = new RecordingAuditQueryPort(List.of());
        RecordingAuditPort auditPort = new RecordingAuditPort();
        ToolInvocationReconciliationService service = new ToolInvocationReconciliationService(
                queryPort, auditPort, new FakeLockPort(false), FIXED_CLOCK, GRACE);

        ToolInvocationReconciliationService.ToolInvocationReconciliationResult result = service.reconcile();

        assertEquals(0, result.total());
        assertTrue(queryPort.lastCutoff == null);
        assertTrue(auditPort.completions.isEmpty());
    }

    private ToolInvocationReconciliationService service(ToolInvocationAuditQueryPort queryPort,
                                                        ToolInvocationAuditPort auditPort) {
        return new ToolInvocationReconciliationService(queryPort, auditPort, new FakeLockPort(true), FIXED_CLOCK, GRACE);
    }

    private static ToolInvocationAuditEntry entry(String invocationId,
                                                  String idempotencyKey,
                                                  ToolInvocationStatus status,
                                                  Instant finishedAt) {
        return new ToolInvocationAuditEntry(
                invocationId,
                "run-1",
                "step-1",
                "agent-1",
                "v1",
                "rollout-1",
                "tenant-a",
                "user-1",
                "web_search",
                idempotencyKey,
                status,
                null,
                "{}",
                null,
                null,
                finishedAt.minusSeconds(60),
                finishedAt);
    }

    private static final class RecordingAuditQueryPort implements ToolInvocationAuditQueryPort {
        private final List<ToolInvocationAuditEntry> unresolvedUnknowns;
        private final Map<String, ToolInvocationAuditEntry> terminalSiblings = new HashMap<>();
        private Instant lastCutoff;
        private Integer lastLimit;

        private RecordingAuditQueryPort(List<ToolInvocationAuditEntry> unresolvedUnknowns) {
            this.unresolvedUnknowns = unresolvedUnknowns;
        }

        @Override
        public ToolInvocationAuditPage page(ToolInvocationAuditQuery query) {
            return new ToolInvocationAuditPage(List.of(), 0L, query.size(), query.current(), 0L);
        }

        @Override
        public List<ToolInvocationAuditEntry> findUnresolvedUnknown(Instant finishedBefore, int limit) {
            lastCutoff = finishedBefore;
            lastLimit = limit;
            return unresolvedUnknowns;
        }

        @Override
        public Optional<ToolInvocationAuditEntry> findLatestTerminalByIdempotencyKey(
                String tenantId, String idempotencyKey) {
            return Optional.ofNullable(terminalSiblings.get(idempotencyKey));
        }
    }

    private static final class RecordingAuditPort implements ToolInvocationAuditPort {
        private final List<ToolInvocationAuditCompletion> completions = new ArrayList<>();

        @Override
        public void recordRequested(ToolInvocationAuditRecord record) {
        }

        @Override
        public void recordDecision(ToolInvocationAuditDecision decision) {
        }

        @Override
        public void recordCompleted(ToolInvocationAuditCompletion completion) {
            completions.add(completion);
        }
    }

    private static final class FakeLockPort implements DistributedLockPort {
        private final boolean locked;

        private FakeLockPort(boolean locked) {
            this.locked = locked;
        }

        @Override
        public boolean tryLock(String lockName, Duration waitTime, Duration leaseTime) {
            return locked;
        }

        @Override
        public void unlock(String lockName) {
        }
    }
}
