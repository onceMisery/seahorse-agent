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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentExternalInvocationCommand(
        String tenantId,
        String userId,
        String agentName,
        String question,
        List<ChatMessage> history,
        Map<String, String> metadata,
        String preferredExecutorEngine) {

    public AgentExternalInvocationCommand {
        tenantId = trimToNull(tenantId);
        userId = Objects.requireNonNullElse(userId, "");
        agentName = trimToNull(agentName);
        question = requireText(question, "question");
        history = List.copyOf(Objects.requireNonNullElse(history, List.of()));
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        preferredExecutorEngine = trimToNull(preferredExecutorEngine);
    }

    private static String requireText(String value, String name) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
