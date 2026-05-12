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

package com.miracle.ai.seahorse.agent.ports.outbound.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * 检索上下文格式化端口。
 * <p>
 * L1 检索内核负责组织 KB 和 MCP 结果，具体 Prompt 上下文格式由该端口的 L3 adapter 提供。
 */
public interface RetrievalContextFormatPort {

    String formatKbContext(List<IntentScore> kbIntents, Map<String, List<RetrievedChunk>> intentChunks, int topK);

    String formatMcpContext(List<McpToolExecutionResult> results, List<IntentScore> mcpIntents);

    static RetrievalContextFormatPort noop() {
        return new RetrievalContextFormatPort() {
            @Override
            public String formatKbContext(List<IntentScore> kbIntents,
                                          Map<String, List<RetrievedChunk>> intentChunks,
                                          int topK) {
                return "";
            }

            @Override
            public String formatMcpContext(List<McpToolExecutionResult> results, List<IntentScore> mcpIntents) {
                return "";
            }
        };
    }
}
