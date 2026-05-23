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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelContextPackQueryService implements ContextPackQueryInboundPort {

    private final ContextPackRepositoryPort contextPackRepositoryPort;
    private final CurrentUserPort currentUserPort;

    public KernelContextPackQueryService(ContextPackRepositoryPort contextPackRepositoryPort,
                                         CurrentUserPort currentUserPort) {
        this.contextPackRepositoryPort = Objects.requireNonNull(
                contextPackRepositoryPort,
                "contextPackRepositoryPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public Optional<ContextPack> findById(String contextPackId) {
        currentUserPort.requireCurrentUser();
        return contextPackRepositoryPort.findById(requireText(contextPackId, "contextPackId must not be blank"));
    }

    @Override
    public List<ContextItem> listItems(String contextPackId) {
        currentUserPort.requireCurrentUser();
        return contextPackRepositoryPort.listItems(requireText(contextPackId, "contextPackId must not be blank"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
