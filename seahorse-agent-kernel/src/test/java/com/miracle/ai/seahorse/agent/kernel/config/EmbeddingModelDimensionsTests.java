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

package com.miracle.ai.seahorse.agent.kernel.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EmbeddingModelDimensionsTests {

    @Test
    void shouldResolveDefaultFullEmbeddingModelDimension() {
        int dimension = EmbeddingModelDimensions.resolveOrThrow("", "nomic-embed-text", "");

        Assertions.assertEquals(768, dimension);
    }

    @Test
    void shouldResolveLocalMockEmbeddingModelDimension() {
        int dimension = EmbeddingModelDimensions.resolveOrThrow("", "local-mock-embedding", "");

        Assertions.assertEquals(768, dimension);
    }

    @Test
    void shouldResolveBgeM3DimensionFromModelName() {
        int dimension = EmbeddingModelDimensions.resolveOrThrow("", "bge-m3", "");

        Assertions.assertEquals(1024, dimension);
    }

    @Test
    void shouldNormalizeProviderPrefixAndModelTag() {
        int dimension = EmbeddingModelDimensions.resolveOrThrow("", "ollama/bge-m3:latest", "");

        Assertions.assertEquals(1024, dimension);
    }

    @Test
    void shouldAllowConfiguredDimensionForCustomEmbeddingModel() {
        int dimension = EmbeddingModelDimensions.resolveOrThrow(
                "", "acme-embed-v2", "acme-embed-v2=2048");

        Assertions.assertEquals(2048, dimension);
    }

    @Test
    void shouldPreferExplicitVectorDimensionOverModelDimension() {
        int dimension = EmbeddingModelDimensions.resolveOrThrow(
                "512", "text-embedding-3-large", "");

        Assertions.assertEquals(512, dimension);
    }

    @Test
    void shouldFailFastForUnknownEmbeddingModelWithoutDimension() {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> EmbeddingModelDimensions.resolveOrThrow("", "unknown-embedder", ""));

        Assertions.assertTrue(error.getMessage().contains("unknown-embedder"));
        Assertions.assertTrue(error.getMessage().contains("embedding-model-dimensions"));
    }
}
