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

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelChatInboundTraceTests {

    @Test
    void ragTraceFinishesFailedWhenDeferredStreamErrors() {
        RecordingTraceRepository traceRepository = new RecordingTraceRepository();
        KernelRagTraceRecorder traceRecorder = new KernelRagTraceRecorder(traceRepository);
        DeferredStreamingModel model = new DeferredStreamingModel();
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline(model, traceRecorder),
                StreamTaskPort.noop(),
                Optional.empty(),
                traceRecorder,
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop());
        RecordingCallback callback = new RecordingCallback();

        service.streamChat(new StreamChatCommand(
                "hello", "conversation-1", "task-1", "user-1", false, ChatMode.RAG), callback);

        assertEquals(KernelRagTraceRecorder.STATUS_RUNNING, traceRepository.startedRun.getStatus());
        assertNull(traceRepository.finishedRun);

        RuntimeException error = new RuntimeException("stream broke");
        model.fail(error);

        assertTrue(callback.awaitTerminal());
        assertSame(error, callback.error);
        assertEquals(KernelRagTraceRecorder.STATUS_FAILED, traceRepository.finishedRun.status());
    }

    private static KernelChatPipeline pipeline(StreamingChatModelPort model, KernelRagTraceRecorder traceRecorder) {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build());
        ChatResponsePorts responsePorts = new ChatResponsePorts(
                RagPromptPort.simple(),
                PromptTemplatePort.empty(),
                model,
                StreamTaskPort.noop());
        return new KernelChatPipeline(preparationPorts, responsePorts, traceRecorder);
    }

    private static final class DeferredStreamingModel implements StreamingChatModelPort {

        private StreamCallback callback;

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            this.callback = callback;
            return () -> {
            };
        }

        private void fail(Throwable error) {
            callback.onError(error);
        }
    }

    private static final class RecordingCallback implements StreamCallback {

        private final CountDownLatch terminal = new CountDownLatch(1);
        private Throwable error;

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            terminal.countDown();
        }

        private boolean awaitTerminal() {
            try {
                return terminal.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {

        private RagTraceRun startedRun;
        private RagTraceRunFinish finishedRun;

        @Override
        public RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request) {
            return new RagTracePage<>(1, 10, 0, List.of());
        }

        @Override
        public Optional<RagTraceRun> findRun(String traceId) {
            return Optional.ofNullable(startedRun);
        }

        @Override
        public List<RagTraceNode> listNodes(String traceId) {
            return List.of();
        }

        @Override
        public void startRun(RagTraceRun run) {
            startedRun = run;
        }

        @Override
        public void finishRun(RagTraceRunFinish finish) {
            finishedRun = finish;
        }

        @Override
        public void startNode(RagTraceNode node) {
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
        }
    }
}
