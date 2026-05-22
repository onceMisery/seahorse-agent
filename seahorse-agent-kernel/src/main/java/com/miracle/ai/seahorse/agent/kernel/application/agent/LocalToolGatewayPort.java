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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;

import java.util.Objects;
import java.util.Optional;

public class LocalToolGatewayPort implements ToolGatewayPort {

    private final ToolRegistryPort toolRegistry;
    private final ToolPolicyPort toolPolicy;

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry) {
        this(toolRegistry, ToolPolicyPort.defaults());
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry, ToolPolicyPort toolPolicy) {
        this.toolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolPolicy = Objects.requireNonNullElseGet(toolPolicy, ToolPolicyPort::defaults);
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Optional<ToolPort> toolPort = toolRegistry.find(safeRequest.toolId());

        // 策略裁决必须发生在真实工具执行之前；非 ALLOW 结果不得触达 ToolPort。
        PolicyDecision decision = Objects.requireNonNullElseGet(
                toolPolicy.decide(ToolPolicyRequest.from(safeRequest, toolPort.isPresent())),
                () -> PolicyDecision.deny("builtin-policy-null", "POLICY_DECISION_MISSING",
                        "Tool policy did not return a decision"));
        if (!decision.allowsExecution()) {
            return ToolInvocationResult.failed(decision.reasonCode());
        }

        try {
            ToolPort executableTool = toolPort
                    .orElseGet(() -> ToolPort.notFound(safeRequest.toolId()));
            return executableTool.invoke(safeRequest.toolCallId(), safeRequest.toolId(), safeRequest.arguments());
        } catch (Exception ex) {
            return ToolInvocationResult.failed(
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }
}
