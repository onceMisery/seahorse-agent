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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.RefinedMemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmMemoryRefinerAdapterTests {

    @Test
    void shouldParseValidAddOperation() {
        CapturingChatModelPort chatModelPort = new CapturingChatModelPort("""
                {
                  "refined": true,
                  "reason": "stable preference",
                  "operations": [
                    {
                      "action": "ADD",
                      "targetKind": "PREFERENCE",
                      "targetKey": "language",
                      "content": "User prefers Java examples.",
                      "confidence": 0.82,
                      "importance": 0.72,
                      "valueScore": 0.9,
                      "riskScore": 0.1,
                      "sourceMessageIds": ["m1"],
                      "signals": ["explicit_preference"],
                      "metadata": {"profileSlot": "preference.language"}
                    }
                  ],
                  "metadata": {"model": "test"}
                }
                """);
        LlmMemoryRefinerAdapter adapter = new LlmMemoryRefinerAdapter(
                chatModelPort, PromptTemplatePort.empty(), new ObjectMapper());

        MemoryRefinementResult result = adapter.refine(request("Please answer with Java examples."));

        assertThat(result.refined()).isTrue();
        assertThat(result.reason()).isEqualTo("stable preference");
        assertThat(result.metadata()).containsEntry("model", "test");
        assertThat(result.operations()).hasSize(1);
        RefinedMemoryOperation operation = result.operations().get(0);
        assertThat(operation.action()).isEqualTo(MemoryIngestionAction.ADD);
        assertThat(operation.targetKind()).isEqualTo("PREFERENCE");
        assertThat(operation.targetKey()).isEqualTo("language");
        assertThat(operation.content()).isEqualTo("User prefers Java examples.");
        assertThat(operation.confidence()).isEqualTo(0.82D);
        assertThat(operation.sourceMessageIds()).containsExactly("m1");
        assertThat(operation.signals()).containsExactly("explicit_preference");
        assertThat(operation.metadata()).containsEntry("profileSlot", "preference.language");
        assertThat(chatModelPort.lastRequest.get()).isNotNull();
        assertThat(chatModelPort.lastRequest.get().getMessages().get(0).getContent())
                .contains("Please answer with Java examples.");
    }

    @Test
    void shouldExtractFencedJsonAndParseReviewOperation() {
        LlmMemoryRefinerAdapter adapter = new LlmMemoryRefinerAdapter(
                new CapturingChatModelPort("""
                        ```json
                        {
                          "refined": true,
                          "reason": "sensitive update",
                          "operations": [
                            {
                              "action": "REVIEW",
                              "targetKind": "PROFILE",
                              "targetKey": "occupation",
                              "content": "User may have changed occupation.",
                              "confidence": 0.61,
                              "importance": 0.7,
                              "valueScore": 0.75,
                              "riskScore": 0.64,
                              "sourceMessageIds": ["m2"],
                              "signals": ["possible_conflict"],
                              "metadata": {"requiresHumanReview": true}
                            }
                          ]
                        }
                        ```
                        """),
                PromptTemplatePort.empty(),
                new ObjectMapper());

        MemoryRefinementResult result = adapter.refine(request("I am no longer a designer."));

        assertThat(result.refined()).isTrue();
        assertThat(result.operations()).singleElement()
                .extracting(RefinedMemoryOperation::action)
                .isEqualTo(MemoryIngestionAction.REVIEW);
    }

    @Test
    void shouldParseMultipleOperationsAndPreserveTargetLayerMetadata() {
        LlmMemoryRefinerAdapter adapter = new LlmMemoryRefinerAdapter(
                new CapturingChatModelPort("""
                        {
                          "refined": true,
                          "reason": "multi delta",
                          "operations": [
                            {
                              "action": "ADD",
                              "targetKind": "PROJECT_FACT",
                              "targetKey": "project.runtime",
                              "content": "User's project runtime is Java 17.",
                              "confidence": 0.91,
                              "importance": 0.73,
                              "valueScore": 0.8,
                              "riskScore": 0.1,
                              "sourceMessageIds": ["m1"],
                              "signals": ["explicit_fact"],
                              "metadata": {"targetLayer": "LONG_TERM"}
                            },
                            {
                              "action": "ADD",
                              "targetKind": "PREFERENCE",
                              "targetKey": "preferences.response_style",
                              "content": "User prefers implementation-first answers.",
                              "confidence": 0.88,
                              "importance": 0.66,
                              "valueScore": 0.7,
                              "riskScore": 0.1,
                              "sourceMessageIds": ["m1"],
                              "signals": ["explicit_preference"],
                              "metadata": {"targetLayer": "SHORT_TERM"}
                            }
                          ],
                          "metadata": {"model": "test"}
                        }
                        """),
                PromptTemplatePort.empty(),
                new ObjectMapper());

        MemoryRefinementResult result = adapter.refine(request("Remember Java 17 and implementation-first answers."));

        assertThat(result.refined()).isTrue();
        assertThat(result.operations()).hasSize(2);
        assertThat(result.operations()).extracting(RefinedMemoryOperation::targetKey)
                .containsExactly("project.runtime", "preferences.response_style");
        assertThat(result.operations().get(0).metadata()).containsEntry("targetLayer", "LONG_TERM");
        assertThat(result.operations().get(1).metadata()).containsEntry("targetLayer", "SHORT_TERM");
    }

    @Test
    void shouldReturnEmptyResultWhenModelOutputIsInvalidJson() {
        LlmMemoryRefinerAdapter adapter = new LlmMemoryRefinerAdapter(
                new CapturingChatModelPort("not json"),
                PromptTemplatePort.empty(),
                new ObjectMapper());

        MemoryRefinementResult result = adapter.refine(request("remember this"));

        assertThat(result.refined()).isFalse();
        assertThat(result.operations()).isEmpty();
        assertThat(result.reason()).isEqualTo("llm_refiner_parse_failed");
    }

    @Test
    void shouldIgnoreUnsupportedActionsWithoutFailingThePipeline() {
        LlmMemoryRefinerAdapter adapter = new LlmMemoryRefinerAdapter(
                new CapturingChatModelPort("""
                        {
                          "refined": true,
                          "reason": "unsupported action",
                          "operations": [
                            {"action": "MERGE", "content": "bad", "confidence": 0.9}
                          ]
                        }
                        """),
                PromptTemplatePort.empty(),
                new ObjectMapper());

        MemoryRefinementResult result = adapter.refine(request("merge this"));

        assertThat(result.refined()).isFalse();
        assertThat(result.operations()).isEmpty();
        assertThat(result.reason()).isEqualTo("llm_refiner_no_supported_operations");
    }

    @Test
    void shouldInjectReviewFeedbackSamplesIntoPromptWhenAvailable() {
        CapturingChatModelPort chatModelPort = new CapturingChatModelPort("""
                {
                  "refined": true,
                  "reason": "guided by feedback",
                  "operations": [
                    {"action": "IGNORE", "content": "", "confidence": 0.9}
                  ]
                }
                """);
        RecordingFeedbackRepository feedbackRepository = new RecordingFeedbackRepository();
        feedbackRepository.samples = List.of(new MemoryReviewFeedbackSample(
                "sample-1",
                "review-1",
                "op-1",
                "tenant-1",
                "user-1",
                "REVIEW",
                MemoryReviewStatus.REJECTED,
                "auditor",
                "not a durable fact",
                "SHORT_TERM",
                "FACT",
                "project.noise",
                "User said the build is annoying.",
                "",
                Map.of(),
                Map.of(),
                List.of("message-1"),
                "",
                "",
                Instant.EPOCH));
        LlmMemoryRefinerAdapter adapter = new LlmMemoryRefinerAdapter(
                chatModelPort,
                PromptTemplatePort.empty(),
                new ObjectMapper(),
                feedbackRepository,
                2);

        adapter.refine(request("The build is annoying again."));

        String prompt = chatModelPort.lastRequest.get().getMessages().get(0).getContent();
        assertThat(prompt).contains("Human review feedback examples:");
        assertThat(prompt).contains("not a durable fact");
        assertThat(prompt).contains("User said the build is annoying.");
        assertThat(feedbackRepository.lastQuery.tenantId()).isEqualTo("tenant-1");
        assertThat(feedbackRepository.lastQuery.userId()).isEqualTo("user-1");
        assertThat(feedbackRepository.lastQuery.limit()).isEqualTo(2);
    }

    @Test
    void shouldInjectCurrentExistingMemoriesIntoPromptWhenAvailable() {
        CapturingChatModelPort chatModelPort = new CapturingChatModelPort("""
                {
                  "refined": true,
                  "reason": "uses read mask",
                  "operations": []
                }
                """);
        LlmMemoryRefinerAdapter adapter = new LlmMemoryRefinerAdapter(
                chatModelPort,
                PromptTemplatePort.empty(),
                new ObjectMapper());

        adapter.refine(new MemoryRefinementRequest(
                "op-1",
                "tenant-1",
                "chat",
                "user-1",
                "conversation-1",
                "message-1",
                "Actually the project moved to OceanBase.",
                MemoryIngestionAction.ADD,
                "FACT",
                "rule_based",
                Map.of("valueScore", 0.6D),
                List.of(new MemoryRefinementMemory(
                        "ltm-existing",
                        "LONG_TERM",
                        "PROJECT_FACT",
                        "User's project currently uses MySQL.",
                        "PROJECT_FACT",
                        "project.database",
                        "project.database:old",
                        "ACTIVE",
                        Map.of("confidenceLevel", 0.9D)))));

        String prompt = chatModelPort.lastRequest.get().getMessages().get(0).getContent();
        assertThat(prompt).contains("Current existing memories:");
        assertThat(prompt).contains("ltm-existing");
        assertThat(prompt).contains("project.database");
        assertThat(prompt).contains("User's project currently uses MySQL.");
    }

    private static MemoryRefinementRequest request(String content) {
        return new MemoryRefinementRequest(
                "op-1",
                "tenant-1",
                "chat",
                "user-1",
                "conversation-1",
                "message-1",
                content,
                MemoryIngestionAction.ADD,
                "FACT",
                "rule_based",
                Map.of("valueScore", 0.6D));
    }

    private static final class CapturingChatModelPort implements ChatModelPort {

        private final String response;
        private final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();

        private CapturingChatModelPort(String response) {
            this.response = response;
        }

        @Override
        public String chat(ChatRequest request, String modelId) {
            lastRequest.set(request);
            return response;
        }
    }

    private static final class RecordingFeedbackRepository implements MemoryReviewFeedbackRepositoryPort {

        private List<MemoryReviewFeedbackSample> samples = List.of();
        private MemoryReviewFeedbackQuery lastQuery;

        @Override
        public void save(MemoryReviewFeedbackSample sample) {
        }

        @Override
        public List<MemoryReviewFeedbackSample> listByCandidate(String candidateId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryReviewFeedbackSample> listSamples(MemoryReviewFeedbackQuery query) {
            lastQuery = query;
            return samples;
        }
    }
}
