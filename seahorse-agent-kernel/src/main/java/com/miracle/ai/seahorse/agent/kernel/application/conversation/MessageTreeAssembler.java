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

import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.MessageTreeNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure message tree assembler for active path and sibling navigation.
 */
public class MessageTreeAssembler {

    private static final Long ROOT_PARENT = Long.MIN_VALUE;

    public List<MessageTreeNode> assemble(List<ConversationMessageRecord> all) {
        MessageIndex index = MessageIndex.of(all);
        List<MessageTreeNode> path = new ArrayList<>();
        Long cursorParent = null;
        Set<Long> visited = new HashSet<>();
        while (true) {
            List<ConversationMessageRecord> siblings = index.childrenOf(cursorParent);
            if (siblings.isEmpty()) {
                return path;
            }
            ConversationMessageRecord activeNode = activeOrFirst(siblings);
            Long activeId = idOf(activeNode);
            if (activeId == null || !visited.add(activeId)) {
                return path;
            }
            int indexInSiblings = siblings.indexOf(activeNode);
            path.add(new MessageTreeNode(
                    activeNode,
                    siblings.subList(0, indexInSiblings).stream().map(MessageTreeAssembler::idOf).toList(),
                    siblings.subList(indexInSiblings + 1, siblings.size()).stream()
                            .map(MessageTreeAssembler::idOf)
                            .toList(),
                    indexInSiblings + 1,
                    siblings.size()));
            cursorParent = activeId;
        }
    }

    public Set<Long> selectActivePathIds(List<ConversationMessageRecord> all, Long targetNodeId) {
        if (targetNodeId == null) {
            return Set.of();
        }
        MessageIndex index = MessageIndex.of(all);
        ConversationMessageRecord target = index.byId().get(targetNodeId);
        if (target == null) {
            return Set.of();
        }

        LinkedHashSet<Long> activeIds = new LinkedHashSet<>();
        ConversationMessageRecord cursor = target;
        while (cursor != null) {
            Long cursorId = idOf(cursor);
            if (cursorId == null || !activeIds.add(cursorId)) {
                break;
            }
            cursor = cursor.getParentId() == null ? null : index.byId().get(cursor.getParentId());
        }

        cursor = target;
        Set<Long> descendantGuard = new HashSet<>(activeIds);
        while (true) {
            List<ConversationMessageRecord> children = index.childrenOf(idOf(cursor));
            if (children.isEmpty()) {
                return activeIds;
            }
            ConversationMessageRecord next = activeOrFirst(children);
            Long nextId = idOf(next);
            if (nextId == null || !descendantGuard.add(nextId)) {
                return activeIds;
            }
            activeIds.add(nextId);
            cursor = next;
        }
    }

    private static ConversationMessageRecord activeOrFirst(List<ConversationMessageRecord> messages) {
        return messages.stream()
                .filter(message -> Integer.valueOf(1).equals(message.getActive()))
                .findFirst()
                .orElse(messages.get(0));
    }

    private static Long idOf(ConversationMessageRecord message) {
        if (message == null || message.getId() == null || message.getId().isBlank()) {
            return null;
        }
        return Long.parseLong(message.getId());
    }

    private record MessageIndex(Map<Long, ConversationMessageRecord> byId,
                                Map<Long, List<ConversationMessageRecord>> byParent) {

        private static MessageIndex of(List<ConversationMessageRecord> all) {
            List<ConversationMessageRecord> safeMessages = Objects.requireNonNullElse(all, List.<ConversationMessageRecord>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .toList();
            Map<Long, ConversationMessageRecord> byId = new HashMap<>();
            for (ConversationMessageRecord message : safeMessages) {
                Long id = idOf(message);
                if (id != null) {
                    byId.put(id, message);
                }
            }
            Map<Long, List<ConversationMessageRecord>> byParent = safeMessages.stream()
                    .collect(Collectors.groupingBy(message -> parentKey(message.getParentId())));
            byParent.values().forEach(messages -> messages.sort(Comparator
                    .comparing((ConversationMessageRecord message) -> Objects.requireNonNullElse(message.getSiblingSeq(), 0))
                    .thenComparing(message -> Objects.requireNonNullElse(idOf(message), Long.MAX_VALUE))));
            return new MessageIndex(byId, byParent);
        }

        private List<ConversationMessageRecord> childrenOf(Long parentId) {
            return byParent.getOrDefault(parentKey(parentId), List.of());
        }

        private static Long parentKey(Long parentId) {
            return parentId == null ? ROOT_PARENT : parentId;
        }
    }
}
