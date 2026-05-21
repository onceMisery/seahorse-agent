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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SearchKnowledgeBaseToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "search_knowledge_base";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Search Knowledge Base",
            "Search Seahorse knowledge bases through the existing RAG retrieval pipeline.",
            """
                    {"type":"object","required":["query"],"properties":{"query":{"type":"string","minLength":1},"topK":{"type":"integer","minimum":1,"maximum":20},"searchMode":{"type":"string","enum":["AUTO","VECTOR","KEYWORD","HYBRID"]},"rewriteHint":{"type":"string"}}}
                    """);

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;
    private static final int MAX_CHARS_PER_CHUNK = 1_200;

    private final KernelRetrievalEngine retrievalEngine;
    private final AgentToolJsonSupport jsonSupport;

    public SearchKnowledgeBaseToolPortAdapter(KernelRetrievalEngine retrievalEngine,
                                              AgentToolJsonSupport jsonSupport) {
        this.retrievalEngine = Objects.requireNonNull(retrievalEngine, "retrievalEngine must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String query = jsonSupport.string(arguments, "query");
            if (query.isBlank()) {
                return ToolInvocationResult.failed("query is required");
            }
            int topK = jsonSupport.boundedInt(arguments, "topK", DEFAULT_TOP_K, 1, MAX_TOP_K);
            List<RetrievedChunk> chunks = retrievalEngine.retrieveKnowledgeChannels(
                    List.of(new SubQuestionIntent(query, List.of())), topK);
            return ToolInvocationResult.ok(jsonSupport.write(observation(query, topK, chunks)));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("search_knowledge_base failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private Map<String, Object> observation(String query, int topK, List<RetrievedChunk> chunks) {
        List<RetrievedChunk> safeChunks = Objects.requireNonNullElse(chunks, List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("topK", topK);
        result.put("resultCount", safeChunks.size());
        result.put("chunks", safeChunks.stream().limit(topK).map(this::chunk).toList());
        result.put("qualitySignals", Map.of(
                "empty", safeChunks.isEmpty(),
                "maxScore", safeChunks.stream()
                        .map(RetrievedChunk::getScore)
                        .filter(Objects::nonNull)
                        .max(Float::compareTo)
                        .orElse(0F)));
        return result;
    }

    private Map<String, Object> chunk(RetrievedChunk chunk) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("chunkId", Objects.requireNonNullElse(chunk.getId(), ""));
        item.put("documentId", Objects.requireNonNullElse(chunk.getDocId(), ""));
        item.put("knowledgeBaseId", Objects.requireNonNullElse(chunk.getKbId(), ""));
        item.put("score", chunk.getScore());
        item.put("content", truncate(Objects.requireNonNullElse(chunk.getText(), "")));
        item.put("metadata", Objects.requireNonNullElse(chunk.getMetadata(), Map.of()));
        return item;
    }

    private String truncate(String value) {
        if (value.length() <= MAX_CHARS_PER_CHUNK) {
            return value;
        }
        return value.substring(0, MAX_CHARS_PER_CHUNK) + "...[truncated]";
    }
}
