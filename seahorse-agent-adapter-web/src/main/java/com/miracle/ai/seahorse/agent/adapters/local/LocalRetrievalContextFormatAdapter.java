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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地检索上下文格式化适配器。
 *
 * <p>格式保持简单稳定，只输出命中文本和可选得分，避免 prompt 体积失控。
 */
public class LocalRetrievalContextFormatAdapter implements RetrievalContextFormatPort {

    private static final String SECTION_SEPARATOR = "\n";
    private static final int DEFAULT_TOP_K = 10;

    @Override
    public String formatKbContext(List<IntentScore> kbIntents,
                                  Map<String, List<RetrievedChunk>> intentChunks,
                                  int topK) {
        List<RetrievedChunk> chunks = flattenChunks(intentChunks);
        if (chunks.isEmpty()) {
            return "";
        }
        int limit = topK > 0 ? topK : DEFAULT_TOP_K;
        AtomicInteger index = new AtomicInteger(1);
        return chunks.stream()
                .filter(Objects::nonNull)
                .filter(chunk -> hasText(chunk.getText()))
                .sorted(Comparator.comparing(this::safeScore).reversed())
                .limit(limit)
                .map(chunk -> formatChunk(index.getAndIncrement(), chunk))
                .reduce((left, right) -> left + SECTION_SEPARATOR + right)
                .orElse("");
    }

    @Override
    public String formatMcpContext(List<McpToolExecutionResult> results, List<IntentScore> mcpIntents) {
        List<McpToolExecutionResult> safeResults = Objects.requireNonNullElse(results, List.of());
        AtomicInteger index = new AtomicInteger(1);
        return safeResults.stream()
                .filter(Objects::nonNull)
                .filter(McpToolExecutionResult::success)
                .filter(result -> hasText(result.content()))
                .map(result -> formatToolResult(index.getAndIncrement(), result))
                .reduce((left, right) -> left + SECTION_SEPARATOR + right)
                .orElse("");
    }

    private List<RetrievedChunk> flattenChunks(Map<String, List<RetrievedChunk>> intentChunks) {
        return Objects.requireNonNullElse(intentChunks, Map.<String, List<RetrievedChunk>>of())
                .values()
                .stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String formatChunk(int index, RetrievedChunk chunk) {
        String scoreText = chunk.getScore() == null ? "" : " score=" + chunk.getScore();
        return "[" + index + scoreText + "] " + chunk.getText().trim();
    }

    private String formatToolResult(int index, McpToolExecutionResult result) {
        String toolId = Objects.requireNonNullElse(result.toolId(), "mcp-tool");
        return "[" + index + " tool=" + toolId + "] " + result.content().trim();
    }

    private Float safeScore(RetrievedChunk chunk) {
        Float score = chunk == null ? null : chunk.getScore();
        return score == null ? 0F : score;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
