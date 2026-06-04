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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelContextPackQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldAllowOwnerToQueryPackAndItems() {
        RecordingContextPackRepository repository = new RecordingContextPackRepository(pack("user-1"));
        KernelContextPackQueryService service = new KernelContextPackQueryService(repository, currentUser(1L,
                "user"));

        Optional<ContextPack> result = service.findById("context-pack-1");
        List<ContextItem> items = service.listItems("context-pack-1");

        assertTrue(result.isPresent());
        assertEquals("user-1", result.orElseThrow().userId());
        assertEquals(List.of("item-1"), items.stream().map(ContextItem::itemId).toList());
    }

    @Test
    void shouldAllowAdminToQueryAnotherUsersPackAndItems() {
        RecordingContextPackRepository repository = new RecordingContextPackRepository(pack("user-1"));
        KernelContextPackQueryService service = new KernelContextPackQueryService(repository, currentUser(1L,
                "admin"));

        Optional<ContextPack> result = service.findById("context-pack-1");
        List<ContextItem> items = service.listItems("context-pack-1");

        assertTrue(result.isPresent());
        assertEquals("user-1", result.orElseThrow().userId());
        assertEquals(1, items.size());
    }

    @Test
    void shouldDenyUnrelatedUserForPackAndItems() {
        RecordingContextPackRepository repository = new RecordingContextPackRepository(pack("user-1"));
        KernelContextPackQueryService service = new KernelContextPackQueryService(repository, currentUser(3L,
                "user"));

        assertThrows(IllegalStateException.class, () -> service.findById("context-pack-1"));
        assertThrows(IllegalStateException.class, () -> service.listItems("context-pack-1"));
    }

    @Test
    void shouldReturnEmptyWhenPackDoesNotExist() {
        KernelContextPackQueryService service = new KernelContextPackQueryService(new RecordingContextPackRepository(
                null), currentUser(2L, "user"));

        assertTrue(service.findById("missing").isEmpty());
        assertEquals(List.of(), service.listItems("missing"));
    }

    private static CurrentUserPort currentUser(Long userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, role + "-" + userId, role, null));
    }

    private static ContextPack pack(String userId) {
        return new ContextPack(
                "context-pack-1",
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                userId,
                "answer customer question",
                300,
                List.of(item()),
                NOW);
    }

    private static ContextItem item() {
        return new ContextItem(
                "item-1",
                "context-pack-1",
                ContextItemSourceType.MEMORY,
                "memory-1",
                "memory content",
                null,
                0.9D,
                0.9D,
                ContextSensitivity.CONFIDENTIAL,
                "decision-1",
                "{\"source\":\"memory\"}",
                10,
                null,
                NOW);
    }

    private static final class RecordingContextPackRepository implements ContextPackRepositoryPort {

        private final ContextPack pack;

        private RecordingContextPackRepository(ContextPack pack) {
            this.pack = pack;
        }

        @Override
        public void save(ContextPack pack) {
        }

        @Override
        public Optional<ContextPack> findById(String contextPackId) {
            if (pack == null || !pack.contextPackId().equals(contextPackId)) {
                return Optional.empty();
            }
            return Optional.of(pack);
        }

        @Override
        public List<ContextItem> listItems(String contextPackId) {
            if (pack == null || !pack.contextPackId().equals(contextPackId)) {
                return List.of();
            }
            return pack.items();
        }
    }
}
