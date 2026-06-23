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

package com.miracle.ai.seahorse.agent.kernel.application.conversation;

import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ForkCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ForkResult;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchCursor;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class KernelConversationBranchServiceTests {

    @Test
    void shouldForkEditedMessageAsSiblingAndActivateNewPath() {
        InMemoryBranchRepository repository = new InMemoryBranchRepository();
        repository.messages.add(message(1L, null, 1, 0, "user", "first"));
        repository.messages.add(message(2L, 1L, 1, 0, "assistant", "old"));
        KernelConversationBranchService service =
                new KernelConversationBranchService(repository, new MessageTreeAssembler());

        ForkResult result = service.fork(new ForkCommand("1", "1", 2L, "new answer", "assistant", false));

        assertEquals(3L, result.newMessageId());
        assertEquals(1L, result.parentId());
        ConversationMessageRecord fresh = repository.messages.stream()
                .filter(message -> "3".equals(message.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1L, fresh.getParentId());
        assertEquals(1, fresh.getSiblingSeq());
        assertIterableEquals(List.of(1L, 3L), repository.activeIds.stream().sorted().toList());
    }

    @Test
    void shouldSaveAndLoadUserBranchCursor() {
        InMemoryBranchRepository repository = new InMemoryBranchRepository();
        repository.messages.add(message(1L, null, 1, 0, "user", "first"));
        repository.messages.add(message(2L, 1L, 1, 0, "assistant", "old"));
        KernelConversationBranchService service =
                new KernelConversationBranchService(repository, new MessageTreeAssembler());

        ConversationBranchCursor cursor = service.saveCursor("1", "1", 2L);

        assertEquals(2L, cursor.getLeafMessageId());
        assertEquals(2L, repository.cursorLeafMessageId);
        assertEquals(2L, service.loadCursor("1", "1").orElseThrow().getLeafMessageId());
    }

    @Test
    void shouldRestoreSavedBranchCursorWhenConversationReloads() {
        InMemoryBranchRepository repository = new InMemoryBranchRepository();
        repository.messages.add(message(1L, null, 1, 0, "user", "first"));
        repository.messages.add(message(2L, 1L, 1, 0, "assistant", "old branch answer"));
        repository.messages.add(message(3L, 1L, 0, 1, "assistant", "new branch answer"));
        repository.messages.add(message(4L, 3L, 0, 0, "user", "follow the new branch"));
        repository.cursorLeafMessageId = 4L;
        KernelConversationBranchService service =
                new KernelConversationBranchService(repository, new MessageTreeAssembler());

        List<String> activeMessageIds = service.loadActiveTree("1", "1").stream()
                .map(node -> node.message().getId())
                .toList();

        assertIterableEquals(List.of("1", "3", "4"), activeMessageIds);
        assertIterableEquals(List.of(2L), service.loadActiveTree("1", "1").get(1).preSiblings());
        assertEquals(0, repository.messages.get(2).getActive());
    }

    @Test
    void shouldSaveCursorWhenSwitchingBranch() {
        InMemoryBranchRepository repository = new InMemoryBranchRepository();
        repository.messages.add(message(1L, null, 1, 0, "user", "first"));
        repository.messages.add(message(2L, 1L, 1, 0, "assistant", "old"));
        repository.messages.add(message(3L, 1L, 0, 1, "assistant", "new"));
        KernelConversationBranchService service =
                new KernelConversationBranchService(repository, new MessageTreeAssembler());

        service.switchBranch("1", "1", 3L);

        assertEquals(3L, repository.cursorLeafMessageId);
        assertEquals(Set.of(), repository.activeIds);
    }

    private static ConversationMessageRecord message(Long id, Long parentId, int active, int siblingSeq,
                                                     String role, String content) {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(String.valueOf(id));
        record.setConversationId("1");
        record.setRole(role);
        record.setContent(content);
        record.setParentId(parentId);
        record.setActive(active);
        record.setSiblingSeq(siblingSeq);
        return record;
    }

    private static final class InMemoryBranchRepository implements ConversationBranchRepositoryPort {
        private final List<ConversationMessageRecord> messages = new ArrayList<>();
        private Set<Long> activeIds = Set.of();
        private Long cursorLeafMessageId;
        private long nextId = 3L;

        @Override
        public Long appendMessage(ConversationMessageRecord record) {
            record.setId(String.valueOf(nextId++));
            messages.add(record);
            return Long.parseLong(record.getId());
        }

        @Override
        public List<ConversationMessageRecord> listSiblings(String conversationId, String userId, Long parentId) {
            return messages.stream()
                    .filter(message -> parentId == null
                            ? message.getParentId() == null
                            : parentId.equals(message.getParentId()))
                    .sorted(Comparator.comparing(ConversationMessageRecord::getSiblingSeq))
                    .toList();
        }

        @Override
        public List<ConversationMessageRecord> listTree(String conversationId, String userId) {
            return List.copyOf(messages);
        }

        @Override
        public void setActivePath(String conversationId, String userId, Set<Long> activeIds) {
            this.activeIds = Set.copyOf(activeIds);
        }

        @Override
        public ConversationBranchCursor upsertCursor(String conversationId, String userId, Long leafMessageId) {
            cursorLeafMessageId = leafMessageId;
            return ConversationBranchCursor.builder()
                    .tenantId("default")
                    .conversationId(conversationId)
                    .userId(userId)
                    .leafMessageId(leafMessageId)
                    .build();
        }

        @Override
        public Optional<ConversationBranchCursor> findCursor(String conversationId, String userId) {
            return Optional.ofNullable(cursorLeafMessageId)
                    .map(leaf -> ConversationBranchCursor.builder()
                            .tenantId("default")
                            .conversationId(conversationId)
                            .userId(userId)
                            .leafMessageId(leaf)
                            .build());
        }
    }
}
