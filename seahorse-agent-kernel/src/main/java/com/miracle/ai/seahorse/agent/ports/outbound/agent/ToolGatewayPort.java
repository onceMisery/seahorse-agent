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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;

/**
 * 统一工具网关端口。
 *
 * <p>Agent Runtime 只能通过该端口触发工具调用，不能直接绕过 Gateway 调用 {@link ToolPort}。
 */
public interface ToolGatewayPort {

    /**
     * 执行一次受管控的工具调用。
     *
     * @param request 工具调用请求，包含 run、step、agent、tenant、user、tool 和入参上下文
     * @return 工具执行结果；策略拒绝、审批中断和真实工具异常都以失败结果返回
     */
    ToolInvocationResult invoke(ToolInvocationRequest request);
}
