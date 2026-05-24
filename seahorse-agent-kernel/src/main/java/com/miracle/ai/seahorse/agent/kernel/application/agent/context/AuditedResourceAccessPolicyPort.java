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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;

import java.util.Objects;

public class AuditedResourceAccessPolicyPort implements ResourceAccessPolicyPort {

    private final ResourceAccessPolicyPort delegate;
    private final AccessDecisionLogPort logPort;

    public AuditedResourceAccessPolicyPort(ResourceAccessPolicyPort delegate,
                                           AccessDecisionLogPort logPort) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.logPort = Objects.requireNonNullElseGet(logPort, AccessDecisionLogPort::empty);
    }

    @Override
    public AccessDecision decide(ResourceAccessRequest request) {
        AccessDecision decision = delegate.decide(request);
        logPort.record(decision);
        return decision;
    }
}
