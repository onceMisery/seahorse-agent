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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;

import java.util.List;
import java.util.Objects;

public record SandboxPolicyRequest(String tenantId,
                                   String runId,
                                   SandboxRuntimeType runtimeType,
                                   boolean networkRequested,
                                   List<String> requestedHosts) {

    public SandboxPolicyRequest {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        runId = requireText(runId, "runId must not be blank");
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        requestedHosts = requestedHosts == null ? List.of() : List.copyOf(requestedHosts);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
