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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Revenue share record for an agent creator within a billing period.
 *
 * <p>Tracks gross revenue, platform share (20%), and creator share (80%).
 * Status lifecycle: PENDING → SETTLED → PAID.
 */
public record RevenueShare(
        Long id,
        String tenantId,
        String agentId,
        Long creatorUserId,
        String period,
        BigDecimal grossRevenue,
        BigDecimal platformShare,
        BigDecimal creatorShare,
        BigDecimal platformRate,
        String status,
        Instant settledAt,
        Instant paidAt,
        Instant createdAt
) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_PAID = "PAID";

    public static final BigDecimal DEFAULT_PLATFORM_RATE = new BigDecimal("0.2000");
    public static final BigDecimal DEFAULT_CREATOR_RATE = new BigDecimal("0.8000");

    /**
     * Returns {@code true} if this share is in PENDING status.
     */
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    /**
     * Returns {@code true} if this share is in SETTLED status.
     */
    public boolean isSettled() {
        return STATUS_SETTLED.equals(status);
    }

    /**
     * Returns {@code true} if this share is in PAID status.
     */
    public boolean isPaid() {
        return STATUS_PAID.equals(status);
    }

    /**
     * Computes the platform share and creator share from the given gross revenue.
     *
     * @param grossRevenue the gross revenue amount
     * @param platformRate the platform rate (e.g. 0.20 for 20%)
     * @return array of [platformShare, creatorShare]
     */
    public static BigDecimal[] computeShares(BigDecimal grossRevenue, BigDecimal platformRate) {
        BigDecimal platformShare = grossRevenue.multiply(platformRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal creatorShare = grossRevenue.subtract(platformShare);
        return new BigDecimal[]{platformShare, creatorShare};
    }
}
