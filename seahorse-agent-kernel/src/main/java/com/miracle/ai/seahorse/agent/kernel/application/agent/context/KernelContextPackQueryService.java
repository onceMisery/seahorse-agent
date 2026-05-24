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
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelContextPackQueryService implements ContextPackQueryInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String CONTEXT_PACK_FORBIDDEN_MESSAGE = "Context pack access denied";

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
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        return contextPackRepositoryPort.findById(requireText(contextPackId, "contextPackId must not be blank"))
                .map(pack -> requireReadable(pack, currentUser));
    }

    @Override
    public List<ContextItem> listItems(String contextPackId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeContextPackId = requireText(contextPackId, "contextPackId must not be blank");
        Optional<ContextPack> pack = contextPackRepositoryPort.findById(safeContextPackId);
        if (pack.isEmpty()) {
            return List.of();
        }
        requireReadable(pack.orElseThrow(), currentUser);
        return contextPackRepositoryPort.listItems(safeContextPackId);
    }

    private ContextPack requireReadable(ContextPack pack, CurrentUser currentUser) {
        if (currentUser.hasRole(ADMIN_ROLE) || pack.userId().equals(currentUser.userId())) {
            return pack;
        }
        throw new IllegalStateException(CONTEXT_PACK_FORBIDDEN_MESSAGE);
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
