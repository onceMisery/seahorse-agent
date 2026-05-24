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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class MemoryReviewApplyClassificationBuilderTests {

    private final MemoryReviewApplyClassificationBuilder builder = new MemoryReviewApplyClassificationBuilder();

    @Test
    void buildReturnsNullForNullDirective() {
        Assertions.assertNull(builder.build(null, "content"));
    }

    @Test
    void buildPreservesReviewApplyMetadataAndDefaultsTargetKind() {
        MemoryReviewApplyDirective directive = new MemoryReviewApplyDirective(
                MemoryIngestionAction.UPDATE,
                "SEMANTIC",
                "",
                "project.memory.layers",
                0.91D,
                0.70D,
                0.80D,
                0.10D,
                List.of("msg-original"),
                Map.of("reviewReason", "manual_fix"));

        MemoryClassificationResult classification = builder.build(directive, "project Seahorse uses four layers");

        Assertions.assertEquals(MemoryIngestionAction.ADD, classification.action());
        Assertions.assertEquals("FACT", classification.decision().type());
        Assertions.assertEquals("project Seahorse uses four layers", classification.decision().content());
        Assertions.assertEquals("FACT", classification.refinedDelta().targetKind());
        Assertions.assertEquals("project.memory.layers", classification.refinedDelta().targetKey());
        Assertions.assertEquals(MemoryIngestionAction.UPDATE, classification.refinedDelta().action());
        Assertions.assertEquals("memory_review_applied", classification.refinedDelta().reason());
        Assertions.assertEquals("review_applied", classification.refinedDelta().metadata().get("status"));
        Assertions.assertEquals("UPDATE", classification.refinedDelta().metadata().get("reviewRequestedAction"));
        Assertions.assertEquals(MemoryLayer.SEMANTIC.name(), classification.refinedDelta().metadata().get("targetLayer"));
        Assertions.assertEquals(List.of("msg-original"),
                classification.refinedDelta().metadata().get("sourceMessageIds"));
        Assertions.assertEquals("manual_fix", classification.refinedDelta().metadata().get("reviewReason"));
    }

    @Test
    void validateTargetLayerRejectsWorkingLayer() {
        MemoryReviewApplyDirective directive = new MemoryReviewApplyDirective(
                MemoryIngestionAction.ADD,
                "WORKING",
                "PROJECT_FACT",
                "project.transient",
                0.91D,
                0.70D,
                0.80D,
                0.10D,
                List.of("msg-original"),
                Map.of());

        MemorySchemaValidationResult result = builder.validateTargetLayer(directive);

        Assertions.assertFalse(result.valid());
        Assertions.assertEquals("invalid_review_target_layer", result.reason());
    }

    @Test
    void validateTargetLayerAcceptsNonWorkingLayers() {
        MemorySchemaValidationResult result = builder.validateTargetLayer(new MemoryReviewApplyDirective(
                MemoryIngestionAction.ADD,
                "SHORT_TERM",
                "FACT",
                "fact-1",
                0.91D,
                0.70D,
                0.80D,
                0.10D,
                List.of(),
                Map.of()));

        Assertions.assertTrue(result.valid());
    }
}
