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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KernelChatInboundServiceTraceTests {

    @Test
    void shouldCloseTraceRunWhenPipelineSucceeds() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        KernelChatPipeline pipeline = mock(KernelChatPipeline.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        doAnswer(invocation -> {
            StreamChatContext context = invocation.getArgument(0);
            context.getCallback().onComplete();
            return null;
        }).when(pipeline).execute(any());
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline, taskPort, new KernelRagTraceRecorder(repository));

        StreamCallback callback = mock(StreamCallback.class);
        service.streamChat(command(), callback);
        org.mockito.Mockito.verify(callback).onComplete();
        verify(pipeline).execute(argThat(context -> context.getTraceRunScope() != null
                && context.getTraceRunScope().active()));
        Assertions.assertEquals("stream-chat", repository.startedRuns.get(0).getTraceName());
        Assertions.assertEquals("task-1", repository.startedRuns.get(0).getTaskId());
        Assertions.assertEquals(KernelRagTraceRecorder.STATUS_SUCCESS, repository.finishedRuns.get(0).status());
    }

    @Test
    void shouldCloseTraceRunAndNotifyCallbackWhenPipelineFails() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        KernelChatPipeline pipeline = mock(KernelChatPipeline.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        StreamCallback callback = mock(StreamCallback.class);
        IllegalStateException error = new IllegalStateException("model\ndown");
        doThrow(error).when(pipeline).execute(any());
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline, taskPort, new KernelRagTraceRecorder(repository));

        service.streamChat(command(), callback);

        verify(callback).onError(error);
        Assertions.assertEquals(KernelRagTraceRecorder.STATUS_FAILED, repository.finishedRuns.get(0).status());
        Assertions.assertFalse(repository.finishedRuns.get(0).errorMessage().contains("\n"));
    }

    private StreamChatCommand command() {
        return new StreamChatCommand("hello", "conv-1", "task-1", "user-1", false);
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {

        private final List<RagTraceRun> startedRuns = new ArrayList<>();
        private final List<RagTraceRunFinish> finishedRuns = new ArrayList<>();

        @Override
        public RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request) {
            return new RagTracePage<>(1, 10, 0, List.of());
        }

        @Override
        public Optional<RagTraceRun> findRun(String traceId) {
            return Optional.empty();
        }

        @Override
        public List<RagTraceNode> listNodes(String traceId) {
            return List.of();
        }

        @Override
        public void startRun(RagTraceRun run) {
            startedRuns.add(run);
        }

        @Override
        public void finishRun(RagTraceRunFinish finish) {
            finishedRuns.add(finish);
        }

        @Override
        public void startNode(RagTraceNode node) {
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
        }
    }
}
