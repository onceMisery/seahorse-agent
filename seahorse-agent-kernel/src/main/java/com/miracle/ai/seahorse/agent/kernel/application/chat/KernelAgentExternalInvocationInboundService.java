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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class KernelAgentExternalInvocationInboundService implements AgentExternalInvocationInboundPort {

    private static final String METADATA_CONVERSATION_ID = "conversationId";
    private static final String METADATA_CONVERSATION_ID_SNAKE = "conversation_id";
    private static final String METADATA_TASK_ID = "taskId";
    private static final String METADATA_TASK_ID_SNAKE = "task_id";
    private static final String METADATA_USER_ID = "userId";
    private static final String METADATA_USER_ID_SNAKE = "user_id";
    private static final String METADATA_AGENT_ID = "agentId";
    private static final String METADATA_AGENT_ID_SNAKE = "agent_id";
    private static final String METADATA_VERSION_ID = "versionId";
    private static final String METADATA_TASK_TEMPLATE_ID = "taskTemplateId";
    private static final String METADATA_RUN_PROFILE_ID = "runProfileId";

    private final ChatInboundPort chatInboundPort;

    public KernelAgentExternalInvocationInboundService(ChatInboundPort chatInboundPort) {
        this.chatInboundPort = Objects.requireNonNull(chatInboundPort, "chatInboundPort must not be null");
    }

    @Override
    public StreamCancellationHandle streamInvoke(
            AgentExternalInvocationCommand command,
            StreamCallback callback) {
        AgentExternalInvocationCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        StreamCallback safeCallback = Objects.requireNonNull(callback, "callback must not be null");
        Map<String, String> metadata = safeCommand.metadata();
        String conversationId = firstText(
                metadata.get(METADATA_CONVERSATION_ID),
                metadata.get(METADATA_CONVERSATION_ID_SNAKE),
                nextPositiveId());
        String taskId = firstText(
                metadata.get(METADATA_TASK_ID),
                metadata.get(METADATA_TASK_ID_SNAKE),
                "external-agent-" + nextPositiveId());
        String userId = firstText(
                safeCommand.userId(),
                metadata.get(METADATA_USER_ID),
                metadata.get(METADATA_USER_ID_SNAKE),
                defaultExternalUserId(safeCommand));
        StreamChatCommand chatCommand = new StreamChatCommand(
                safeCommand.question(),
                conversationId,
                taskId,
                userId,
                false,
                ChatMode.AGENT,
                firstText(metadata.get(METADATA_AGENT_ID), metadata.get(METADATA_AGENT_ID_SNAKE)),
                metadata.get(METADATA_VERSION_ID),
                metadata.get(METADATA_TASK_TEMPLATE_ID),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                null,
                parseLong(metadata.get(METADATA_RUN_PROFILE_ID)),
                safeCommand.history(),
                safeCommand.preferredExecutorEngine(),
                safeCommand.tenantId(),
                externalCurrentUser(safeCommand, userId));
        chatInboundPort.streamChat(chatCommand, safeCallback);
        return () -> chatInboundPort.stopTask(taskId);
    }

    private CurrentUser externalCurrentUser(AgentExternalInvocationCommand command, String userId) {
        return new CurrentUser(
                parseLong(userId),
                userId,
                "agent",
                null,
                command.tenantId());
    }

    private String defaultExternalUserId(AgentExternalInvocationCommand command) {
        return "external-agent:%s:%s".formatted(
                firstText(command.tenantId(), "default"),
                firstText(command.agentName(), "agent"));
    }

    private String nextPositiveId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    }

    private Long parseLong(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String safeValue = trimToNull(value);
            if (safeValue != null) {
                return safeValue;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
