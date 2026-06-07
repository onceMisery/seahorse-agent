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

import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock Embedding Adapter for E2E Testing.
 */
public class MockEmbeddingAdapter implements EmbeddingModelPort {

    private static final int DIMENSION = 768;

    @Override
    public List<Float> embed(String modelId, String text) {
        if (text == null || text.isEmpty()) {
            return generateZeroVector();
        }

        int hash = text.hashCode();
        List<Float> vector = new ArrayList<>(DIMENSION);

        for (int i = 0; i < DIMENSION; i++) {
            float value = (float) Math.sin((hash + i * 31) / 1000.0) * 0.1f;
            vector.add(value);
        }

        return normalizeVector(vector);
    }

    private List<Float> generateZeroVector() {
        List<Float> vector = new ArrayList<>(DIMENSION);
        for (int i = 0; i < DIMENSION; i++) {
            vector.add(0.01f);
        }
        return vector;
    }

    private List<Float> normalizeVector(List<Float> vector) {
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0.0001f) {
            List<Float> normalized = new ArrayList<>(vector.size());
            for (float v : vector) {
                normalized.add(v / norm);
            }
            return normalized;
        }
        return vector;
    }
}
