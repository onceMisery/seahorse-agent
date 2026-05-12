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

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 原生文本分块节点。
 *
 * <p>默认按旧 chunker 行为在分块后立即执行 embedding；若 pipeline 显式使用 embedder 节点，
 * 可通过 settings.embed=false 关闭该行为。
 */
public class ChunkerNodeFeature implements IngestionNodeFeature {

    private static final String NODE_TYPE = "chunker";
    private static final String STRATEGY_FIXED_SIZE = "fixed_size";
    private static final String STRATEGY_STRUCTURE_AWARE = "structure_aware";
    private static final String KEY_STRATEGY = "strategy";
    private static final String KEY_CHUNK_SIZE = "chunkSize";
    private static final String KEY_OVERLAP_SIZE = "overlapSize";
    private static final String KEY_EMBED = "embed";
    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_OVERLAP_SIZE = 128;
    private static final int SINGLE_CHUNK_SIZE = -1;

    private final EmbedderNodeFeature embedderNodeFeature;

    public ChunkerNodeFeature(EmbeddingModelPort embeddingModelPort) {
        this.embedderNodeFeature = new EmbedderNodeFeature(embeddingModelPort);
    }

    @Override
    public String name() {
        return NODE_TYPE;
    }

    @Override
    public String nodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        IngestionContext safeContext = Objects.requireNonNull(context, "context must not be null");
        String text = resolveText(safeContext);
        if (!hasText(text)) {
            return NodeResult.fail(new IllegalArgumentException("chunk text must not be blank"));
        }
        try {
            ChunkerSettings settings = parseSettings(config);
            List<VectorChunk> chunks = chunk(text, settings);
            safeContext.setChunks(chunks);
            if (settings.embed()) {
                embedderNodeFeature.embedChunks(chunks, settings.modelId());
            }
            return NodeResult.ok("chunked " + chunks.size() + " chunks");
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    private List<VectorChunk> chunk(String text, ChunkerSettings settings) {
        if (STRATEGY_STRUCTURE_AWARE.equals(settings.strategy())) {
            return structureAwareChunk(text, settings);
        }
        return fixedSizeChunk(text, settings);
    }

    private List<VectorChunk> fixedSizeChunk(String text, ChunkerSettings settings) {
        String normalized = normalizeText(text);
        if (settings.chunkSize() == SINGLE_CHUNK_SIZE) {
            return List.of(chunk(0, normalized));
        }
        int chunkSize = Math.max(1, settings.chunkSize());
        int overlap = Math.max(0, Math.min(settings.overlapSize(), chunkSize - 1));
        List<VectorChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            String content = normalized.substring(start, end);
            if (hasText(content)) {
                chunks.add(chunk(index, content));
                index++;
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }

    private List<VectorChunk> structureAwareChunk(String text, ChunkerSettings settings) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> blocks = splitBlocks(normalized);
        if (blocks.isEmpty()) {
            return List.of(chunk(0, normalized));
        }
        return packBlocks(blocks, Math.max(1, settings.chunkSize()), Math.max(0, settings.overlapSize()));
    }

    private List<String> splitBlocks(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        List<String> blocks = new ArrayList<>();
        for (String part : parts) {
            if (hasText(part)) {
                blocks.add(part.strip());
            }
        }
        return blocks;
    }

    private List<VectorChunk> packBlocks(List<String> blocks, int targetSize, int overlap) {
        List<VectorChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (shouldFlush(current, block, targetSize)) {
                appendPackedChunk(chunks, current, overlap);
            }
            appendBlock(current, block);
        }
        appendPackedChunk(chunks, current, overlap);
        return chunks;
    }

    private boolean shouldFlush(StringBuilder current, String block, int targetSize) {
        if (current.isEmpty()) {
            return false;
        }
        return current.length() + block.length() + 2 > targetSize;
    }

    private void appendPackedChunk(List<VectorChunk> chunks, StringBuilder current, int overlap) {
        if (current.isEmpty()) {
            return;
        }
        int index = chunks.size();
        String content = current.toString();
        chunks.add(chunk(index, content));
        current.setLength(0);
        if (overlap > 0) {
            current.append(tail(content, overlap));
        }
    }

    private void appendBlock(StringBuilder current, String block) {
        if (!current.isEmpty()) {
            current.append("\n\n");
        }
        current.append(block);
    }

    private String tail(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(value.length() - maxChars);
    }

    private VectorChunk chunk(int index, String content) {
        return VectorChunk.builder()
                .chunkId(UUID.randomUUID().toString())
                .index(index)
                .content(content)
                .build();
    }

    private ChunkerSettings parseSettings(NodeConfig config) {
        JsonNode settings = config == null ? null : config.getSettings();
        String strategy = normalizeStrategy(text(settings, KEY_STRATEGY));
        int chunkSize = intValue(settings, KEY_CHUNK_SIZE, DEFAULT_CHUNK_SIZE);
        int overlapSize = intValue(settings, KEY_OVERLAP_SIZE, DEFAULT_OVERLAP_SIZE);
        boolean embed = boolValue(settings, KEY_EMBED, true);
        String modelId = firstText(text(settings, "modelId"), text(settings, "embeddingModelId"));
        return new ChunkerSettings(strategy, chunkSize, overlapSize, embed, modelId);
    }

    private String resolveText(IngestionContext context) {
        if (hasText(context.getEnhancedText())) {
            return context.getEnhancedText();
        }
        return context.getRawText();
    }

    private String normalizeText(String text) {
        return Objects.requireNonNullElse(text, "").replace("\r", "");
    }

    private String normalizeStrategy(String strategy) {
        String normalized = Objects.requireNonNullElse(strategy, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        if (STRATEGY_STRUCTURE_AWARE.equals(normalized)) {
            return STRATEGY_STRUCTURE_AWARE;
        }
        return STRATEGY_FIXED_SIZE;
    }

    private int intValue(JsonNode settings, String key, int defaultValue) {
        JsonNode node = node(settings, key);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        return node.asInt(defaultValue);
    }

    private boolean boolValue(JsonNode settings, String key, boolean defaultValue) {
        JsonNode node = node(settings, key);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        return node.asBoolean(defaultValue);
    }

    private String text(JsonNode settings, String key) {
        JsonNode node = node(settings, key);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private JsonNode node(JsonNode settings, String key) {
        if (settings == null || settings.isNull()) {
            return null;
        }
        return settings.get(key);
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ChunkerSettings(String strategy, int chunkSize, int overlapSize, boolean embed, String modelId) {
    }
}
