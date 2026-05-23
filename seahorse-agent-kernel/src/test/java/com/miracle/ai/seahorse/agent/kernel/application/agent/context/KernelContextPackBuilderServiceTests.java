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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelContextPackBuilderServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldBuildPackWithOnlyAclAllowedNonSecretItems() {
        RecordingContextPackRepository repository = new RecordingContextPackRepository();
        ContextPackBuilderInboundPort service = new KernelContextPackBuilderService(
                allowOnly("doc-1", "memory-1"),
                repository,
                FIXED_CLOCK);

        ContextPack pack = service.build(new ContextBuildRequest(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "answer customer question",
                300,
                List.of(
                        candidate("doc-1", ContextItemSourceType.RAG_CHUNK, ContextSensitivity.INTERNAL, 0.91, 90),
                        candidate("doc-2", ContextItemSourceType.RAG_CHUNK, ContextSensitivity.INTERNAL, 0.89, 90),
                        candidate("secret-1", ContextItemSourceType.MEMORY, ContextSensitivity.SECRET, 0.99, 40),
                        candidate("memory-1", ContextItemSourceType.MEMORY, ContextSensitivity.CONFIDENTIAL, 0.81,
                                40))));

        assertTrue(pack.contextPackId().startsWith("ctx_run-1_"));
        assertEquals(2, pack.itemCount());
        assertEquals(List.of("doc-1", "memory-1"), pack.items().stream().map(ContextItem::sourceId).toList());
        for (ContextItem item : pack.items()) {
            assertTrue(item.aclDecisionId().startsWith("decision-"));
            assertTrue(item.citationJson().contains(item.sourceId()));
            assertNotEquals(ContextSensitivity.SECRET, item.sensitivity());
        }
        assertEquals(pack, repository.savedPack);
    }

    @Test
    void shouldKeepHighestScoreItemsWithinTokenBudget() {
        RecordingContextPackRepository repository = new RecordingContextPackRepository();
        ContextPackBuilderInboundPort service = new KernelContextPackBuilderService(
                allowAll(),
                repository,
                FIXED_CLOCK);

        ContextPack pack = service.build(new ContextBuildRequest(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "answer customer question",
                130,
                List.of(
                        candidate("low", ContextItemSourceType.RAG_CHUNK, ContextSensitivity.INTERNAL, 0.10, 70),
                        candidate("high", ContextItemSourceType.RAG_CHUNK, ContextSensitivity.INTERNAL, 0.95, 80),
                        candidate("mid", ContextItemSourceType.MEMORY, ContextSensitivity.INTERNAL, 0.75, 50))));

        assertEquals(List.of("high", "mid"), pack.items().stream().map(ContextItem::sourceId).toList());
        int usedTokens = pack.items().stream().mapToInt(ContextItem::estimatedTokens).sum();
        assertTrue(usedTokens <= 130);
    }

    private static ContextBuildItemCandidate candidate(String sourceId,
                                                       ContextItemSourceType sourceType,
                                                       ContextSensitivity sensitivity,
                                                       double score,
                                                       int estimatedTokens) {
        return new ContextBuildItemCandidate(
                sourceType,
                sourceId,
                "content for " + sourceId,
                "summary for " + sourceId,
                score,
                0.88,
                sensitivity,
                new ResourceRef("DOCUMENT", sourceId, "tenant-1", "user-1", "{}"),
                "{\"sourceId\":\"" + sourceId + "\"}",
                estimatedTokens,
                null);
    }

    private static ResourceAccessPolicyPort allowAll() {
        return request -> decision(request, AccessDecisionEffect.ALLOW);
    }

    private static ResourceAccessPolicyPort allowOnly(String... resourceIds) {
        List<String> allowed = List.of(resourceIds);
        return request -> decision(request,
                allowed.contains(request.resourceRef().resourceId())
                        ? AccessDecisionEffect.ALLOW
                        : AccessDecisionEffect.DENY);
    }

    private static AccessDecision decision(ResourceAccessRequest request, AccessDecisionEffect effect) {
        return new AccessDecision(
                "decision-" + request.resourceRef().resourceId(),
                request.resourceRef().tenantId(),
                AccessSubjectType.USER_DELEGATED_AGENT,
                request.subjectId(),
                ResourceAction.READ,
                request.resourceRef().resourceType(),
                request.resourceRef().resourceId(),
                effect,
                effect == AccessDecisionEffect.ALLOW ? "ALLOWED" : "DENIED",
                NOW);
    }

    private static final class RecordingContextPackRepository implements ContextPackRepositoryPort {

        private ContextPack savedPack;

        @Override
        public void save(ContextPack pack) {
            savedPack = pack;
        }

        @Override
        public Optional<ContextPack> findById(String contextPackId) {
            assertNotNull(contextPackId);
            return Optional.ofNullable(savedPack)
                    .filter(pack -> pack.contextPackId().equals(contextPackId));
        }

        @Override
        public List<ContextItem> listItems(String contextPackId) {
            if (savedPack == null || !savedPack.contextPackId().equals(contextPackId)) {
                return List.of();
            }
            return savedPack.items().stream()
                    .sorted(Comparator.comparing(ContextItem::itemId))
                    .toList();
        }
    }
}
