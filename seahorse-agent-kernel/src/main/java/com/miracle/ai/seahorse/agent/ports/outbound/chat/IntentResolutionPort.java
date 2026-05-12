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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;

import java.util.List;

/**
 * 意图解析端口。
 */
public interface IntentResolutionPort {

    /**
     * 根据改写结果解析子问题意图。
     *
     * @param rewriteResult 改写结果
     * @return 子问题意图列表
     */
    List<SubQuestionIntent> resolve(RewriteResult rewriteResult);

    /**
     * 判断是否为纯系统交互意图。
     *
     * @param nodeScores 意图分数列表
     * @return true 表示纯系统交互
     */
    boolean isSystemOnly(List<IntentScore> intentScores);

    /**
     * 合并子问题意图分组。
     *
     * @param subIntents 子问题意图
     * @return MCP/KB 意图分组
     */
    IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents);

    static IntentResolutionPort empty() {
        return new IntentResolutionPort() {
            @Override
            public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
                return List.of();
            }

            @Override
            public boolean isSystemOnly(List<IntentScore> intentScores) {
                return false;
            }

            @Override
            public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
                return new IntentGroup(List.of(), List.of());
            }
        };
    }
}
