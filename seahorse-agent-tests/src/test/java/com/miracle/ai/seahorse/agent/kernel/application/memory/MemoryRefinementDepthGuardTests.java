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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class MemoryRefinementDepthGuardTests {

    @Test
    void emptyListReturnsDepthZeroAndDoesNotExceed() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(2);
        Assertions.assertEquals(0, guard.currentMaxDepth(List.of()));
        Assertions.assertFalse(guard.exceedsMaxDepth(List.of()));
    }

    @Test
    void nullListReturnsDepthZero() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(2);
        Assertions.assertEquals(0, guard.currentMaxDepth(null));
        Assertions.assertFalse(guard.exceedsMaxDepth(null));
    }

    @Test
    void memoriesWithoutDepthMetadataTreatedAsZero() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(2);
        MemoryRefinementMemory memory = memoryWithMetadata(Map.of());
        Assertions.assertEquals(0, guard.currentMaxDepth(List.of(memory)));
        Assertions.assertFalse(guard.exceedsMaxDepth(List.of(memory)));
    }

    @Test
    void depthOneDoesNotExceedMaxTwo() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(2);
        MemoryRefinementMemory memory = memoryWithMetadata(
                Map.of(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, 1));
        Assertions.assertEquals(1, guard.currentMaxDepth(List.of(memory)));
        Assertions.assertFalse(guard.exceedsMaxDepth(List.of(memory)));
    }

    @Test
    void depthTwoExceedsMaxTwo() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(2);
        MemoryRefinementMemory memory = memoryWithMetadata(
                Map.of(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, 2));
        Assertions.assertEquals(2, guard.currentMaxDepth(List.of(memory)));
        Assertions.assertTrue(guard.exceedsMaxDepth(List.of(memory)));
    }

    @Test
    void mixedMemoriesReturnsMaximumDepth() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(3);
        MemoryRefinementMemory m0 = memoryWithMetadata(Map.of());
        MemoryRefinementMemory m1 = memoryWithMetadata(
                Map.of(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, 1));
        MemoryRefinementMemory m2 = memoryWithMetadata(
                Map.of(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, 2));
        Assertions.assertEquals(2, guard.currentMaxDepth(List.of(m0, m1, m2)));
    }

    @Test
    void stringDepthValueParsedCorrectly() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(3);
        MemoryRefinementMemory memory = memoryWithMetadata(
                Map.of(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, "2"));
        Assertions.assertEquals(2, guard.currentMaxDepth(List.of(memory)));
    }

    @Test
    void invalidStringDepthTreatedAsZero() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(2);
        MemoryRefinementMemory memory = memoryWithMetadata(
                Map.of(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, "invalid"));
        Assertions.assertEquals(0, guard.currentMaxDepth(List.of(memory)));
    }

    @Test
    void zeroMaxDepthFallsBackToDefault() {
        MemoryRefinementDepthGuard guard = new MemoryRefinementDepthGuard(0);
        MemoryRefinementMemory memory = memoryWithMetadata(
                Map.of(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, 2));
        // default is 2, so depth=2 should exceed
        Assertions.assertTrue(guard.exceedsMaxDepth(List.of(memory)));
    }

    @Test
    void incrementedMetadataFromDepthProducesCorrectValue() {
        Map<String, Object> metadata = MemoryRefinementDepthGuard.incrementedMetadata(0);
        Assertions.assertEquals(1, metadata.get(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH));
    }

    @Test
    void incrementedMetadataFromExistingMapProducesCorrectValue() {
        Map<String, Object> existing = Map.of(
                MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH, 1,
                "otherKey", "otherValue");
        Map<String, Object> result = MemoryRefinementDepthGuard.incrementedMetadata(existing);
        Assertions.assertEquals(2, result.get(MemoryRefinementDepthGuard.METADATA_REFINEMENT_DEPTH));
        Assertions.assertEquals("otherValue", result.get("otherKey"));
    }

    private static MemoryRefinementMemory memoryWithMetadata(Map<String, Object> metadata) {
        return new MemoryRefinementMemory(
                "mem-1", "SHORT_TERM", "FACT", "test content",
                "FACT", "", "", "ACTIVE", metadata);
    }
}
