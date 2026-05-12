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

package com.miracle.ai.seahorse.agent.kernel.feature.mcp;

/**
 * MCP 工具执行状态。
 * <p>
 * 状态值用于让内核区分成功、工具缺失和执行失败，避免把所有异常都退化为空字符串。
 */
public enum McpToolExecutionStatus {

    /**
     * 工具执行成功。
     */
    SUCCESS,

    /**
     * 工具未注册或未被当前配置启用。
     */
    TOOL_NOT_FOUND,

    /**
     * 工具执行过程中出现异常。
     */
    EXECUTION_FAILED
}
