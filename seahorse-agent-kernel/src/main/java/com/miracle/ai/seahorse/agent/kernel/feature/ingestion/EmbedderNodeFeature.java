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

import java.util.List;
import java.util.Objects;

/**
 * 原生向量化节点。
 *
 * <p>该节点只负责把已分块文本转换为 embedding，不写数据库或向量库。
 */
public class EmbedderNodeFeature implements IngestionNodeFeature {

    public static final String NODE_TYPE = "embedder";
    private static final String KEY_MODEL_ID = "modelId";
    private static final String KEY_EMBEDDING_MODEL_ID = "embeddingModelId";

    private final EmbeddingModelPort embeddingModelPort;

    public EmbedderNodeFeature(EmbeddingModelPort embeddingModelPort) {
        this.embeddingModelPort = Objects.requireNonNullElse(embeddingModelPort, EmbeddingModelPort.noop());
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
        List<VectorChunk> chunks = Objects.requireNonNullElse(safeContext.getChunks(), List.of());
        if (chunks.isEmpty()) {
            return NodeResult.fail(new IllegalArgumentException("chunks must not be empty"));
        }
        try {
            embedChunks(chunks, resolveModelId(safeContext, config));
            return NodeResult.ok("embedded " + chunks.size() + " chunks");
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    void embedChunks(List<VectorChunk> chunks, String modelId) {
        for (VectorChunk chunk : chunks) {
            VectorChunk safeChunk = Objects.requireNonNull(chunk, "chunk must not be null");
            String content = Objects.requireNonNullElse(safeChunk.getContent(), "");
            safeChunk.setEmbedding(toArray(embeddingModelPort.embed(modelId, content)));
        }
    }

    private String resolveModelId(IngestionContext context, NodeConfig config) {
        JsonNode settings = config == null ? null : config.getSettings();
        String configured = firstText(setting(settings, KEY_MODEL_ID), setting(settings, KEY_EMBEDDING_MODEL_ID));
        if (hasText(configured)) {
            return configured;
        }
        if (context.getMetadata() == null) {
            return "";
        }
        Object value = context.getMetadata().get(KEY_EMBEDDING_MODEL_ID);
        return value == null ? "" : String.valueOf(value);
    }

    private float[] toArray(List<Float> values) {
        List<Float> safeValues = Objects.requireNonNullElse(values, List.of());
        float[] array = new float[safeValues.size()];
        for (int index = 0; index < safeValues.size(); index++) {
            array[index] = safeValues.get(index);
        }
        return array;
    }

    private String setting(JsonNode settings, String key) {
        if (settings == null || settings.isNull()) {
            return "";
        }
        JsonNode node = settings.get(key);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
