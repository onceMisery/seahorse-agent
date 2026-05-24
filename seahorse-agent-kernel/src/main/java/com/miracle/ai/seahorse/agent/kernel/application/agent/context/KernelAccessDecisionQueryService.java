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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AccessDecisionQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.Objects;

public class KernelAccessDecisionQueryService implements AccessDecisionQueryInboundPort {

    private static final String ADMIN_ROLE = "admin";

    private final AccessDecisionQueryPort queryPort;
    private final CurrentUserPort currentUserPort;

    public KernelAccessDecisionQueryService(AccessDecisionQueryPort queryPort,
                                            CurrentUserPort currentUserPort) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public AccessDecisionPage page(String tenantId,
                                   AccessSubjectType subjectType,
                                   String subjectId,
                                   ResourceAction action,
                                   String resourceType,
                                   String resourceId,
                                   AccessDecisionEffect effect,
                                   String reasonCode,
                                   long current,
                                   long size) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return queryPort.page(new AccessDecisionQuery(
                tenantId,
                subjectType,
                subjectId,
                action,
                resourceType,
                resourceId,
                effect,
                reasonCode,
                current,
                size));
    }
}
