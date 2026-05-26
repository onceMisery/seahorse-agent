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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.factory;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTemplateTests {

    @Test
    void shouldRejectRequestedToolsOutsideTemplateBoundary() {
        AgentTemplate template = knowledgeTemplate();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> template.validateRequestedTools(List.of("search", "email-send")));

        assertEquals("Requested tools must be a subset of template tools", error.getMessage());
    }

    @Test
    void shouldRejectRiskAboveTemplateCap() {
        AgentTemplate template = knowledgeTemplate();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> template.validateRequestedRisk(AgentRiskLevel.HIGH));

        assertEquals("Requested risk level exceeds template risk cap", error.getMessage());
    }

    @Test
    void shouldMergeInstructionsOverlayWithoutReplacingTemplateBoundary() {
        AgentTemplate template = knowledgeTemplate();

        String merged = template.mergeInstructionsOverlay("Only answer HR policy questions.");

        assertTrue(merged.contains("Answer from approved enterprise knowledge."));
        assertTrue(merged.contains("Only answer HR policy questions."));
    }

    private static AgentTemplate knowledgeTemplate() {
        return new AgentTemplate(
                AgentTemplateId.KNOWLEDGE_ASSISTANT,
                AgentTemplateStatus.ENABLED,
                "Knowledge Assistant",
                "Enterprise knowledge assistant",
                AgentType.ASSISTANT,
                AgentRiskLevel.LOW,
                List.of("search", "memory-read"),
                "Answer from approved enterprise knowledge.",
                "{\"grounded\":true}");
    }
}
