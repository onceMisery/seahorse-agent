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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentLoopMarkdownOutputTests {

    @Test
    void finalMarkdownNormalizesCompressedMermaidFences() {
        String compressedMarkdown = """
                # Redis intro---## Project overviewIntro text### Evidence|Path |Purpose ||------|------|
                - **Client layer**: Redis clients- **Network layer**: RESP protocol
                ## Architecture
                ```mermaidflowchart TD
                A[Client] --> B[Redis]
                ```---## Flow
                ```mermaidgraph LR
                X[Read] --> Y[Write]
                ```---## Summary
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("```mermaid\nflowchart TD"));
        assertTrue(answer.contains("```mermaid\ngraph LR"));
        assertTrue(answer.contains("```mermaid\nflowchart TD\n"));
        assertTrue(answer.contains("# Redis intro"));
        assertTrue(answer.contains("\n- **"));
        assertFalse(answer.contains("```mermaidflowchart"));
        assertFalse(answer.contains("```mermaidgraph"));
        assertFalse(answer.contains("---##"));
        assertFalse(answer.contains("```---"));
    }

    private static final class FinalAnswerModel implements StreamingChatModelPort {
        private final String answer;

        private FinalAnswerModel(String answer) {
            this.answer = answer;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            callback.onContent(answer);
            toolCallCollector.onToolCalls(List.of());
            callback.onComplete();
            return () -> {
            };
        }
    }
}
