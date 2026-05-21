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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationAppendResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class MemoryAggregationCaptureIntegrationTests {

    @Test
    void shouldKeepLegacyCaptureWhenAggregationDisabled() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingAggregation aggregation = new RecordingAggregation();
        KernelChatPipeline pipeline = pipeline(
                MemoryAggregationPolicy.defaults(),
                workflow,
                aggregation,
                "Assistant noted Java preference");

        pipeline.execute(context("Remember I use Java", new RecordingCallback()));

        Assertions.assertNotNull(workflow.lastCommand);
        Assertions.assertNull(aggregation.lastEvent);
        Assertions.assertEquals("chat-completed", workflow.lastCommand.source());
        Assertions.assertEquals("Remember I use Java", workflow.lastCommand.writeRequest().message().getContent());
    }

    @Test
    void shouldCaptureUserAndAssistantTurnWhenAggregationEnabled() {
        RecordingWorkflow workflow = new RecordingWorkflow();
        RecordingAggregation aggregation = new RecordingAggregation();
        KernelChatPipeline pipeline = pipeline(
                MemoryAggregationPolicy.defaults().withEnabled(true),
                workflow,
                aggregation,
                "Assistant noted Java preference");

        pipeline.execute(context("Remember I use Java", new RecordingCallback()));

        Assertions.assertNull(workflow.lastCommand);
        Assertions.assertNotNull(aggregation.lastEvent);
        Assertions.assertEquals("Remember I use Java", aggregation.lastEvent.userText());
        Assertions.assertEquals("Assistant noted Java preference", aggregation.lastEvent.assistantText());
    }

    private KernelChatPipeline pipeline(MemoryAggregationPolicy policy,
                                        RecordingWorkflow workflow,
                                        RecordingAggregation aggregation,
                                        String assistantText) {
        SimplePorts ports = new SimplePorts(assistantText);
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                ports,
                MemoryEnginePort.noop(),
                workflow,
                aggregation,
                policy,
                QueryOptimizerPort.passthrough(),
                ports,
                ports,
                ports,
                ports);
        ChatResponsePorts responsePorts = new ChatResponsePorts(ports, ports, ports, ports);
        return new KernelChatPipeline(preparationPorts, responsePorts, KernelRagTraceRecorder.noop());
    }

    private StreamChatContext context(String question, StreamCallback callback) {
        return StreamChatContext.builder()
                .question(question)
                .conversationId("conversation-1")
                .taskId("task-1")
                .userId("user-1")
                .callback(callback)
                .build();
    }

    private static final class SimplePorts implements ConversationMemoryPort,
            QueryRewritePort,
            IntentResolutionPort,
            IntentGuidancePort,
            RetrievalContextPort,
            RagPromptPort,
            PromptTemplatePort,
            StreamingChatModelPort,
            StreamTaskPort {

        private final String assistantText;

        private SimplePorts(String assistantText) {
            this.assistantText = assistantText;
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage> loadAndAppend(
                String conversationId,
                String userId,
                com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage message) {
            return List.of();
        }

        @Override
        public RewriteResult rewriteWithSplit(
                String question,
                List<com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage> history) {
            return QueryRewritePort.passthrough().rewriteWithSplit(question, history);
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent> resolve(
                RewriteResult rewriteResult) {
            return List.of(new com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent(
                    rewriteResult.rewrittenQuestion(), List.of()));
        }

        @Override
        public boolean isSystemOnly(List<com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore> intentScores) {
            return false;
        }

        @Override
        public com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup mergeIntentGroup(
                List<com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent> subIntents) {
            return new com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup(List.of(), List.of());
        }

        @Override
        public GuidanceDecision detectAmbiguity(
                String question,
                List<com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent> subIntents) {
            return GuidanceDecision.none();
        }

        @Override
        public RetrievalContext retrieve(
                List<com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent> subIntents,
                int topK) {
            return RetrievalContext.builder().build();
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage> buildStructuredMessages(
                PromptContext context,
                List<com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage> history,
                String question,
                List<String> subQuestions) {
            return List.of(com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage.user(question));
        }

        @Override
        public String load(String path) {
            return "";
        }

        @Override
        public com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle streamChat(
                ChatRequest request,
                StreamCallback callback) {
            callback.onContent(assistantText);
            callback.onComplete();
            return () -> {
            };
        }

        @Override
        public void register(
                String taskId,
                com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender sender,
                java.util.function.Supplier<com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload>
                        onCancelSupplier) {
        }

        @Override
        public void bindHandle(
                String taskId,
                com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle handle) {
        }

        @Override
        public boolean isCancelled(String taskId) {
            return false;
        }

        @Override
        public void cancel(String taskId) {
        }

        @Override
        public void unregister(String taskId) {
        }
    }

    private static final class RecordingWorkflow implements MemoryIngestionWorkflowPort {

        private MemoryIngestionCommand lastCommand;

        @Override
        public MemoryIngestionResult ingest(MemoryIngestionCommand command) {
            lastCommand = command;
            return MemoryIngestionResult.accepted(List.of("legacy"));
        }
    }

    private static final class RecordingAggregation implements MemoryAggregationServicePort {

        private MemoryTurnEvent lastEvent;

        @Override
        public MemoryAggregationAppendResult appendTurn(MemoryTurnEvent event) {
            lastEvent = event;
            return MemoryAggregationAppendResult.pending(new MemoryBufferState(
                    event.tenantId(),
                    event.userId(),
                    event.conversationId(),
                    event.sessionId(),
                    1,
                    event.estimatedTokens(),
                    Instant.now(),
                    false,
                    null));
        }

        @Override
        public MemoryIngestionResult flushReady(
                String sessionId,
                String tenantId,
                MemoryFlushTrigger trigger,
                Instant now) {
            return MemoryIngestionResult.ignored("not_used");
        }
    }

    private static final class RecordingCallback implements StreamCallback {

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}
