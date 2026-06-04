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

public interface TokenServicePort {

    /**
     * Login with userId only (backward compatible).
     * Delegates to {@link #login(String, String)} with null tenantId.
     *
     * @deprecated Use {@link #login(String, String)} to pass tenantId explicitly.
     */
    @Deprecated
    default String login(String userId) {
        return login(userId, null);
    }

    /**
     * Login with userId and tenantId. The tenantId is stored in the session
     * for multi-tenant context propagation.
     *
     * @param userId   the user identifier
     * @param tenantId the tenant identifier (may be null for default tenant)
     * @return the generated token value
     */
    String login(String userId, String tenantId);

    void logout();
}
