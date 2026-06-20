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

import com.miracle.ai.seahorse.agent.kernel.application.agent.output.OutputGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;

import java.util.Objects;

/**
 * Immutable dependency bundle for the kernel ReAct loop.
 */
public record AgentLoopDependencies(
        StreamingChatModelPort modelPort,
        ToolRegistryPort toolRegistry,
        ToolGatewayPort toolGateway,
        KernelAgentLoopOptions options,
        KernelRagTraceRecorder traceRecorder,
        ContextWeaverPort contextWeaver,
        AgentRunStepRecorder runStepRecorder,
        AgentApprovalWaitHandler approvalWaitHandler,
        OutputGovernanceService outputGovernance,
        MarkdownNormalizer markdownNormalizer,
        AgentStreamEmitter streamEmitter,
        ToolCallParser toolCallParser) {

    public AgentLoopDependencies {
        modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        ToolRegistryPort effectiveToolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        toolRegistry = effectiveToolRegistry;
        toolGateway = Objects.requireNonNullElseGet(toolGateway, () -> new LocalToolGatewayPort(effectiveToolRegistry));
        options = Objects.requireNonNullElseGet(options, KernelAgentLoopOptions::defaults);
        traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        contextWeaver = Objects.requireNonNullElseGet(contextWeaver, DefaultContextWeaver::new);
        runStepRecorder = Objects.requireNonNullElseGet(runStepRecorder, AgentRunStepRecorder::noop);
        approvalWaitHandler = Objects.requireNonNullElseGet(approvalWaitHandler, AgentApprovalWaitHandler::noop);
        markdownNormalizer = Objects.requireNonNullElseGet(markdownNormalizer, MarkdownNormalizer::new);
        streamEmitter = Objects.requireNonNullElseGet(streamEmitter, AgentStreamEmitter::new);
        toolCallParser = Objects.requireNonNullElseGet(toolCallParser, ToolCallParser::new);
    }
}
