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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        KernelContextPackQueryService service = new KernelContextPackQueryService(new RecordingContextPackRepository(),
                currentUser(2L, "user"));

        assertTrue(service.findById("missing").isEmpty());
        assertEquals(List.of(), service.listItems("missing"));
    }

    @Test
    void shouldCleanupExpiredItemsForReadablePack() {
        RecordingContextPackRepository repository = new RecordingContextPackRepository(pack("user-1"));
        KernelContextPackQueryService service = new KernelContextPackQueryService(
                repository,
                currentUser(1L, "user"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.cleanupExpiredItems("context-pack-1");

        assertEquals("context-pack-1", result.contextPackId());
        assertEquals(NOW, result.cutoff());
        assertEquals(2, result.deletedItemCount());
        assertEquals("context-pack-1", repository.cleanedContextPackId);
        assertEquals(NOW, repository.cleanupCutoff);
    }

    @Test
    void shouldDenyExpiredItemCleanupForUnrelatedUser() {
        RecordingContextPackRepository repository = new RecordingContextPackRepository(pack("user-1"));
        KernelContextPackQueryService service = new KernelContextPackQueryService(
                repository,
                currentUser(3L, "user"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> service.cleanupExpiredItems("context-pack-1"));
        assertEquals(null, repository.cleanedContextPackId);
    }

    @Test
    void shouldDiffReadableContextPacksBySourceKey() {
        ContextPack leftPack = pack("left-pack", "user-1", List.of(
                item("left-unchanged", "left-pack", ContextItemSourceType.MEMORY, "memory-1", "same content"),
                item("left-changed", "left-pack", ContextItemSourceType.RAG_CHUNK, "doc-1", "old content"),
                item("left-removed", "left-pack", ContextItemSourceType.TOOL_RESULT, "tool-1", "removed content")));
        ContextPack rightPack = pack("right-pack", "user-1", List.of(
                item("right-unchanged", "right-pack", ContextItemSourceType.MEMORY, "memory-1", "same content"),
                item("right-changed", "right-pack", ContextItemSourceType.RAG_CHUNK, "doc-1", "new content"),
                item("right-added", "right-pack", ContextItemSourceType.USER_INPUT, "input-1", "added content")));
        KernelContextPackQueryService service = new KernelContextPackQueryService(
                new RecordingContextPackRepository(leftPack, rightPack),
                currentUser(1L, "user"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.diff("left-pack", "right-pack");

        assertEquals("left-pack", result.leftContextPackId());
        assertEquals("right-pack", result.rightContextPackId());
        assertEquals(1, result.addedItemCount());
        assertEquals(1, result.removedItemCount());
        assertEquals(1, result.changedItemCount());
        assertEquals(1, result.unchangedItemCount());
        assertEquals("USER_INPUT:input-1", result.addedItems().get(0).itemKey());
        assertEquals("TOOL_RESULT:tool-1", result.removedItems().get(0).itemKey());
        assertEquals(List.of("content", "summary"), result.changedItems().get(0).changedFields());
    }

    @Test
    void shouldPreserveDuplicateSourceKeysInDiff() {
        ContextPack leftPack = pack("left-pack", "user-1", List.of(
                item("left-memory-1", "left-pack", ContextItemSourceType.MEMORY, "memory-1", "same content"),
                item("left-memory-2", "left-pack", ContextItemSourceType.MEMORY, "memory-1", "old duplicate content")));
        ContextPack rightPack = pack("right-pack", "user-1", List.of(
                item("right-memory-1", "right-pack", ContextItemSourceType.MEMORY, "memory-1", "same content"),
                item("right-memory-2", "right-pack", ContextItemSourceType.MEMORY, "memory-1", "new duplicate content")));
        KernelContextPackQueryService service = new KernelContextPackQueryService(
                new RecordingContextPackRepository(leftPack, rightPack),
                currentUser(1L, "user"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.diff("left-pack", "right-pack");

        assertEquals(0, result.addedItemCount());
        assertEquals(0, result.removedItemCount());
        assertEquals(1, result.changedItemCount());
        assertEquals(1, result.unchangedItemCount());
        assertEquals("MEMORY:memory-1#2", result.changedItems().get(0).itemKey());
        assertEquals(List.of("content", "summary"), result.changedItems().get(0).changedFields());
    }

    @Test
    void shouldDenyDiffWhenEitherPackIsUnreadable() {
        KernelContextPackQueryService service = new KernelContextPackQueryService(
                new RecordingContextPackRepository(pack("left-pack", "user-1", List.of(item())),
                        pack("right-pack", "user-2", List.of(item("right-item", "right-pack",
                                ContextItemSourceType.MEMORY, "memory-1", "same content")))),
                currentUser(1L, "user"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> service.diff("left-pack", "right-pack"));
    }

    private static CurrentUserPort currentUser(Long userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, role + "-" + userId, role, null));
    }

    private static ContextPack pack(String userId) {
        return pack("context-pack-1", userId, List.of(item()));
    }

    private static ContextPack pack(String contextPackId, String userId, List<ContextItem> items) {
        return new ContextPack(
                contextPackId,
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                userId,
                "answer customer question",
                300,
                items,
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

    private static ContextItem item(String itemId,
                                    String contextPackId,
                                    ContextItemSourceType sourceType,
                                    String sourceId,
                                    String content) {
        return new ContextItem(
                itemId,
                contextPackId,
                sourceType,
                sourceId,
                content,
                content,
                0.9D,
                0.9D,
                ContextSensitivity.CONFIDENTIAL,
                "decision-" + sourceId,
                "{\"sourceId\":\"" + sourceId + "\"}",
                10,
                null,
                NOW);
    }

    private static final class RecordingContextPackRepository implements ContextPackRepositoryPort {

        private final Map<String, ContextPack> packs;
        private String cleanedContextPackId;
        private Instant cleanupCutoff;

        private RecordingContextPackRepository(ContextPack... packs) {
            this.packs = Arrays.stream(packs == null ? new ContextPack[0] : packs)
                    .filter(pack -> pack != null)
                    .collect(Collectors.toMap(ContextPack::contextPackId, pack -> pack));
        }

        @Override
        public void save(ContextPack pack) {
        }

        @Override
        public Optional<ContextPack> findById(String contextPackId) {
            return Optional.ofNullable(packs.get(contextPackId));
        }

        @Override
        public List<ContextItem> listItems(String contextPackId) {
            return findById(contextPackId).map(ContextPack::items).orElse(List.of());
        }

        @Override
        public int deleteExpiredItems(String contextPackId, Instant cutoff) {
            cleanedContextPackId = contextPackId;
            cleanupCutoff = cutoff;
            return 2;
        }
    }
}
