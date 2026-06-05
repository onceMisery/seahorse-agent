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

package com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.RevenueShare;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for revenue share persistence.
 */
public interface RevenueShareRepositoryPort {

    /**
     * Saves (inserts or updates) a revenue share record.
     *
     * @param share the revenue share to save
     * @return the saved revenue share
     */
    RevenueShare save(RevenueShare share);

    /**
     * Finds a revenue share by agent ID and billing period.
     *
     * @param agentId the agent identifier
     * @param period  the billing period (yyyy-MM)
     * @return the revenue share, or empty
     */
    Optional<RevenueShare> findByAgentIdAndPeriod(String agentId, String period);

    /**
     * Finds all revenue shares for a creator, optionally filtered by status.
     *
     * @param creatorUserId the creator user ID
     * @param status        optional status filter (null for all)
     * @return list of revenue shares
     */
    List<RevenueShare> findByCreatorUserId(Long creatorUserId, String status);

    /**
     * Finds all PENDING revenue shares for a given period.
     *
     * @param period the billing period (yyyy-MM)
     * @return list of pending revenue shares
     */
    List<RevenueShare> findPendingShares(String period);

    /**
     * Updates the status of a revenue share record.
     *
     * @param id     the revenue share ID
     * @param status the new status
     */
    void updateStatus(Long id, String status);
}
