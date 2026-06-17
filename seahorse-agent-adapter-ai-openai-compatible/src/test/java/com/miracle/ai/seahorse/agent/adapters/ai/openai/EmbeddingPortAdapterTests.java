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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingPortAdapterTests {

    @Test
    void shouldRejectEmbeddingWithUnexpectedDimension() {
        EmbeddingModelPort model = (modelId, text) -> List.of(1.0F, 0.0F);
        EmbeddingPortAdapter adapter = new EmbeddingPortAdapter(model, "test-model", 3);

        assertArrayEquals(new float[0], adapter.embed("hello"));
    }

    @Test
    void shouldReturnEmbeddingWhenDimensionMatches() {
        EmbeddingModelPort model = (modelId, text) -> List.of(1.0F, 0.0F);
        EmbeddingPortAdapter adapter = new EmbeddingPortAdapter(model, "test-model", 2);

        assertArrayEquals(new float[] {1.0F, 0.0F}, adapter.embed("hello"));
        assertEquals(2, adapter.dimension());
    }
}
