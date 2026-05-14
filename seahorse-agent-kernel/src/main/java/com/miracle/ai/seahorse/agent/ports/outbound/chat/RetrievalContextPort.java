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

import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;

import java.util.List;

/**
 * RAG 检索上下文端口。
 */
public interface RetrievalContextPort {

    /**
     * 检索并合并 KB/MCP 上下文。
     *
     * @param subIntents 子问题意图
     * @param topK       期望返回数量
     * @return 检索上下文
     */
    RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK);

    /**
     * 带 Trace run 的检索入口，默认回落到旧契约，避免外部实现被迫改造。
     */
    default RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK, TraceRunScope traceRunScope) {
        return retrieve(subIntents, topK);
    }
}
