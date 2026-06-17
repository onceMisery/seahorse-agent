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

package com.miracle.ai.seahorse.agent.adapters.ai.openai;

import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * EmbeddingModelPort 到 EmbeddingPort 的适配器。
 *
 * <p>将现有的 EmbeddingModelPort（返回 List&lt;Float&gt;）转换为
 * EmbeddingPort（返回 float[]），用于 Skill 语义匹配。
 */
public class EmbeddingPortAdapter implements EmbeddingPort {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingPortAdapter.class);

    private final EmbeddingModelPort embeddingModel;
    private final String modelId;
    private final int dimension;

    public EmbeddingPortAdapter(EmbeddingModelPort embeddingModel, String modelId, int dimension) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        this.modelId = modelId != null ? modelId : "text-embedding-3-small";
        this.dimension = dimension > 0 ? dimension : 1536; // 默认 1536 维
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            LOG.debug("Empty text, returning empty vector");
            return new float[0];
        }

        try {
            List<Float> embedding = embeddingModel.embed(modelId, text);
            float[] vector = toFloatArray(embedding);
            if (vector.length > 0 && vector.length != dimension) {
                LOG.error("Embedding dimension mismatch for model {}: expected {}, actual {}",
                        modelId, dimension, vector.length);
                return new float[0];
            }
            return vector;
        } catch (Exception ex) {
            LOG.error("Failed to generate embedding for text: {}", truncate(text, 50), ex);
            return new float[0];
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        return texts.stream()
                .map(this::embed)
                .collect(Collectors.toList());
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelName() {
        return modelId;
    }

    private float[] toFloatArray(List<Float> floatList) {
        if (floatList == null || floatList.isEmpty()) {
            return new float[0];
        }

        float[] result = new float[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            result[i] = floatList.get(i);
        }
        return result;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
