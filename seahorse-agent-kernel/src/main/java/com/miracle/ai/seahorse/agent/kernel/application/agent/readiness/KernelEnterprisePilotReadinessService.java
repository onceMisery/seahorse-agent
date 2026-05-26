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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessCheckResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness.EnterprisePilotReadinessReport;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessGenerateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.EnterprisePilotReadinessRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessAgentDefinitionEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessEvidencePort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class KernelEnterprisePilotReadinessService implements EnterprisePilotReadinessInboundPort {

    private static final String REPORT_ID_PREFIX = "epr_";

    private final EnterprisePilotReadinessRepositoryPort repository;
    private final ReadinessAgentDefinitionEvidencePort agentDefinitionEvidencePort;
    private final List<ReadinessEvidencePort> evidencePorts;
    private final Clock clock;

    public KernelEnterprisePilotReadinessService(EnterprisePilotReadinessRepositoryPort repository,
                                                 ReadinessAgentDefinitionEvidencePort agentDefinitionEvidencePort,
                                                 ReadinessEvidencePort toolRiskEvidencePort,
                                                 ReadinessEvidencePort resourceAclEvidencePort,
                                                 ReadinessEvidencePort evalEvidencePort,
                                                 ReadinessEvidencePort quotaEvidencePort,
                                                 ReadinessEvidencePort auditEvidencePort,
                                                 ReadinessEvidencePort rollbackEvidencePort,
                                                 Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.agentDefinitionEvidencePort = Objects.requireNonNull(agentDefinitionEvidencePort,
                "agentDefinitionEvidencePort must not be null");
        this.evidencePorts = List.of(
                Objects.requireNonNull(toolRiskEvidencePort, "toolRiskEvidencePort must not be null"),
                Objects.requireNonNull(resourceAclEvidencePort, "resourceAclEvidencePort must not be null"),
                Objects.requireNonNull(evalEvidencePort, "evalEvidencePort must not be null"),
                Objects.requireNonNull(quotaEvidencePort, "quotaEvidencePort must not be null"),
                Objects.requireNonNull(auditEvidencePort, "auditEvidencePort must not be null"),
                Objects.requireNonNull(rollbackEvidencePort, "rollbackEvidencePort must not be null"));
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public EnterprisePilotReadinessReport generate(EnterprisePilotReadinessGenerateCommand command) {
        EnterprisePilotReadinessGenerateCommand safeCommand = Objects.requireNonNull(command,
                "command must not be null");
        Instant now = clock.instant();
        List<EnterprisePilotReadinessCheckResult> results = new ArrayList<>();
        results.addAll(agentDefinitionEvidencePort.collect(
                safeCommand.tenantId(),
                safeCommand.agentId(),
                safeCommand.versionId(),
                now));
        for (ReadinessEvidencePort evidencePort : evidencePorts) {
            results.add(evidencePort.collect(
                    safeCommand.tenantId(),
                    safeCommand.agentId(),
                    safeCommand.versionId(),
                    now));
        }
        EnterprisePilotReadinessReport report = new EnterprisePilotReadinessReport(
                reportId(),
                safeCommand.tenantId(),
                safeCommand.agentId(),
                safeCommand.versionId(),
                null,
                results,
                now);
        return repository.save(report);
    }

    @Override
    public Optional<EnterprisePilotReadinessReport> latest(String tenantId, String agentId, String versionId) {
        return repository.findLatest(tenantId, agentId, versionId);
    }

    private String reportId() {
        return REPORT_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
