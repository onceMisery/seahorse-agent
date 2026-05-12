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

import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;

import java.util.Objects;

/**
 * 问答前置阶段端口集合。
 */
public record ChatPreparationPorts(ConversationMemoryPort memoryPort,
                                   QueryRewritePort queryRewritePort,
                                   IntentResolutionPort intentResolutionPort,
                                   IntentGuidancePort intentGuidancePort,
                                   RetrievalContextPort retrievalContextPort) {

    public ChatPreparationPorts {
        Objects.requireNonNull(memoryPort, "对话记忆端口不能为空");
        Objects.requireNonNull(queryRewritePort, "查询改写端口不能为空");
        Objects.requireNonNull(intentResolutionPort, "意图解析端口不能为空");
        Objects.requireNonNull(intentGuidancePort, "意图引导端口不能为空");
        Objects.requireNonNull(retrievalContextPort, "检索上下文端口不能为空");
    }
}
