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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildItemCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class KernelContextPackBuilderService implements ContextPackBuilderInboundPort {

    private static final String CONTEXT_PACK_PREFIX = "ctx_";
    private static final String CONTEXT_ITEM_PREFIX = "ctxi_";

    private final ResourceAccessPolicyPort resourceAccessPolicyPort;
    private final ContextPackRepositoryPort contextPackRepositoryPort;
    private final Clock clock;

    public KernelContextPackBuilderService(ResourceAccessPolicyPort resourceAccessPolicyPort,
                                           ContextPackRepositoryPort contextPackRepositoryPort,
                                           Clock clock) {
        this.resourceAccessPolicyPort = Objects.requireNonNullElseGet(
                resourceAccessPolicyPort,
                ResourceAccessPolicyPort::denyAll);
        this.contextPackRepositoryPort = Objects.requireNonNullElseGet(
                contextPackRepositoryPort,
                ContextPackRepositoryPort::empty);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public ContextPack build(ContextBuildRequest request) {
        ContextBuildRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Instant createdAt = clock.instant();
        String contextPackId = contextPackId(safeRequest, createdAt);
        List<ContextItem> items = selectedItems(safeRequest, contextPackId, createdAt);
        ContextPack pack = new ContextPack(
                contextPackId,
                safeRequest.runId(),
                safeRequest.agentId(),
                safeRequest.versionId(),
                safeRequest.tenantId(),
                safeRequest.userId(),
                safeRequest.taskGoal(),
                safeRequest.budgetTokens(),
                items,
                createdAt);
        contextPackRepositoryPort.save(pack);
        return pack;
    }

    private List<ContextItem> selectedItems(ContextBuildRequest request, String contextPackId, Instant createdAt) {
        List<ContextBuildItemCandidate> candidates = request.candidates().stream()
                .sorted(Comparator.comparingDouble(ContextBuildItemCandidate::score).reversed())
                .toList();
        List<ContextItem> items = new ArrayList<>();
        int usedTokens = 0;
        int sequence = 1;
        for (ContextBuildItemCandidate candidate : candidates) {
            if (candidate.sensitivity() == ContextSensitivity.SECRET
                    || usedTokens + candidate.estimatedTokens() > request.budgetTokens()) {
                continue;
            }
            AccessDecision decision = resourceAccessPolicyPort.decide(new ResourceAccessRequest(
                    request.tenantId(),
                    AccessSubjectType.USER_DELEGATED_AGENT,
                    request.userId(),
                    ResourceAction.READ,
                    candidate.resourceRef()));
            if (decision.effect() != AccessDecisionEffect.ALLOW) {
                continue;
            }
            items.add(toItem(contextPackId, candidate, decision, sequence, createdAt));
            usedTokens += candidate.estimatedTokens();
            sequence++;
        }
        return List.copyOf(items);
    }

    private ContextItem toItem(String contextPackId,
                               ContextBuildItemCandidate candidate,
                               AccessDecision decision,
                               int sequence,
                               Instant createdAt) {
        return new ContextItem(
                contextPackId + "_" + CONTEXT_ITEM_PREFIX + sequence,
                contextPackId,
                candidate.sourceType(),
                candidate.sourceId(),
                candidate.content(),
                candidate.summary(),
                candidate.score(),
                candidate.confidence(),
                candidate.sensitivity(),
                decision.decisionId(),
                candidate.citationJson(),
                candidate.estimatedTokens(),
                candidate.expiresAt(),
                createdAt);
    }

    private String contextPackId(ContextBuildRequest request, Instant createdAt) {
        return CONTEXT_PACK_PREFIX + stableIdPart(request.runId()) + "_" + createdAt.toEpochMilli();
    }

    private String stableIdPart(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
