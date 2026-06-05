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

package com.miracle.ai.seahorse.agent.kernel.domain.billing;

import java.time.Instant;

/**
 * A tenant's active subscription to a plan, carrying quota limits derived from the plan.
 *
 * @param id                primary key
 * @param tenantId          owning tenant
 * @param planCode          subscribed plan tier
 * @param status            lifecycle status: ACTIVE / CANCELED / EXPIRED
 * @param startedAt         subscription start timestamp
 * @param expiresAt         subscription expiry timestamp
 * @param tokenLimit        token quota for the billing period
 * @param storageLimitBytes storage quota in bytes
 * @param concurrencyLimit  max concurrent agent runs
 */
public record Subscription(Long id,
                           String tenantId,
                           PlanCode planCode,
                           String status,
                           Instant startedAt,
                           Instant expiresAt,
                           long tokenLimit,
                           long storageLimitBytes,
                           int concurrencyLimit) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    /**
     * Returns {@code true} if this subscription is in ACTIVE status.
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /**
     * Returns {@code true} if this subscription has expired relative to the given instant.
     *
     * @param now the reference instant
     * @return whether the subscription is expired
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
