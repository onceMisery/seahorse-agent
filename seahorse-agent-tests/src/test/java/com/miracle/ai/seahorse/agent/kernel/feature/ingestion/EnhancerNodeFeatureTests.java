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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EnhancerNodeFeatureTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldApplyDocumentEnhancementTasksWithoutLegacyPromptManager() {
        AtomicInteger calls = new AtomicInteger();
        EnhancerNodeFeature feature = new EnhancerNodeFeature((request, modelId) -> switch (calls.getAndIncrement()) {
            case 0 -> "整理后的文本";
            case 1 -> "```json\n[\"合同\", \"发票\"]\n```";
            case 2 -> "{\"department\":\"finance\"}";
            default -> "";
        }, taskType -> "system:" + taskType);
        NodeConfig config = NodeConfig.builder()
                .nodeType("enhancer")
                .settings(OBJECT_MAPPER.valueToTree(Map.of(
                        "modelId", "chat-a",
                        "tasks", List.of(
                                Map.of("type", "context_enhance"),
                                Map.of("type", "keywords"),
                                Map.of("type", "metadata")))))
                .build();
        IngestionContext context = IngestionContext.builder().rawText("原始文本").build();

        NodeResult result = feature.execute(context, config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getEnhancedText()).isEqualTo("整理后的文本");
        assertThat(context.getKeywords()).containsExactly("合同", "发票");
        assertThat(context.getMetadata()).containsEntry("department", "finance");
    }
}
