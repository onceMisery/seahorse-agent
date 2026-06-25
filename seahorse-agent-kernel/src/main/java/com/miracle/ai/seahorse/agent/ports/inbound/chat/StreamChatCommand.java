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

package com.miracle.ai.seahorse.agent.ports.inbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;

import java.util.List;
import java.util.Objects;

/**
 * 流式问答命令。
 */
public record StreamChatCommand(
        String question,
        String conversationId,
        String taskId,
        String userId,
        boolean deepThinking,
        ChatMode chatMode,
        String agentId,
        String versionId,
        String taskTemplateId,
        List<String> attachmentIds,
        List<String> selectedSkillNames,
        List<String> knowledgeBaseIds,
        Long roleCardId,
        Long branchLeafMessageId,
        Long assistantParentMessageId,
        Long runProfileId) {

    public StreamChatCommand {
        question = requireText(question, "question");
        conversationId = requireText(conversationId, "conversationId");
        taskId = requireText(taskId, "taskId");
        userId = Objects.requireNonNullElse(userId, "");
        chatMode = Objects.requireNonNullElse(chatMode, ChatMode.RAG);
        agentId = trimToNull(agentId);
        versionId = trimToNull(versionId);
        taskTemplateId = trimToNull(taskTemplateId);
        attachmentIds = normalizeIds(attachmentIds);
        selectedSkillNames = normalizeSkillNames(selectedSkillNames);
        knowledgeBaseIds = normalizeIds(knowledgeBaseIds);
        roleCardId = normalizeRoleCardId(roleCardId);
        branchLeafMessageId = normalizePositiveId(branchLeafMessageId);
        assistantParentMessageId = normalizePositiveId(assistantParentMessageId);
        runProfileId = normalizePositiveId(runProfileId);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode,
                             String agentId,
                             String versionId,
                             String taskTemplateId,
                             List<String> attachmentIds,
                             List<String> selectedSkillNames,
                             List<String> knowledgeBaseIds,
                             Long roleCardId,
                             Long branchLeafMessageId,
                             Long runProfileId) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, agentId, versionId, taskTemplateId,
                attachmentIds, selectedSkillNames, knowledgeBaseIds, roleCardId, branchLeafMessageId, null, runProfileId);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode,
                             String agentId,
                             String versionId,
                             String taskTemplateId,
                             List<String> attachmentIds,
                             List<String> selectedSkillNames,
                             List<String> knowledgeBaseIds,
                             Long roleCardId,
                             Long branchLeafMessageId) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, agentId, versionId, taskTemplateId,
                attachmentIds, selectedSkillNames, knowledgeBaseIds, roleCardId, branchLeafMessageId, null, null);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode,
                             String agentId,
                             String versionId,
                             String taskTemplateId,
                             List<String> attachmentIds,
                             List<String> selectedSkillNames,
                             List<String> knowledgeBaseIds,
                             Long roleCardId) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, agentId, versionId, taskTemplateId,
                attachmentIds, selectedSkillNames, knowledgeBaseIds, roleCardId, null, null);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode,
                             String agentId,
                             String versionId,
                             String taskTemplateId,
                             List<String> attachmentIds,
                             List<String> selectedSkillNames,
                             List<String> knowledgeBaseIds) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, agentId, versionId, taskTemplateId,
                attachmentIds, selectedSkillNames, knowledgeBaseIds, null, null);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode,
                             String agentId,
                             String versionId,
                             String taskTemplateId,
                             List<String> attachmentIds,
                             List<String> selectedSkillNames) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, agentId, versionId, taskTemplateId,
                attachmentIds, selectedSkillNames, List.of(), null);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode,
                             String agentId,
                             String versionId,
                             String taskTemplateId,
                             List<String> attachmentIds) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, agentId, versionId, taskTemplateId,
                attachmentIds, List.of(), List.of(), null);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode,
                             String agentId,
                             String versionId) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, agentId, versionId, null, List.of(),
                List.of(), List.of(), null);
    }

    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking,
                             ChatMode chatMode) {
        this(question, conversationId, taskId, userId, deepThinking, chatMode, null, null, null, List.of(), List.of(),
                List.of(), null);
    }

    /**
     * 兼容旧 5 参签名：缺省 {@link ChatMode#RAG}。
     */
    public StreamChatCommand(String question,
                             String conversationId,
                             String taskId,
                             String userId,
                             boolean deepThinking) {
        this(question, conversationId, taskId, userId, deepThinking, ChatMode.RAG, null, null, null, List.of(),
                List.of(), List.of(), null);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static List<String> normalizeIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static final int MAX_SELECTED_SKILLS = 5;

    private static List<String> normalizeSkillNames(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase().replace('_', '-'))
                .distinct()
                .toList();
        if (normalized.size() > MAX_SELECTED_SKILLS) {
            throw new IllegalArgumentException(
                    "Too many selectedSkillNames: " + normalized.size()
                            + " (maximum " + MAX_SELECTED_SKILLS + ")");
        }
        return normalized;
    }

    private static Long normalizeRoleCardId(Long value) {
        return value == null || value == 0 ? null : value;
    }

    private static Long normalizePositiveId(Long value) {
        return value == null || value <= 0 ? null : value;
    }
}
