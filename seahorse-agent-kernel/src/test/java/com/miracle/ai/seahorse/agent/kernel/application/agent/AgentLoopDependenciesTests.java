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

import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentLoopDependenciesTests {

    @Test
    void fillsOptionalCollaboratorsWithKernelDefaults() {
        AgentLoopDependencies dependencies = new AgentLoopDependencies(
                StreamingChatModelPort.noop(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertNotNull(dependencies.toolRegistry());
        assertNotNull(dependencies.toolGateway());
        assertNotNull(dependencies.options());
        assertNotNull(dependencies.traceRecorder());
        assertInstanceOf(DefaultContextWeaver.class, dependencies.contextWeaver());
        assertNotNull(dependencies.runStepRecorder());
        assertNotNull(dependencies.approvalWaitHandler());
        assertNotNull(dependencies.markdownNormalizer());
        assertNotNull(dependencies.streamEmitter());
        assertNotNull(dependencies.toolCallParser());
    }

    @Test
    void keepsExplicitCollaborators() {
        ToolRegistryPort registry = ToolRegistryPort.empty();
        ToolGatewayPort gateway = request -> null;
        ContextWeaverPort contextWeaver = new DefaultContextWeaver();
        AgentRunStepRecorder recorder = AgentRunStepRecorder.noop();
        AgentApprovalWaitHandler approval = AgentApprovalWaitHandler.noop();
        MarkdownNormalizer markdownNormalizer = new MarkdownNormalizer();
        AgentStreamEmitter streamEmitter = new AgentStreamEmitter();
        ToolCallParser toolCallParser = new ToolCallParser();

        AgentLoopDependencies dependencies = new AgentLoopDependencies(
                StreamingChatModelPort.noop(),
                registry,
                gateway,
                KernelAgentLoopOptions.defaults(),
                KernelRagTraceRecorder.noop(),
                contextWeaver,
                recorder,
                approval,
                null,
                markdownNormalizer,
                streamEmitter,
                toolCallParser);

        assertSame(registry, dependencies.toolRegistry());
        assertSame(gateway, dependencies.toolGateway());
        assertSame(contextWeaver, dependencies.contextWeaver());
        assertSame(recorder, dependencies.runStepRecorder());
        assertSame(approval, dependencies.approvalWaitHandler());
        assertSame(markdownNormalizer, dependencies.markdownNormalizer());
        assertSame(streamEmitter, dependencies.streamEmitter());
        assertSame(toolCallParser, dependencies.toolCallParser());
    }
}
