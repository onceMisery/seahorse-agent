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

package com.miracle.ai.seahorse.agent.kernel.domain.user;

import java.time.Instant;

/**
 * Domain model representing a user's trial subscription.
 *
 * <p>A trial is bound to a specific tenant and user, carries quota limits,
 * and transitions through the statuses {@code ACTIVE → EXPIRED | CONVERTED}.
 */
public record UserTrial(
        Long id,
        String tenantId,
        Long userId,
        String planCode,
        String status,
        long tokenLimit,
        long storageLimitBytes,
        int concurrencyLimit,
        Instant startedAt,
        Instant expiresAt) {

    /** Trial status constants. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CONVERTED = "CONVERTED";

    /**
     * Returns {@code true} if the trial has passed its expiration time.
     *
     * @param now the current time reference
     * @return whether the trial is expired
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /**
     * Returns {@code true} if the trial status is {@link #STATUS_ACTIVE}.
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
