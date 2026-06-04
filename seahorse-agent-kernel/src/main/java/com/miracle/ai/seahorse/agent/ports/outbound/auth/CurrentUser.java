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

public record CurrentUser(Long userId, String username, String role, String avatar, String tenantId) {

    /**
     * Backward-compatible constructor: defaults tenantId to null (resolved at adapter level).
     */
    public CurrentUser(Long userId, String username, String role, String avatar) {
        this(userId, username, role, avatar, null);
    }

    /**
     * Returns the operator identifier for audit trails and ownership comparisons.
     * Uses {@code username} which preserves the application-level operator format
     * (e.g. "admin-1", "user-2") independent of the numeric primary key type.
     */
    public String operator() {
        return username != null ? username : String.valueOf(userId);
    }

    public boolean hasRole(String expectedRole) {
        return expectedRole != null && role != null && expectedRole.equalsIgnoreCase(role);
    }

    /**
     * Returns the effective tenant ID, falling back to the default if null.
     */
    public String effectiveTenantId() {
        return tenantId != null ? tenantId : "default";
    }
}
