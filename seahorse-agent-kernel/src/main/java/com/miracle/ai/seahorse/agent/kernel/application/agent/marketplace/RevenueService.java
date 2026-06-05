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

package com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.RevenueShare;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.RevenueShareRepositoryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Revenue share application service for the agent marketplace.
 *
 * <p>Handles revenue calculation, monthly settlement, and creator earnings queries.
 * Platform rate defaults to 20%, creator rate to 80%.
 */
public class RevenueService {

    private final RevenueShareRepositoryPort revenueShareRepository;

    public RevenueService(RevenueShareRepositoryPort revenueShareRepository) {
        this.revenueShareRepository = Objects.requireNonNull(revenueShareRepository,
                "revenueShareRepository must not be null");
    }

    /**
     * Calculates and records revenue share for an agent in a given period.
     *
     * <p>If a revenue share record already exists for the agent+period, the gross revenue
     * is accumulated; otherwise a new record is created.
     *
     * @param agentId       the agent identifier
     * @param period        the billing period in yyyy-MM format
     * @param grossAmount   the gross revenue amount to add
     * @param creatorUserId the creator user ID who owns the agent
     * @return the created or updated revenue share
     */
    public RevenueShare calculateRevenue(String agentId, String period,
                                          BigDecimal grossAmount, Long creatorUserId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period must not be blank");
        }
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("grossAmount must not be null or negative");
        }
        if (creatorUserId == null) {
            throw new IllegalArgumentException("creatorUserId must not be null");
        }

        String tenantId = TenantContext.get();
        BigDecimal platformRate = RevenueShare.DEFAULT_PLATFORM_RATE;

        Optional<RevenueShare> existing = revenueShareRepository.findByAgentIdAndPeriod(agentId, period);
        if (existing.isPresent()) {
            RevenueShare current = existing.get();
            BigDecimal newGross = current.grossRevenue().add(grossAmount);
            BigDecimal[] shares = RevenueShare.computeShares(newGross, platformRate);
            RevenueShare updated = new RevenueShare(
                    current.id(), tenantId, agentId, creatorUserId, period,
                    newGross, shares[0], shares[1], platformRate,
                    current.status(), current.settledAt(), current.paidAt(), current.createdAt()
            );
            return revenueShareRepository.save(updated);
        }

        BigDecimal[] shares = RevenueShare.computeShares(grossAmount, platformRate);
        RevenueShare newShare = new RevenueShare(
                null, tenantId, agentId, creatorUserId, period,
                grossAmount, shares[0], shares[1], platformRate,
                RevenueShare.STATUS_PENDING, null, null, Instant.now()
        );
        return revenueShareRepository.save(newShare);
    }

    /**
     * Settles all PENDING revenue shares for a given period by marking them as SETTLED.
     *
     * @param period the billing period in yyyy-MM format
     * @return the number of shares settled
     */
    public int settleMonth(String period) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period must not be blank");
        }

        List<RevenueShare> pendingShares = revenueShareRepository.findPendingShares(period);
        int settled = 0;
        for (RevenueShare share : pendingShares) {
            revenueShareRepository.updateStatus(share.id(), RevenueShare.STATUS_SETTLED);
            settled++;
        }
        return settled;
    }

    /**
     * Returns the creator's earnings, optionally filtered by status.
     *
     * @param creatorUserId the creator user ID
     * @param status        optional status filter (null for all)
     * @return list of revenue shares for the creator
     */
    public List<RevenueShare> getCreatorEarnings(Long creatorUserId, String status) {
        if (creatorUserId == null) {
            throw new IllegalArgumentException("creatorUserId must not be null");
        }
        return revenueShareRepository.findByCreatorUserId(creatorUserId, status);
    }
}
