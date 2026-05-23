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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;

import java.time.Instant;
import java.util.Objects;

public interface ResourceAccessPolicyPort {

    AccessDecision decide(ResourceAccessRequest request);

    static ResourceAccessPolicyPort denyAll() {
        return request -> {
            ResourceAccessRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
            return new AccessDecision(
                    "deny-" + safeRequest.resourceRef().resourceType() + "-" + safeRequest.resourceRef().resourceId(),
                    safeRequest.tenantId(),
                    safeRequest.subjectType(),
                    safeRequest.subjectId(),
                    safeRequest.action(),
                    safeRequest.resourceRef().resourceType(),
                    safeRequest.resourceRef().resourceId(),
                    AccessDecisionEffect.DENY,
                    "DEFAULT_DENY",
                    Instant.now());
        };
    }
}
