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

/**
 * Outbound port for recording login history events.
 *
 * <p>MVP scope: only write (record) operations; query methods are deferred.
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
     * @param status        the outcome: "SUCCESS", "FAILED", or "BLOCKED"
     * @param failureReason the reason for failure (null on success)
     */
    void recordLogin(long userId, String tenantId, String loginType,
                     String ipAddress, String userAgent,
                     String status, String failureReason);
}
