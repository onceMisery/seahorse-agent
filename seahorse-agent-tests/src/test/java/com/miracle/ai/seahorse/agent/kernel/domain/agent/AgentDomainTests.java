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

package com.miracle.ai.seahorse.agent.kernel.domain.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A2 契约测试：Agent 领域模型（Step/ToolCall/Observation/LoopRequest/LoopResult）。
 */
class AgentDomainTests {

    @Test
    void thoughtStepCarriesToolCallsAndIsNotFinal() {
        AgentToolCall call = new AgentToolCall("call-1", "weather", Map.of("city", "Shanghai"));
        AgentStep step = AgentStep.thought("查询天气", List.of(call), List.of());
        assertFalse(step.isFinal());
        assertEquals(1, step.toolCalls().size());
        assertNull(step.finalAnswer());
    }

    @Test
    void finalAnswerStepIsFinalAndHasNoToolCalls() {
        AgentStep step = AgentStep.finalAnswer("天气是 21℃");
        assertTrue(step.isFinal());
        assertEquals("天气是 21℃", step.finalAnswer());
        assertTrue(step.toolCalls().isEmpty());
        assertTrue(step.observations().isEmpty());
    }

    @Test
    void toolCallRejectsBlankIdOrToolId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentToolCall("", "weather", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentToolCall("c-1", " ", Map.of()));
    }

    @Test
    void toolCallArgumentsAreDefensivelyCopied() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("k", "v");
        AgentToolCall call = new AgentToolCall("c-1", "weather", mutable);
        mutable.put("k", "tampered");
        assertEquals("v", call.arguments().get("k"), "构造时必须做防御性拷贝");
        assertThrows(UnsupportedOperationException.class,
                () -> call.arguments().put("foo", "bar"));
    }

    @Test
    void observationFactoriesProduceConsistentPayload() {
        AgentObservation ok = AgentObservation.ok("c-1", "{\"temp\":21}");
        assertTrue(ok.success());
        assertEquals("c-1", ok.toolCallId());
        assertEquals("{\"temp\":21}", ok.content());
        assertNull(ok.error());

        AgentObservation failed = AgentObservation.failed("c-2", "timeout");
        assertFalse(failed.success());
        assertEquals("timeout", failed.error());
        assertNull(failed.content());
    }

    @Test
    void loopRequestBuilderRequiresQuestionAndAppliesDefaults() {
        AgentLoopRequest req = AgentLoopRequest.builder()
                .question("天气如何")
                .history(List.of(ChatMessage.user("hi")))
                .tools(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .build();
        assertEquals(6, req.maxSteps(), "maxSteps 缺省 6");
        assertNotNull(req.history());
        assertNull(req.memoryContext());
    }

    @Test
    void loopRequestBlankQuestionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AgentLoopRequest.builder()
                .question(" ")
                .history(List.of())
                .tools(List.of())
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build());
    }

    @Test
    void loopResultExposesStepsAndTruncatedFlag() {
        AgentStep s1 = AgentStep.thought("t",
                List.of(new AgentToolCall("c1", "weather", Map.of())),
                List.of());
        AgentStep s2 = AgentStep.finalAnswer("done");
        AgentLoopResult result = new AgentLoopResult("done", List.of(s1, s2), false);
        assertEquals(2, result.steps().size());
        assertFalse(result.truncated());
        assertEquals("done", result.finalAnswer());
        assertThrows(UnsupportedOperationException.class,
                () -> result.steps().add(s2));
    }
}
