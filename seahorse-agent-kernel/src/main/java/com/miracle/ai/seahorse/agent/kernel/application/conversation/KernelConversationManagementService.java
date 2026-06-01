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

import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;

import java.util.List;
import java.util.Objects;

/**
 * Kernel 层会话管理服务。
 */
public class KernelConversationManagementService implements ConversationManagementInboundPort {

    private static final int TITLE_MAX_LENGTH = 128;

    private final ConversationRepositoryPort repositoryPort;

    public KernelConversationManagementService(ConversationRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    @Override
    public String create(String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Long conversationId = repositoryPort.create(userId);
        return String.valueOf(conversationId);
    }

    @Override
    public List<ConversationRecord> listConversations(String userId) {
        if (!hasText(userId)) {
            return List.of();
        }
        return repositoryPort.listConversations(userId);
    }

    @Override
    public void rename(String conversationId, String userId, String title) {
        String safeTitle = requireTitle(title);
        if (!repositoryPort.rename(conversationId, userId, safeTitle)) {
            throw new IllegalArgumentException("conversation not found");
        }
    }

    @Override
    public void delete(String conversationId, String userId) {
        if (!repositoryPort.delete(conversationId, userId)) {
            throw new IllegalArgumentException("conversation not found");
        }
    }

    @Override
    public List<ConversationMessageRecord> listMessages(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        return repositoryPort.listMessages(conversationId, userId);
    }

    private String requireTitle(String title) {
        if (!hasText(title)) {
            throw new IllegalArgumentException("title must not be blank");
        }
        String safeTitle = title.trim();
        if (safeTitle.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("title length must not exceed " + TITLE_MAX_LENGTH);
        }
        return safeTitle;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}