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

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;

import java.util.List;
import java.util.Objects;

/**
 * 模型 token 估算端口。
 */
public interface TokenCounterPort {

    int countTextTokens(String modelId, String text);

    default int countMessages(String modelId, List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : Objects.requireNonNullElse(messages, List.<ChatMessage>of())) {
            if (message != null) {
                total += countTextTokens(modelId, message.getContent());
            }
        }
        return total;
    }

    static TokenCounterPort approximate() {
        return (modelId, text) -> {
            String value = Objects.requireNonNullElse(text, "");
            if (value.isBlank()) {
                return 0;
            }
            return Math.max(1, (int) Math.ceil(value.codePointCount(0, value.length()) / 4.0D));
        };
    }
}
