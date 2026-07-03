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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;

import java.util.Objects;

public record SandboxRuntimeProfilePolicyUpsertCommand(String policyId,
                                                       String tenantId,
                                                       SandboxRuntimeType runtimeType,
                                                       String profileId,
                                                       SandboxRuntimeProfilePolicyStatus status,
                                                       Long sessionTtlSeconds,
                                                       Boolean networkAllowed) {

    public SandboxRuntimeProfilePolicyUpsertCommand {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        policyId = policyId == null || policyId.trim().isEmpty() ? null : policyId.trim();
        profileId = profileId == null || profileId.trim().isEmpty() ? null : profileId.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
