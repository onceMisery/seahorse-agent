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

package com.miracle.ai.seahorse.agent.kernel.application.trial;

import com.miracle.ai.seahorse.agent.kernel.domain.user.UserTrial;
import com.miracle.ai.seahorse.agent.ports.outbound.user.TrialRepositoryPort;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Kernel service exposing trial-query operations for other layers.
 *
 * <p>This service is read-only; trial creation is handled by
 * {@link com.miracle.ai.seahorse.agent.kernel.application.auth.KernelRegistrationService}.
 */
public class KernelTrialService {

    private final TrialRepositoryPort trialRepositoryPort;

    public KernelTrialService(TrialRepositoryPort trialRepositoryPort) {
        this.trialRepositoryPort = Objects.requireNonNull(trialRepositoryPort, "trialRepositoryPort must not be null");
    }

    /**
     * Retrieve the trial record for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return the trial, if any
     */
    public Optional<UserTrial> getTrialByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return trialRepositoryPort.findByTenantId(tenantId);
    }

    /**
     * Returns {@code true} if the tenant has an active, non-expired trial.
     *
     * @param tenantId the tenant identifier
     * @return whether the trial is currently active
     */
    public boolean isTrialActive(String tenantId) {
        return getTrialByTenantId(tenantId)
                .map(trial -> trial.isActive() && !trial.isExpired(Instant.now()))
                .orElse(false);
    }

    /**
     * Returns {@code true} if the tenant's trial has expired or does not exist.
     *
     * @param tenantId the tenant identifier
     * @return whether the trial is expired
     */
    public boolean isTrialExpired(String tenantId) {
        return getTrialByTenantId(tenantId)
                .map(trial -> trial.isExpired(Instant.now()) || !trial.isActive())
                .orElse(true);
    }
}
