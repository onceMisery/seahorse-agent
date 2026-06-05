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

package com.miracle.ai.seahorse.agent.ports.outbound.auth;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for recording and querying login history events.
 */
public interface LoginHistoryPort {

    /**
     * Record a login attempt.
     *
     * @param userId        the user ID (may be 0 if user was not found)
     * @param tenantId      the tenant ID
     * @param loginType     the login type, e.g. "PASSWORD", "OAUTH", "TOKEN_REFRESH"
     * @param ipAddress     the client IP address (may be null)
     * @param userAgent     the User-Agent header (may be null)
     * @param deviceInfo    parsed device information (may be null)
     * @param status        the outcome: "SUCCESS", "FAILED", or "BLOCKED"
     * @param failureReason the reason for failure (null on success)
     */
    void recordLogin(long userId, String tenantId, String loginType,
                     String ipAddress, String userAgent, String deviceInfo,
                     String status, String failureReason);

    /**
     * Find login history entries for a user with pagination.
     *
     * @param userId the user ID
     * @param page   the page number (0-based)
     * @param size   the page size
     * @return list of login history entries
     */
    List<LoginHistoryEntry> findByUserId(long userId, int page, int size);

    /**
     * Count total login history entries for a user.
     *
     * @param userId the user ID
     * @return the count
     */
    long countByUserId(long userId);

    /**
     * Login history entry record.
     */
    record LoginHistoryEntry(Long id, long userId, String tenantId, String loginType,
                             String ipAddress, String userAgent, String deviceInfo,
                             String status, String failureReason, Instant createdAt) {
    }
}
