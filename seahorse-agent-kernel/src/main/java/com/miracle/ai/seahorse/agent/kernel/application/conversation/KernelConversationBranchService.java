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

import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationBranchInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ForkCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ForkResult;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchCursor;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.MessageTreeNode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Kernel service for message branch operations.
 */
@RequiredArgsConstructor
public class KernelConversationBranchService implements ConversationBranchInboundPort {

    @NonNull
    private final ConversationBranchRepositoryPort repositoryPort;
    @NonNull
    private final MessageTreeAssembler assembler;

    @Override
    public ForkResult fork(ForkCommand command) {
        ForkCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        requireText(safeCommand.conversationId(), "conversationId must not be blank");
        requireText(safeCommand.userId(), "userId must not be blank");
        requireText(safeCommand.role(), "role must not be blank");
        requireText(safeCommand.content(), "content must not be blank");
        if (safeCommand.anchorMessageId() == null) {
            throw new IllegalArgumentException("anchorMessageId must not be null");
        }

        List<ConversationMessageRecord> all = repositoryPort.listTree(safeCommand.conversationId(), safeCommand.userId());
        ConversationMessageRecord anchor = all.stream()
                .filter(message -> String.valueOf(safeCommand.anchorMessageId()).equals(message.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("anchor message not found"));
        Long parentId = safeCommand.regenerate() ? safeCommand.anchorMessageId() : anchor.getParentId();
        int siblingSeq = repositoryPort.listSiblings(safeCommand.conversationId(), safeCommand.userId(), parentId).size();

        ConversationMessageRecord fresh = new ConversationMessageRecord();
        fresh.setConversationId(safeCommand.conversationId());
        fresh.setUserId(safeCommand.userId());
        fresh.setRole(safeCommand.role());
        fresh.setContent(safeCommand.content());
        fresh.setParentId(parentId);
        fresh.setBranchRootId(parentId);
        fresh.setActive(1);
        fresh.setSiblingSeq(siblingSeq);
        Long newMessageId = repositoryPort.appendMessage(fresh);

        Set<Long> activeIds = assembler.selectActivePathIds(
                repositoryPort.listTree(safeCommand.conversationId(), safeCommand.userId()),
                newMessageId);
        repositoryPort.setActivePath(safeCommand.conversationId(), safeCommand.userId(), activeIds);
        return new ForkResult(newMessageId, parentId);
    }

    @Override
    public List<MessageTreeNode> switchBranch(String conversationId, String userId, Long targetNodeId) {
        requireText(conversationId, "conversationId must not be blank");
        requireText(userId, "userId must not be blank");
        if (targetNodeId == null) {
            throw new IllegalArgumentException("targetNodeId must not be null");
        }
        List<ConversationMessageRecord> all = repositoryPort.listTree(conversationId, userId);
        Set<Long> activeIds = assembler.selectActivePathIds(all, targetNodeId);
        if (activeIds.isEmpty()) {
            throw new IllegalArgumentException("target message not found");
        }
        repositoryPort.upsertCursor(conversationId, userId, targetNodeId);
        return assembleWithActivePath(all, activeIds);
    }

    @Override
    public ConversationBranchCursor saveCursor(String conversationId, String userId, Long leafMessageId) {
        requireText(conversationId, "conversationId must not be blank");
        requireText(userId, "userId must not be blank");
        if (leafMessageId == null) {
            throw new IllegalArgumentException("leafMessageId must not be null");
        }
        Set<Long> activeIds = assembler.selectActivePathIds(
                repositoryPort.listTree(conversationId, userId),
                leafMessageId);
        if (activeIds.isEmpty()) {
            throw new IllegalArgumentException("leaf message not found");
        }
        return repositoryPort.upsertCursor(conversationId, userId, leafMessageId);
    }

    @Override
    public Optional<ConversationBranchCursor> loadCursor(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return Optional.empty();
        }
        return repositoryPort.findCursor(conversationId, userId);
    }

    @Override
    public List<MessageTreeNode> loadActiveTree(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        List<ConversationMessageRecord> all = repositoryPort.listTree(conversationId, userId);
        Optional<ConversationBranchCursor> cursor = repositoryPort.findCursor(conversationId, userId);
        if (cursor.isPresent()) {
            Set<Long> activeIds = assembler.selectActivePathIds(all, cursor.get().getLeafMessageId());
            if (!activeIds.isEmpty()) {
                return assembleWithActivePath(all, activeIds);
            }
        }
        return assembler.assemble(all);
    }

    private List<MessageTreeNode> assembleWithActivePath(List<ConversationMessageRecord> all, Set<Long> activeIds) {
        List<ConversationMessageRecord> projected = all.stream()
                .map(message -> copyWithActive(message, activeIds))
                .toList();
        return assembler.assemble(projected);
    }

    private ConversationMessageRecord copyWithActive(ConversationMessageRecord source, Set<Long> activeIds) {
        ConversationMessageRecord copy = new ConversationMessageRecord();
        copy.setId(source.getId());
        copy.setConversationId(source.getConversationId());
        copy.setUserId(source.getUserId());
        copy.setRole(source.getRole());
        copy.setContent(source.getContent());
        copy.setAgentRunId(source.getAgentRunId());
        copy.setThinkingContent(source.getThinkingContent());
        copy.setThinkingDuration(source.getThinkingDuration());
        copy.setParentId(source.getParentId());
        Long id = source.getId() == null || source.getId().isBlank() ? null : Long.parseLong(source.getId());
        copy.setActive(id != null && activeIds.contains(id) ? 1 : 0);
        copy.setBranchRootId(source.getBranchRootId());
        copy.setSiblingSeq(source.getSiblingSeq());
        copy.setVote(source.getVote());
        copy.setCreateTime(source.getCreateTime());
        return copy;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
