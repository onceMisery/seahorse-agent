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

package com.miracle.ai.seahorse.agent.kernel.application.agent.readiness;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessGenerateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.EnterprisePilotReadinessRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessAgentDefinitionEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessAuditEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessEvalEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessQuotaEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessResourceAclEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessRollbackEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessToolRiskEvidencePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelEnterprisePilotReadinessServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldGenerateCompleteReadinessReportFromSmallEvidencePorts() {
        MemoryReadinessRepository repository = new MemoryReadinessRepository();
        KernelEnterprisePilotReadinessService service = new KernelEnterprisePilotReadinessService(
                repository,
                (tenantId, agentId, versionId, checkedAt) -> List.of(
                        pass(EnterprisePilotReadinessCheckCode.OWNER),
                        pass(EnterprisePilotReadinessCheckCode.PUBLISHED_VERSION),
                        pass(EnterprisePilotReadinessCheckCode.DISABLE_SWITCH)),
                single(EnterprisePilotReadinessCheckCode.TOOL_RISK, EnterprisePilotReadinessStatus.PASS),
                single(EnterprisePilotReadinessCheckCode.RESOURCE_ACL, EnterprisePilotReadinessStatus.PASS),
                single(EnterprisePilotReadinessCheckCode.EVAL, EnterprisePilotReadinessStatus.FAIL),
                single(EnterprisePilotReadinessCheckCode.QUOTA, EnterprisePilotReadinessStatus.WARN),
                single(EnterprisePilotReadinessCheckCode.AUDIT, EnterprisePilotReadinessStatus.PASS),
                single(EnterprisePilotReadinessCheckCode.ROLLBACK, EnterprisePilotReadinessStatus.PASS),
                CLOCK);

        EnterprisePilotReadinessReport report = service.generate(new EnterprisePilotReadinessGenerateCommand(
                "tenant-1",
                "agent-1",
                "version-1",
                "operator-1"));

        assertEquals(EnterprisePilotReadinessStatus.FAIL, report.status());
        assertEquals(9, report.checkResults().size());
        assertTrue(EnterprisePilotReadinessCheckCode.all().stream()
                .allMatch(code -> report.result(code).isPresent()));
        assertEquals(EnterprisePilotReadinessStatus.WARN,
                report.result(EnterprisePilotReadinessCheckCode.QUOTA).orElseThrow().status());
        assertEquals(report, repository.saved);
        assertEquals(Optional.of(report), service.latest("tenant-1", "agent-1", "version-1"));
    }

    private static ReadinessToolRiskEvidencePort single(EnterprisePilotReadinessCheckCode code,
                                                        EnterprisePilotReadinessStatus status) {
        return (tenantId, agentId, versionId, checkedAt) -> result(code, status, checkedAt);
    }

    private static EnterprisePilotReadinessCheckResult pass(EnterprisePilotReadinessCheckCode code) {
        return result(code, EnterprisePilotReadinessStatus.PASS, NOW);
    }

    private static EnterprisePilotReadinessCheckResult result(EnterprisePilotReadinessCheckCode code,
                                                              EnterprisePilotReadinessStatus status,
                                                              Instant checkedAt) {
        return new EnterprisePilotReadinessCheckResult(
                code,
                status,
                status == EnterprisePilotReadinessStatus.PASS
                        ? EnterprisePilotReadinessReasonCode.READY
                        : EnterprisePilotReadinessReasonCode.EVAL_FAILED,
                "evidence:" + code.name(),
                code.name(),
                checkedAt);
    }

    private static final class MemoryReadinessRepository implements EnterprisePilotReadinessRepositoryPort {

        private EnterprisePilotReadinessReport saved;

        @Override
        public EnterprisePilotReadinessReport save(EnterprisePilotReadinessReport report) {
            saved = report;
            return report;
        }

        @Override
        public Optional<EnterprisePilotReadinessReport> findLatest(String tenantId,
                                                                   String agentId,
                                                                   String versionId) {
            return Optional.ofNullable(saved)
                    .filter(report -> report.tenantId().equals(tenantId))
                    .filter(report -> report.agentId().equals(agentId))
                    .filter(report -> report.versionId().equals(versionId));
        }
    }
}
