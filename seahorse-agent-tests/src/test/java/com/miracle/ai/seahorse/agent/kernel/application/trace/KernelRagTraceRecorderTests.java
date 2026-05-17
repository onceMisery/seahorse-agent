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

package com.miracle.ai.seahorse.agent.kernel.application.trace;

import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
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

class KernelRagTraceRecorderTests {

    @Test
    void shouldRecordRunAndNodeLifecycleInOrder() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        KernelRagTraceRecorder recorder = new KernelRagTraceRecorder(repository);

        TraceRunScope runScope = recorder.startRun(new TraceRunStartCommand(
                "stream-chat", "KernelChatInboundService#streamChat", "conv-1", "task-1", "user-1"));
        recorder.recordNode(runScope, new TraceNodeStartCommand(
                "load-memory", "CHAT_STAGE", "KernelChatPipeline", "loadMemory", null, 0), () -> {
                });
        recorder.finishRun(runScope);

        Assertions.assertEquals(List.of("startRun", "startNode", "finishNode", "finishRun"), repository.events);
        Assertions.assertEquals("stream-chat", repository.startedRuns.get(0).getTraceName());
        Assertions.assertEquals("load-memory", repository.startedNodes.get(0).getNodeName());
        Assertions.assertEquals(KernelRagTraceRecorder.STATUS_SUCCESS, repository.finishedNodes.get(0).status());
        Assertions.assertEquals(KernelRagTraceRecorder.STATUS_SUCCESS, repository.finishedRuns.get(0).status());
    }

    @Test
    void shouldFinishNodeAsFailedWhenRecordedActionThrows() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        KernelRagTraceRecorder recorder = new KernelRagTraceRecorder(repository);
        TraceRunScope runScope = recorder.startRun(new TraceRunStartCommand(
                "stream-chat", "KernelChatInboundService#streamChat", "conv-1", "task-1", "user-1"));

        IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
                () -> recorder.recordNode(runScope, new TraceNodeStartCommand(
                        "retrieval", "CHAT_STAGE", "KernelChatPipeline", "retrieve", null, 0), () -> {
                            throw new IllegalStateException("bad\nstate");
                        }));

        Assertions.assertEquals("bad\nstate", error.getMessage());
        Assertions.assertEquals(KernelRagTraceRecorder.STATUS_FAILED, repository.finishedNodes.get(0).status());
        Assertions.assertFalse(repository.finishedNodes.get(0).errorMessage().contains("\n"));
    }

    @Test
    void shouldSkipRunAndNodesWhenSampleRateIsZero() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        KernelRagTraceRecorder recorder = new KernelRagTraceRecorder(repository, new RagTraceRecorderOptions(0D));

        TraceRunScope runScope = recorder.startRun(new TraceRunStartCommand(
                "stream-chat", "KernelChatInboundService#streamChat", "conv-1", "task-1", "user-1"));
        recorder.recordNode(runScope, new TraceNodeStartCommand(
                "load-memory", "CHAT_STAGE", "KernelChatPipeline", "loadMemory", null, 0), () -> {
                });
        recorder.finishRun(runScope);

        Assertions.assertFalse(runScope.active());
        Assertions.assertTrue(repository.events.isEmpty());
    }

    @Test
    void shouldRecordWhenSampleRateIsOne() {
        RecordingTraceRepository repository = new RecordingTraceRepository();
        KernelRagTraceRecorder recorder = new KernelRagTraceRecorder(repository, new RagTraceRecorderOptions(1D));

        TraceRunScope runScope = recorder.startRun(new TraceRunStartCommand(
                "stream-chat", "KernelChatInboundService#streamChat", "conv-1", "task-1", "user-1"));
        recorder.finishRun(runScope);

        Assertions.assertTrue(runScope.active());
        Assertions.assertEquals(List.of("startRun", "finishRun"), repository.events);
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {

        private final List<String> events = new ArrayList<>();
        private final List<RagTraceRun> startedRuns = new ArrayList<>();
        private final List<RagTraceRunFinish> finishedRuns = new ArrayList<>();
        private final List<RagTraceNode> startedNodes = new ArrayList<>();
        private final List<RagTraceNodeFinish> finishedNodes = new ArrayList<>();

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
            events.add("startRun");
            startedRuns.add(run);
        }

        @Override
        public void finishRun(RagTraceRunFinish finish) {
            events.add("finishRun");
            finishedRuns.add(finish);
        }

        @Override
        public void startNode(RagTraceNode node) {
            events.add("startNode");
            startedNodes.add(node);
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
            events.add("finishNode");
            finishedNodes.add(finish);
        }
    }
}
