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

package com.miracle.ai.seahorse.agent.ports.outbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 查询优化端口。
 *
 * <p>在查询改写（{@link QueryRewritePort}）之前执行，对用户原始问题进行
 * 术语映射、专有名词保护等确定性优化。
 *
 * <p>该端口不负责 LLM 改写——那是 QueryRewritePort 的职责。
 */
public interface QueryOptimizerPort {

    /**
     * 优化用户查询。
     *
     * @param originalQuestion 用户原始问题
     * @param history          当前会话历史
     * @param memoryContext    四层记忆上下文（可为 null）
     * @return 优化结果
     */
    QueryOptimizationResult optimize(String originalQuestion,
                                     List<ChatMessage> history,
                                     MemoryContext memoryContext);

    static QueryOptimizerPort passthrough() {
        return (question, history, memoryContext) -> new QueryOptimizationResult(
                Objects.requireNonNullElse(question, ""),
                Objects.requireNonNullElse(question, ""),
                Map.of(),
                List.of(),
                List.of("passthrough"));
    }
}
