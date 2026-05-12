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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;

import java.util.List;

/**
 * 查询改写端口。
 */
public interface QueryRewritePort {

    /**
     * 结合会话历史改写并拆分用户问题。
     *
     * @param question 用户原始问题
     * @param history  会话历史
     * @return 改写结果
     */
    RewriteResult rewriteWithSplit(String question, List<ChatMessage> history);

    static QueryRewritePort passthrough() {
        return (question, history) -> {
            String safeQuestion = java.util.Objects.requireNonNullElse(question, "");
            return new RewriteResult(safeQuestion, List.of(safeQuestion));
        };
    }
}
