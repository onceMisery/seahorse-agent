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

package com.miracle.ai.seahorse.agent.kernel.application.agent.rollout;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutActionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutRollbackCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionRollbackCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class KernelAgentRolloutService implements AgentRolloutInboundPort {

    private static final String ROLLOUT_ID_PREFIX = "avr_";

    private final AgentRolloutRepositoryPort rolloutRepository;
    private final ProductionGateRepositoryPort productionGateRepository;
    private final AgentFactoryInboundPort agentFactoryPort;
    private final Clock clock;

    public KernelAgentRolloutService(AgentRolloutRepositoryPort rolloutRepository,
                                     ProductionGateRepositoryPort productionGateRepository,
                                     AgentFactoryInboundPort agentFactoryPort,
                                     Clock clock) {
        this.rolloutRepository = Objects.requireNonNull(rolloutRepository, "rolloutRepository must not be null");
        this.productionGateRepository = Objects.requireNonNull(productionGateRepository,
                "productionGateRepository must not be null");
        this.agentFactoryPort = Objects.requireNonNull(agentFactoryPort, "agentFactoryPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public AgentVersionRollout createCanary(AgentRolloutCreateCommand command) {
        AgentRolloutCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        int canaryPercent = safeCommand.canaryPercent() == null
                ? AgentRolloutLimits.DEFAULT_CANARY_PERCENT
                : safeCommand.canaryPercent();
        AgentVersionRollout rollout = new AgentVersionRollout(
                rolloutId(),
                safeCommand.tenantId(),
                safeCommand.agentId(),
                safeCommand.versionId(),
                canaryPercent,
                AgentRolloutStatus.RUNNING,
                null,
                null,
                safeCommand.operator(),
                clock.instant(),
                clock.instant(),
                null);
        return rolloutRepository.save(rollout);
    }

    @Override
    public AgentVersionRollout pause(AgentRolloutActionCommand command) {
        AgentVersionRollout rollout = rolloutFor(command.tenantId(), command.agentId(), command.rolloutId());
        if (rollout.status().terminal()) {
            return rollout;
        }
        return rolloutRepository.save(rollout.pause(clock.instant()));
    }

    @Override
    public AgentVersionRollout promote(AgentRolloutActionCommand command) {
        AgentVersionRollout rollout = rolloutFor(command.tenantId(), command.agentId(), command.rolloutId());
        Optional<ProductionGateReport> latest = productionGateRepository.latest(rollout.agentId());
        if (latest.isEmpty()) {
            return rolloutRepository.save(rollout.fail(AgentRolloutFailureCode.GATE_MISSING, clock.instant()));
        }
        ProductionGateReport report = latest.orElseThrow();
        if (!rollout.versionId().equals(report.versionId()) || report.status() != ProductionGateStatus.PASS) {
            return rolloutRepository.save(rollout.fail(AgentRolloutFailureCode.GATE_FAILED, clock.instant()));
        }
        return rolloutRepository.save(rollout.promote(report.reportId(), clock.instant()));
    }

    @Override
    public AgentVersionRollout rollback(AgentRolloutRollbackCommand command) {
        AgentVersionRollout rollout = rolloutFor(command.tenantId(), command.agentId(), command.rolloutId());
        try {
            AgentRollbackResult result = agentFactoryPort.rollback(new AgentVersionRollbackCommand(
                    command.tenantId(),
                    command.agentId(),
                    command.targetVersionId(),
                    command.operator(),
                    AgentRollbackReasonCode.CANARY_FAILED,
                    command.comment()));
            if (result.status() == AgentRollbackStatus.ROLLED_BACK
                    || result.status() == AgentRollbackStatus.NOOP_ALREADY_ACTIVE) {
                return rolloutRepository.save(rollout.rolledBack(clock.instant()));
            }
            return rolloutRepository.save(rollout.fail(AgentRolloutFailureCode.ROLLBACK_FAILED, clock.instant()));
        } catch (IllegalArgumentException ex) {
            return rolloutRepository.save(rollout.fail(AgentRolloutFailureCode.ROLLBACK_TARGET_MISSING, clock.instant()));
        } catch (RuntimeException ex) {
            return rolloutRepository.save(rollout.fail(AgentRolloutFailureCode.ROLLBACK_FAILED, clock.instant()));
        }
    }

    @Override
    public Optional<AgentVersionRollout> latest(String tenantId, String agentId, String versionId) {
        return rolloutRepository.findLatest(tenantId, agentId, versionId);
    }

    private AgentVersionRollout rolloutFor(String tenantId, String agentId, String rolloutId) {
        AgentVersionRollout rollout = rolloutRepository.findById(rolloutId)
                .orElseThrow(() -> new IllegalArgumentException("rollout not found"));
        if (!rollout.tenantId().equals(tenantId) || !rollout.agentId().equals(agentId)) {
            throw new IllegalArgumentException("rollout scope does not match command");
        }
        return rollout;
    }

    private String rolloutId() {
        return ROLLOUT_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
