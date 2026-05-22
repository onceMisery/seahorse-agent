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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.tool;

/**
 * 工具调用审计状态。
 */
public enum ToolInvocationStatus {

    /**
     * Gateway 已接收调用请求。
     */
    REQUESTED,

    /**
     * 策略允许执行真实工具。
     */
    ALLOWED,

    /**
     * 策略拒绝执行真实工具。
     */
    DENIED,

    /**
     * 策略要求人工审批，真实工具暂不执行。
     */
    APPROVAL_REQUIRED,

    /**
     * 真实工具执行成功。
     */
    SUCCEEDED,

    /**
     * 真实工具执行失败或 Gateway 捕获异常。
     */
    FAILED
}
