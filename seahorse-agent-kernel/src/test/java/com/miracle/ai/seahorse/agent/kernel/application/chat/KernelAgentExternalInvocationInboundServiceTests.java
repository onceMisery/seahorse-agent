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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KernelAgentExternalInvocationInboundServiceTests {

    @Test
    void externalInvocationDefaultsMissingUserIdToStableSystemUser() {
        CapturingChatInboundPort chatInboundPort = new CapturingChatInboundPort();
        KernelAgentExternalInvocationInboundService service =
                new KernelAgentExternalInvocationInboundService(chatInboundPort);

        service.streamInvoke(new AgentExternalInvocationCommand(
                "tenant-a",
                null,
                "planner",
                "ping",
                List.of(),
                Map.of("source", "agentscope-a2a"),
                null), noopCallback());

        StreamChatCommand command = chatInboundPort.command.get();
        assertEquals("external-agent:tenant-a:planner", command.userId());
        assertEquals("external-agent:tenant-a:planner", command.currentUser().operator());
        assertEquals("tenant-a", command.currentUser().effectiveTenantId());
        assertNull(command.agentId());
    }

    @Test
    void externalInvocationKeepsExplicitUserId() {
        CapturingChatInboundPort chatInboundPort = new CapturingChatInboundPort();
        KernelAgentExternalInvocationInboundService service =
                new KernelAgentExternalInvocationInboundService(chatInboundPort);

        service.streamInvoke(new AgentExternalInvocationCommand(
                "tenant-a",
                "user-1",
                "planner",
                "ping",
                List.of(),
                Map.of(),
                null), noopCallback());

        StreamChatCommand command = chatInboundPort.command.get();
        assertEquals("user-1", command.userId());
        assertEquals("user-1", command.currentUser().operator());
    }

    @Test
    void externalInvocationCanReadUserIdFromMetadata() {
        CapturingChatInboundPort chatInboundPort = new CapturingChatInboundPort();
        KernelAgentExternalInvocationInboundService service =
                new KernelAgentExternalInvocationInboundService(chatInboundPort);

        service.streamInvoke(new AgentExternalInvocationCommand(
                "tenant-a",
                null,
                "planner",
                "ping",
                List.of(),
                Map.of("user_id", "metadata-user"),
                null), noopCallback());

        StreamChatCommand command = chatInboundPort.command.get();
        assertEquals("metadata-user", command.userId());
        assertEquals("metadata-user", command.currentUser().operator());
    }

    @Test
    void externalInvocationOnlySetsInternalAgentIdFromMetadata() {
        CapturingChatInboundPort chatInboundPort = new CapturingChatInboundPort();
        KernelAgentExternalInvocationInboundService service =
                new KernelAgentExternalInvocationInboundService(chatInboundPort);

        service.streamInvoke(new AgentExternalInvocationCommand(
                "tenant-a",
                null,
                "a2a-protocol-name",
                "ping",
                List.of(),
                Map.of("agentId", "internal-agent-id"),
                null), noopCallback());

        assertEquals("internal-agent-id", chatInboundPort.command.get().agentId());
    }

    private StreamCallback noopCallback() {
        return new StreamCallback() {
            @Override
            public void onContent(String content) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable error) {
            }
        };
    }

    private static final class CapturingChatInboundPort implements ChatInboundPort {

        private final AtomicReference<StreamChatCommand> command = new AtomicReference<>();

        @Override
        public void streamChat(StreamChatCommand command, StreamCallback callback) {
            this.command.set(command);
        }

        @Override
        public void stopTask(String taskId) {
        }
    }
}
