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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;

@FunctionalInterface
public interface ToolPolicyPort {

    /**
     * 对单次工具调用做策略裁决。
     *
     * <p>调用方必须在真实工具执行前调用该方法；返回值是后续审计、审批、脱敏和限流的统一依据。
     *
     * @param request 工具策略裁决请求，包含 run、agent、tenant、user、tool、参数和工具绑定上下文
     * @return 策略裁决结果；只有 {@link PolicyDecision.Effect#ALLOW} 可以继续执行真实工具
     */
    PolicyDecision decide(ToolPolicyRequest request);

    /**
     * Phase 2 最小内置策略：先覆盖工具存在性和当前 allowlist 绑定，后续再接 catalog、risk/action 与资源 ACL。
     */
    static ToolPolicyPort defaults() {
        return request -> {
            if (request == null || !request.toolRegistered()) {
                return PolicyDecision.deny("builtin-tool-not-found",
                        "TOOL_NOT_FOUND",
                        "Tool is not registered");
            }
            if (!request.allowedToolIds().isEmpty() && !request.allowedToolIds().contains(request.toolId())) {
                return PolicyDecision.deny("builtin-tool-not-bound",
                        "TOOL_NOT_BOUND",
                        "Tool is not bound to the current agent version");
            }
            return PolicyDecision.allow("builtin-tool-allow");
        };
    }
}
